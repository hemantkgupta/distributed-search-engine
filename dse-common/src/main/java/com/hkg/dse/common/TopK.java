package com.hkg.dse.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A bounded top-K min-heap of {@link Score}s.
 *
 * <p>Used uniformly by the lexical loop (BMW heap), the vector loop
 * (HNSW candidate heap), and the broker (cross-shard merge). Maintains
 * the current K best results by score and exposes the current
 * "heap floor" — the K-th best score so far — which Block-Max WAND
 * uses as the dynamic pruning threshold.</p>
 */
public final class TopK {

    private final int k;
    /** Min-heap on score (lowest at top) — pop replaces the worst. */
    private final PriorityQueue<Score> heap;

    public TopK(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1, got " + k);
        }
        this.k = k;
        // Min-heap on score ascending — Score's natural order is desc,
        // so we reverse for min-on-score.
        this.heap = new PriorityQueue<>(Collections.reverseOrder());
    }

    public void offer(Score score) {
        if (heap.size() < k) {
            heap.offer(score);
        } else if (score.value() > heap.peek().value()) {
            heap.poll();
            heap.offer(score);
        }
    }

    /** Current K-th best score, or Double.NEGATIVE_INFINITY if heap not yet full. */
    public double floor() {
        if (heap.size() < k) {
            return Double.NEGATIVE_INFINITY;
        }
        return heap.peek().value();
    }

    public int size() {
        return heap.size();
    }

    /** Sorted result list (best first). Does not consume the heap. */
    public List<Score> sortedResults() {
        List<Score> sorted = new ArrayList<>(heap);
        Collections.sort(sorted);
        return sorted;
    }
}
