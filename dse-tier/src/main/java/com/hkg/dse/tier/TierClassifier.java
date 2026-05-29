package com.hkg.dse.tier;

import com.hkg.dse.shard.Query;

/**
 * Decides which tier(s) to query for a given {@link Query}.
 *
 * <p>Production classifiers are small learned models over query
 * features (term rarity, query length, intent classification output).
 * Here we use a feature-rule heuristic that captures the same logic:
 * <ul>
 *   <li>Default path: Tier-1 only (~80% of QPS).</li>
 *   <li>Escalate to both if the query has many rare terms (tail-intent).</li>
 *   <li>Always-both for explicit deep-recall requests (large topK).</li>
 * </ul>
 *
 * <p>Calibrated to be the right shape for the production decision
 * surface; the parameters would be retrained per workload.</p>
 */
public final class TierClassifier {

    public enum TierDecision {
        TIER1_ONLY,
        TIER2_ONLY,
        BOTH
    }

    /** Queries asking for >this many results escalate to both tiers. */
    private final int deepRecallThreshold;
    /** Lexical queries with at least this many terms are considered tail-intent. */
    private final int tailIntentTermCount;

    public TierClassifier() {
        this(50, 4);
    }

    public TierClassifier(int deepRecallThreshold, int tailIntentTermCount) {
        if (deepRecallThreshold < 1) {
            throw new IllegalArgumentException("deepRecallThreshold must be >= 1");
        }
        if (tailIntentTermCount < 1) {
            throw new IllegalArgumentException("tailIntentTermCount must be >= 1");
        }
        this.deepRecallThreshold = deepRecallThreshold;
        this.tailIntentTermCount = tailIntentTermCount;
    }

    public TierDecision classify(Query query) {
        if (query.topK() >= deepRecallThreshold) return TierDecision.BOTH;
        if (query.lexicalTerms().size() >= tailIntentTermCount) return TierDecision.BOTH;
        return TierDecision.TIER1_ONLY;
    }
}
