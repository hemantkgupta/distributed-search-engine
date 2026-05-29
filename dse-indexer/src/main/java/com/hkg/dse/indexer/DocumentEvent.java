package com.hkg.dse.indexer;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Term;

import java.util.List;

/**
 * One CDC document event: a doc-ID, content terms, ACL tokens,
 * payload, and (optionally) a pre-computed embedding.
 *
 * <p>In production the embedding would be computed downstream by the
 * GPU embedding pipeline; here it is part of the event so the test
 * harness can wire deterministic vectors.</p>
 */
public record DocumentEvent(
    DocId docId,
    List<Term> contentTerms,
    List<String> aclTokens,
    String payload,
    float[] embedding,
    float[][] colbertTokens
) {
    public DocumentEvent {
        if (docId == null) throw new IllegalArgumentException("docId required");
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("embedding required");
        }
    }
}
