package com.hkg.dse.merge;

import com.hkg.dse.segment.UnifiedSegment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tiered merge selection policy.
 *
 * <p>Selects {@code mergeFactor} similarly-sized segments at the smallest
 * tier and proposes them as a merge candidate set. Also force-merges any
 * segment whose tombstone ratio exceeds a configurable threshold, since
 * the tombstones won't physically free space until merge consolidates
 * the segment.</p>
 *
 * <p>The threshold-based force-merge is the load-bearing operational
 * lever for HNSW recall — without it, unreachable points accumulate
 * over weeks. See {@code concepts/hnsw-graph.md}.</p>
 */
public final class TieredMergePolicy {

    public static final double DEFAULT_FORCE_MERGE_TOMBSTONE_RATIO = 0.20;
    public static final int DEFAULT_MERGE_FACTOR = 3;

    private final double forceMergeTombstoneRatio;
    private final int mergeFactor;

    public TieredMergePolicy() {
        this(DEFAULT_FORCE_MERGE_TOMBSTONE_RATIO, DEFAULT_MERGE_FACTOR);
    }

    public TieredMergePolicy(double forceMergeTombstoneRatio, int mergeFactor) {
        if (forceMergeTombstoneRatio < 0 || forceMergeTombstoneRatio > 1) {
            throw new IllegalArgumentException("ratio in [0,1]");
        }
        if (mergeFactor < 2) {
            throw new IllegalArgumentException("mergeFactor must be >= 2");
        }
        this.forceMergeTombstoneRatio = forceMergeTombstoneRatio;
        this.mergeFactor = mergeFactor;
    }

    public double forceMergeTombstoneRatio() { return forceMergeTombstoneRatio; }
    public int mergeFactor() { return mergeFactor; }

    /**
     * Pick segments to merge. Returns the input list if no candidate is
     * found (caller checks size and decides whether to schedule).
     */
    public List<UnifiedSegment> pickMergeCandidates(List<UnifiedSegment> segments) {
        // Force-merge any segment exceeding tombstone-ratio threshold.
        for (UnifiedSegment s : segments) {
            if (s.tombstoneRatio() >= forceMergeTombstoneRatio) {
                // Include this segment + up to mergeFactor-1 of its
                // smallest-live-doc-count peers.
                List<UnifiedSegment> peers = new ArrayList<>(segments);
                peers.remove(s);
                peers.sort(Comparator.comparingInt(UnifiedSegment::liveDocCount));
                List<UnifiedSegment> pick = new ArrayList<>();
                pick.add(s);
                for (int i = 0; i < Math.min(mergeFactor - 1, peers.size()); i++) {
                    pick.add(peers.get(i));
                }
                return pick;
            }
        }
        // Otherwise, tier-based pick: take mergeFactor smallest live-doc-count.
        if (segments.size() < mergeFactor) return List.of();
        List<UnifiedSegment> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparingInt(UnifiedSegment::liveDocCount));
        return sorted.subList(0, mergeFactor);
    }
}
