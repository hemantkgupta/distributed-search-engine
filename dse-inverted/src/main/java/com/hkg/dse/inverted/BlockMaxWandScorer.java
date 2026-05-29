package com.hkg.dse.inverted;

import com.hkg.dse.common.Score;
import com.hkg.dse.common.Term;
import com.hkg.dse.common.TopK;
import com.hkg.dse.pfor.PostingCursor;
import com.hkg.dse.pfor.PostingList;
import com.hkg.dse.roaring.RoaringBitset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Block-Max WAND scorer over an inverted index for a conjunctive
 * ("AND") query.
 *
 * <p>The scorer walks one {@link PostingCursor} per query term in
 * lock-step (DAAT). At each candidate doc-ID, it checks whether the sum
 * of per-block max-impacts across the cursors could exceed the current
 * top-K heap floor. If not, it skips ahead — past the lagging cursor's
 * current block — without scoring any docs in that block.</p>
 *
 * <p>This is the lexical analogue of {@code skipping unauthorised
 * neighbours in the HNSW walk}: it preserves correctness (BMW never
 * skips a doc that could make the top-K) while eliminating most of the
 * work the naive DAAT scorer would do.</p>
 *
 * <p>The optional {@code authorisedDocs} bitset implements the ACL
 * pre-filter: a doc-ID not in the bitset is treated as if it weren't in
 * any posting list. See {@code tradeoffs/pre-filter-vs-post-filter-acl}.</p>
 */
public final class BlockMaxWandScorer {

    private final InvertedIndex index;

    public BlockMaxWandScorer(InvertedIndex index) {
        this.index = index;
    }

    /**
     * Score a conjunctive query.
     *
     * @param queryTerms terms that must all be present
     * @param k          top-K size
     * @param authorisedDocs optional ACL bitset; null = no ACL filter
     */
    public List<Score> scoreAnd(List<Term> queryTerms, int k, RoaringBitset authorisedDocs) {
        List<PostingCursor> cursors = new ArrayList<>();
        for (Term t : queryTerms) {
            Optional<PostingList> postings = index.postingsFor(t);
            if (postings.isEmpty()) {
                // Conjunctive query with a missing term → empty result
                return List.of();
            }
            cursors.add(new PostingCursor(postings.get()));
        }
        TopK heap = new TopK(k);

        while (true) {
            // Align cursors on a candidate doc-ID
            int candidate = cursors.get(0).docId();
            for (PostingCursor c : cursors) {
                if (c.docId() > candidate) candidate = c.docId();
            }
            if (candidate == PostingCursor.NO_MORE_DOCS) break;

            boolean aligned = true;
            for (PostingCursor c : cursors) {
                if (c.docId() != candidate) {
                    if (c.docId() < candidate) c.advance(candidate);
                    if (c.docId() != candidate) aligned = false;
                }
            }
            if (!aligned) continue;
            if (cursors.get(0).docId() == PostingCursor.NO_MORE_DOCS) break;

            // ACL pre-filter: skip unauthorised candidate
            if (authorisedDocs != null && !authorisedDocs.contains(candidate)) {
                advanceAll(cursors, candidate + 1);
                continue;
            }

            // BMW threshold check: sum of per-block max-impacts vs heap floor
            float blockMaxSum = 0f;
            for (PostingCursor c : cursors) blockMaxSum += c.blockMaxImpact();
            double floor = heap.floor();

            if (blockMaxSum > floor) {
                // Score this candidate
                float score = 0f;
                for (PostingCursor c : cursors) score += c.impact();
                heap.offer(Score.of(candidate, score));
                advanceAll(cursors, candidate + 1);
            } else {
                // BMW skip: advance past the smallest block-max-doc-id
                int skipTarget = Arrays.stream(cursors.stream().mapToInt(PostingCursor::blockMaxDocId).toArray())
                    .min().orElse(PostingCursor.NO_MORE_DOCS);
                advanceAll(cursors, skipTarget + 1);
            }
        }

        return heap.sortedResults();
    }

    private static void advanceAll(List<PostingCursor> cursors, int target) {
        for (PostingCursor c : cursors) {
            if (c.docId() < target) c.advance(target);
        }
    }
}
