package com.hkg.dse.acl;

import com.hkg.dse.common.Principal;
import com.hkg.dse.common.Term;
import com.hkg.dse.inverted.InvertedIndex;
import com.hkg.dse.pfor.PostingBlock;
import com.hkg.dse.pfor.PostingList;
import com.hkg.dse.roaring.RoaringBitset;

import java.util.Optional;

/**
 * Builds the per-query authorised-doc Roaring bitmap by ORing the
 * posting lists for the principal's expanded ACL tokens.
 *
 * <p>This is the construction step for the pre-filter bitset that gets
 * passed into both the lexical BMW scorer and the HNSW vector traversal.
 * The OR happens inside the inverted index because ACL terms (e.g.
 * {@code _acl_read:user_42}) are first-class terms with their own
 * posting lists — the same machinery that handles content terms.</p>
 *
 * <p>See {@code tradeoffs/pre-filter-vs-post-filter-acl}.</p>
 */
public final class AclBitsetBuilder {

    private final InvertedIndex index;

    public AclBitsetBuilder(InvertedIndex index) {
        this.index = index;
    }

    public RoaringBitset buildFor(Principal principal) {
        RoaringBitset acl = new RoaringBitset();
        for (String token : principal.aclTokens()) {
            Optional<PostingList> postings = index.postingsFor(Term.aclRead(token));
            postings.ifPresent(list -> orAllDocsInto(acl, list));
        }
        return acl;
    }

    private static void orAllDocsInto(RoaringBitset target, PostingList list) {
        for (PostingBlock block : list.blocks()) {
            for (int docId : block.docIds()) {
                target.add(docId);
            }
        }
    }
}
