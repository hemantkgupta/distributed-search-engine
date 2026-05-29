package com.hkg.dse.hnsw;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Score;
import com.hkg.dse.roaring.RoaringBitset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Simplified single-layer HNSW-style proximity graph.
 *
 * <p>The production HNSW has multiple layers with exponentially-decaying
 * node density and a top-down greedy walk. For pedagogical clarity this
 * implementation flattens the layers — the query is a beam search at
 * layer 0 with width {@code ef}, starting at a fixed entry point.</p>
 *
 * <p>The structural commitment: vectors are stored as PQ-compressed
 * codes, distance is approximated via ADC, and ACL pre-filter is a
 * Roaring bitset passed into the beam search. Unauthorised nodes are
 * EXPLORED for navigation but NOT added to the result heap — the
 * load-bearing recall-preserving detail.</p>
 */
public final class HnswGraph {

    /** Per-node neighbour list (other doc-IDs). */
    private final Map<Integer, int[]> neighbours = new HashMap<>();
    /** Per-node PQ-compressed vector code. */
    private final Map<Integer, byte[]> codes = new HashMap<>();
    private final ProductQuantizer pq;
    private int entryPoint = -1;
    private final int maxNeighbours;

    public HnswGraph(ProductQuantizer pq, int maxNeighbours) {
        if (maxNeighbours < 2) {
            throw new IllegalArgumentException("maxNeighbours must be >= 2");
        }
        this.pq = pq;
        this.maxNeighbours = maxNeighbours;
    }

    public ProductQuantizer pq() { return pq; }

    public int entryPoint() { return entryPoint; }

    /**
     * Insert a new node into the graph.
     *
     * <p>This is a simplified version of the HNSW insertion: link the new
     * node to up to {@code maxNeighbours} closest existing nodes (found by
     * beam search), and add the new node to each of their neighbour lists
     * (pruning if needed).</p>
     */
    public void insert(DocId docId, float[] vector) {
        byte[] code = pq.encode(vector);
        codes.put(docId.value(), code);
        if (entryPoint == -1) {
            neighbours.put(docId.value(), new int[0]);
            entryPoint = docId.value();
            return;
        }
        // Search for closest existing nodes
        List<Score> nearest = searchInternal(vector, maxNeighbours, null);
        int[] newNeighbours = new int[nearest.size()];
        for (int i = 0; i < nearest.size(); i++) {
            newNeighbours[i] = nearest.get(i).docId().value();
        }
        neighbours.put(docId.value(), newNeighbours);
        // Add bidirectional links
        for (int neighbour : newNeighbours) {
            int[] existing = neighbours.get(neighbour);
            int[] updated = appendBounded(existing, docId.value(), maxNeighbours, vector, pq, codes);
            neighbours.put(neighbour, updated);
        }
    }

    private static int[] appendBounded(int[] existing, int newId, int cap,
                                       float[] newVec, ProductQuantizer pq,
                                       Map<Integer, byte[]> codes) {
        if (existing.length < cap) {
            int[] result = new int[existing.length + 1];
            System.arraycopy(existing, 0, result, 0, existing.length);
            result[existing.length] = newId;
            return result;
        }
        // Drop the furthest neighbour to make room
        float[][] lut = pq.precomputeLut(newVec);
        float worstDist = pq.approxSquaredDistance(lut, codes.get(existing[0]));
        int worstIdx = 0;
        for (int i = 1; i < existing.length; i++) {
            float d = pq.approxSquaredDistance(lut, codes.get(existing[i]));
            if (d > worstDist) {
                worstDist = d;
                worstIdx = i;
            }
        }
        int[] result = existing.clone();
        result[worstIdx] = newId;
        return result;
    }

    /** Beam-search top-K. {@code authorisedDocs} may be null for un-filtered search. */
    public List<Score> search(float[] queryVector, int k, RoaringBitset authorisedDocs) {
        return searchInternal(queryVector, k, authorisedDocs);
    }

    private List<Score> searchInternal(float[] queryVector, int k, RoaringBitset authorisedDocs) {
        if (entryPoint == -1) return List.of();
        float[][] lut = pq.precomputeLut(queryVector);
        // candidates: min-heap of distance (lowest first) — frontier to expand
        PriorityQueue<int[]> candidates = new PriorityQueue<>(
            (a, b) -> Float.compare(Float.intBitsToFloat(a[1]), Float.intBitsToFloat(b[1]))
        );
        // results: max-heap of distance (highest first) — bounded top-K
        PriorityQueue<int[]> results = new PriorityQueue<>(
            (a, b) -> Float.compare(Float.intBitsToFloat(b[1]), Float.intBitsToFloat(a[1]))
        );
        Set<Integer> visited = new HashSet<>();

        float entryDist = pq.approxSquaredDistance(lut, codes.get(entryPoint));
        candidates.add(new int[]{entryPoint, Float.floatToRawIntBits(entryDist)});
        visited.add(entryPoint);
        if (authorisedDocs == null || authorisedDocs.contains(entryPoint)) {
            results.add(new int[]{entryPoint, Float.floatToRawIntBits(entryDist)});
        }

        while (!candidates.isEmpty()) {
            int[] cur = candidates.poll();
            int curId = cur[0];
            float curDist = Float.intBitsToFloat(cur[1]);
            // Stop if the closest unvisited candidate is worse than our worst result
            if (!results.isEmpty() && results.size() >= k) {
                float worstResult = Float.intBitsToFloat(results.peek()[1]);
                if (curDist > worstResult) break;
            }
            int[] nbrs = neighbours.get(curId);
            if (nbrs == null) continue;
            for (int n : nbrs) {
                if (!visited.add(n)) continue;
                float d = pq.approxSquaredDistance(lut, codes.get(n));
                int[] entry = new int[]{n, Float.floatToRawIntBits(d)};
                candidates.add(entry);
                // ACL pre-filter: do NOT add to result heap; DO add to candidates above
                if (authorisedDocs != null && !authorisedDocs.contains(n)) continue;
                if (results.size() < k) {
                    results.add(entry);
                } else {
                    float worst = Float.intBitsToFloat(results.peek()[1]);
                    if (d < worst) {
                        results.poll();
                        results.add(entry);
                    }
                }
            }
        }

        List<Score> out = new ArrayList<>();
        for (int[] r : results) {
            float dist = Float.intBitsToFloat(r[1]);
            // Convert distance to score (higher = better)
            out.add(new Score(DocId.of(r[0]), -dist));
        }
        Collections.sort(out);
        return out;
    }

    public int size() { return codes.size(); }
}
