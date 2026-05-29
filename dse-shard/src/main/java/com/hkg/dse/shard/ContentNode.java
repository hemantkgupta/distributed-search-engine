package com.hkg.dse.shard;

import com.hkg.dse.acl.AclBitsetBuilder;
import com.hkg.dse.colbert.ColbertReranker;
import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Score;
import com.hkg.dse.inverted.BlockMaxWandScorer;
import com.hkg.dse.rrf.ReciprocalRankFusion;
import com.hkg.dse.roaring.RoaringBitset;
import com.hkg.dse.segment.UnifiedSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * A content node — owns one shard's {@link UnifiedSegment} and runs the
 * full per-shard retrieval pipeline.
 *
 * <p>The pipeline:
 * <ol>
 *   <li>Build the ACL Roaring bitmap from the principal's tokens.</li>
 *   <li>Lexical first stage: BMW over the inverted index with ACL pre-filter.</li>
 *   <li>Vector first stage: HNSW with ACL pre-filter inside the walk.</li>
 *   <li>RRF fusion of the two candidate lists.</li>
 *   <li>ColBERT MaxSim rerank.</li>
 *   <li>Apply tombstones to filter out any deleted docs that survived.</li>
 * </ol>
 *
 * <p>All steps share one memory space — see
 * {@code patterns/unified-lexical-vector-segment}.</p>
 */
public final class ContentNode {

    private final UnifiedSegment segment;
    private final ReciprocalRankFusion rrf;
    /** Default first-stage candidate budget per channel. */
    public static final int FIRST_STAGE_K = 1_000;

    public ContentNode(UnifiedSegment segment) {
        this(segment, new ReciprocalRankFusion());
    }

    public ContentNode(UnifiedSegment segment, ReciprocalRankFusion rrf) {
        this.segment = segment;
        this.rrf = rrf;
    }

    /** Expose the segment for out-of-band tombstone application. */
    public UnifiedSegment segment() {
        return segment;
    }

    /** Alias for {@link #segment()} used by the cluster wrapper. */
    public UnifiedSegment segmentForTombstone() {
        return segment;
    }

    public ShardResult execute(Query query) {
        // 1. ACL bitset
        AclBitsetBuilder aclBuilder = new AclBitsetBuilder(segment.invertedIndex());
        RoaringBitset acl = aclBuilder.buildFor(query.principal());

        // 2. Lexical first stage
        BlockMaxWandScorer lexical = new BlockMaxWandScorer(segment.invertedIndex());
        List<Score> lexicalHits = lexical.scoreAnd(query.lexicalTerms(), FIRST_STAGE_K, acl);

        // 3. Vector first stage
        List<Score> vectorHits = segment.hnswGraph()
            .search(query.denseVector(), FIRST_STAGE_K, acl);

        // 4. RRF fusion
        List<Score> fused = rrf.fuse(FIRST_STAGE_K, lexicalHits, vectorHits);

        // 5. ColBERT rerank
        List<DocId> candidateIds = new ArrayList<>();
        for (Score s : fused) candidateIds.add(s.docId());
        ColbertReranker colbert = segment.colbert();
        List<Score> reranked = colbert.rerank(candidateIds, query.colbertTokens(), query.topK());

        // 6. Apply tombstones (anything tombstoned mid-query should not leak)
        List<Score> finalResults = new ArrayList<>();
        for (Score s : reranked) {
            if (!segment.tombstones().contains(s.docId().value())) {
                finalResults.add(s);
            }
        }
        return ShardResult.complete(finalResults);
    }
}
