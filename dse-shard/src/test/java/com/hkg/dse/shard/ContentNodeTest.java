package com.hkg.dse.shard;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Principal;
import com.hkg.dse.common.Term;
import com.hkg.dse.hnsw.ProductQuantizer;
import com.hkg.dse.segment.SegmentBuilder;
import com.hkg.dse.segment.UnifiedSegment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContentNodeTest {

    private static final int DIM = 16;

    private float[] vec(long seed) {
        Random r = new Random(seed);
        float[] v = new float[DIM];
        for (int i = 0; i < DIM; i++) v[i] = (float) r.nextGaussian();
        return v;
    }

    private float[][] tokens(long seed) {
        Random r = new Random(seed);
        float[][] t = new float[2][DIM];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < DIM; j++) t[i][j] = (float) r.nextGaussian();
        }
        return t;
    }

    private UnifiedSegment buildSegment() {
        int n = 30;
        float[][] training = new float[n][DIM];
        for (int i = 0; i < n; i++) training[i] = vec(i);
        ProductQuantizer pq = ProductQuantizer.trainSampled(training, DIM, 4, 8, 1L);
        SegmentBuilder b = new SegmentBuilder(pq, 8);
        for (int i = 0; i < n; i++) {
            // Doc i has terms "cisco", "doc<i>", and ACL tokens depending on i.
            // First 10 docs accessible to user_42; next 10 to group_admins; last 10 to nobody we'll add.
            List<Term> terms;
            if (i < 10) {
                terms = List.of(Term.of("cisco"), Term.aclRead("user_42"));
            } else if (i < 20) {
                terms = List.of(Term.of("cisco"), Term.aclRead("group_admins"));
            } else {
                terms = List.of(Term.of("cisco"), Term.aclRead("group_others"));
            }
            b.addDocument(DocId.of(i), terms, training[i], "payload" + i, tokens(i + 100));
        }
        return b.build();
    }

    @Test
    void endToEndPipelineReturnsResults() {
        UnifiedSegment seg = buildSegment();
        ContentNode node = new ContentNode(seg);
        Principal principal = new Principal("42", "t", Set.of("group_admins"));
        Query query = new Query(
            List.of(Term.of("cisco")),
            vec(5),
            tokens(7),
            principal,
            10
        );
        ShardResult result = node.execute(query);
        assertThat(result.partial()).isFalse();
        assertThat(result.topK()).isNotEmpty();
        // Every returned doc must be in the authorised set (docs 0-19)
        for (var score : result.topK()) {
            assertThat(score.docId().value()).isLessThan(20);
        }
    }

    @Test
    void aclScopesResultsToPrincipalsAccess() {
        UnifiedSegment seg = buildSegment();
        ContentNode node = new ContentNode(seg);
        Principal limited = new Principal("42", "t", Set.of()); // user_42 only
        Query query = new Query(
            List.of(Term.of("cisco")),
            vec(5),
            tokens(7),
            limited,
            10
        );
        ShardResult result = node.execute(query);
        // Only docs 0-9 should be returned (user_42 ACL)
        for (var score : result.topK()) {
            assertThat(score.docId().value()).isLessThan(10);
        }
    }

    @Test
    void tombstonedDocsExcluded() {
        UnifiedSegment seg = buildSegment();
        // Tombstone all docs the principal is otherwise authorised for
        for (int i = 0; i < 10; i++) seg.markTombstone(DocId.of(i));

        ContentNode node = new ContentNode(seg);
        Principal user = new Principal("42", "t", Set.of()); // user_42 only
        Query query = new Query(
            List.of(Term.of("cisco")),
            vec(5),
            tokens(7),
            user,
            10
        );
        ShardResult result = node.execute(query);
        // With all the user's docs tombstoned, the result must be empty
        assertThat(result.topK()).isEmpty();
    }
}
