package com.hkg.dse.fst;

import com.hkg.dse.common.Term;

import java.util.Optional;
import java.util.TreeMap;

/**
 * A term dictionary mapping {@link Term} → posting-list ordinal.
 *
 * <p>This is a simplified stand-in for Lucene's Finite State Transducer
 * term dictionary. The production FST encodes terms as a minimal
 * deterministic acyclic automaton with shared prefixes and suffixes,
 * achieving an order-of-magnitude memory reduction over a hash map for
 * 100 M+ terms.</p>
 *
 * <p>The structural property the search engine relies on — fast O(|term|)
 * lookup from term string to posting-list offset — is what we model here.
 * The memory wins are an FST-specific implementation detail not material
 * to demonstrating the inverted-index architecture.</p>
 */
public final class TermDictionary {

    /** Sorted map for deterministic iteration / range queries. */
    private final TreeMap<Term, Integer> termToOrdinal = new TreeMap<>();
    private int nextOrdinal = 0;

    /** Insert a term, returning its newly assigned ordinal (or existing one). */
    public int put(Term term) {
        Integer existing = termToOrdinal.get(term);
        if (existing != null) return existing;
        int ord = nextOrdinal++;
        termToOrdinal.put(term, ord);
        return ord;
    }

    public Optional<Integer> get(Term term) {
        Integer ord = termToOrdinal.get(term);
        return Optional.ofNullable(ord);
    }

    public boolean contains(Term term) {
        return termToOrdinal.containsKey(term);
    }

    public int size() {
        return termToOrdinal.size();
    }

    /** Iterate terms with a given prefix; useful for term-prefix queries. */
    public Iterable<Term> termsWithPrefix(String prefix) {
        Term from = Term.of(prefix);
        // Construct an exclusive upper bound by incrementing the last char
        char[] chars = prefix.toCharArray();
        chars[chars.length - 1]++;
        Term to = Term.of(new String(chars));
        return termToOrdinal.subMap(from, to).keySet();
    }
}
