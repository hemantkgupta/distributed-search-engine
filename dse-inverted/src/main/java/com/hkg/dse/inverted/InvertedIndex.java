package com.hkg.dse.inverted;

import com.hkg.dse.common.Term;
import com.hkg.dse.fst.TermDictionary;
import com.hkg.dse.pfor.Bm25Stats;
import com.hkg.dse.pfor.PostingList;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An inverted index pairing a {@link TermDictionary} with one
 * {@link PostingList} per term.
 *
 * <p>Built once at segment creation time; queried many times. The
 * inverted index is the lexical side of the {@code unified-lexical-vector
 * -segment} pattern — it lives in the same segment as the HNSW graph
 * and shares the same doc-ID space.</p>
 */
public final class InvertedIndex {

    private final TermDictionary dictionary;
    private final Map<Integer, PostingList> postingByOrdinal;
    private final Bm25Stats bm25;
    private final int totalDocs;

    public InvertedIndex(
        TermDictionary dictionary,
        Map<Integer, PostingList> postingByOrdinal,
        Bm25Stats bm25,
        int totalDocs
    ) {
        this.dictionary = dictionary;
        this.postingByOrdinal = Map.copyOf(postingByOrdinal);
        this.bm25 = bm25;
        this.totalDocs = totalDocs;
    }

    public Optional<PostingList> postingsFor(Term term) {
        return dictionary.get(term).map(postingByOrdinal::get);
    }

    public Bm25Stats bm25Stats() {
        return bm25;
    }

    public int totalDocs() {
        return totalDocs;
    }

    public TermDictionary dictionary() {
        return dictionary;
    }

    /**
     * Builder that accepts (doc-ID, doc-length, [terms]) and produces an
     * in-memory inverted index plus BM25 statistics.
     */
    public static final class Builder {

        /** Per-term accumulator: term-ordinal → (docId, tf) pairs in append order. */
        private final Map<Integer, java.util.List<int[]>> postings = new HashMap<>();
        private final TermDictionary dictionary = new TermDictionary();
        /** doc-id → doc length (for BM25 normalisation). */
        private final Map<Integer, Integer> docLengths = new HashMap<>();
        private int totalDocLengthSum = 0;

        public Builder addDocument(int docId, java.util.List<Term> terms) {
            Map<Term, Integer> tf = new HashMap<>();
            for (Term t : terms) {
                tf.merge(t, 1, Integer::sum);
            }
            int docLength = terms.size();
            docLengths.put(docId, docLength);
            totalDocLengthSum += docLength;
            for (var entry : tf.entrySet()) {
                int ord = dictionary.put(entry.getKey());
                postings.computeIfAbsent(ord, k -> new java.util.ArrayList<>())
                    .add(new int[]{docId, entry.getValue()});
            }
            return this;
        }

        public InvertedIndex build() {
            int totalDocs = docLengths.size();
            double avgLen = totalDocs == 0 ? 1.0 : (double) totalDocLengthSum / totalDocs;
            Bm25Stats stats = Bm25Stats.withDefaults(avgLen);
            Map<Integer, PostingList> built = new HashMap<>();
            for (var entry : postings.entrySet()) {
                int ord = entry.getKey();
                var list = entry.getValue();
                list.sort(java.util.Comparator.comparingInt(a -> a[0]));
                PostingList.Builder b = new PostingList.Builder(stats, totalDocs, list.size());
                for (int[] pair : list) {
                    b.add(pair[0], pair[1], docLengths.get(pair[0]));
                }
                built.put(ord, b.build());
            }
            return new InvertedIndex(dictionary, built, stats, totalDocs);
        }
    }
}
