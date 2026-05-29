# Distributed Search Engine

A Java 17 multi-module reference implementation of a **unified lexical + vector search substrate**, built around the [unified-lexical-vector-segment](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/patterns/unified-lexical-vector-segment.md) pattern: inverted index + HNSW graph + tombstone bitmap co-located on the same content node, served by a two-stage retrieval pipeline (BM25 + Block-Max WAND lexical, PQ32-HNSW vector, RRF fusion, ColBERT-style rerank).

Companion code for the [`distributed-search-engine`](https://github.com/hemantkgupta/CSE-Raw/blob/main/raw-blog/distributed-search-engine.md) and [`distributed-search-engine-full`](https://github.com/hemantkgupta/CSE-Raw/blob/main/raw-blog/distributed-search-engine-full.md) blog posts in the CSE wiki.

## Status

In-progress build over 4 phases. Each phase is committed and pushed at its boundary.

The phase plan:

**Phase 1 — Lexical retrieval primitives:**
* **CP1** — `dse-common`: foundational types (DocId, Score, Term, Principal, AclToken)
* **CP2** — `dse-roaring`: minimal Roaring-style sparse bitset for tombstones + ACL bitsets
* **CP3** — `dse-pfor`: PFOR-Delta posting codec + 128-int blocks with per-block max-impact
* **CP4** — `dse-fst`: Finite-State-Transducer-shaped term dictionary
* **CP5** — `dse-inverted`: inverted index with BM25 + Block-Max WAND scoring

**Phase 2 — Vector retrieval + unified segment:**
* **CP6** — `dse-hnsw`: HNSW graph + PQ32 vector compression + ADC distance + ACL pre-filter
* **CP7** — `dse-rrf`: Reciprocal Rank Fusion
* **CP8** — `dse-colbert`: ColBERT-style MaxSim reranker (simplified)
* **CP9** — `dse-segment`: unified segment composing inverted + HNSW + tombstones + forward
* **CP10** — `dse-merge`: tiered merge policy + HNSW rebuild over surviving doc-IDs

**Phase 3 — Distribution + ingest:**
* **CP11** — `dse-acl`: principal expansion + ACL bitset builder
* **CP12** — `dse-shard`: content node — ACL build → BMW + HNSW → RRF → ColBERT rerank
* **CP13** — `dse-broker`: scatter-gather fan-out, per-shard timeout, RRF cross-shard, partial results
* **CP14** — `dse-indexer`: CDC consumer + segment builder + flush

**Phase 4 — Ops + integration:**
* **CP15** — `dse-metrics`: counters + histograms (latency, recall, merge IO)
* **CP16** — `dse-node`: end-to-end integration (broker + shards + indexer in-process)
* **CP17** — `dse-tier`: Tier-1/Tier-2 routing classifier
* **CP18** — `deploy/`: K8s YAML for content node + broker + indexer

## Architecture

The repo embodies the architectural decisions from the [2026 deep-research report](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/sources/distributed-search-engine-deep-research-report.md):

- **Document-partitioned sharding** (`dse-broker` routes by `hash(doc_id) % shard_count`)
- **Unified segment** (`dse-segment` co-locates inverted + HNSW + forward + tombstones)
- **BM25 + Block-Max WAND** (`dse-inverted`)
- **PQ32-HNSW with ACL pre-filter inside the traversal** (`dse-hnsw`)
- **RRF score-agnostic fusion** (`dse-rrf`)
- **ColBERT-style late-interaction rerank** (`dse-colbert`)
- **Tiered merge with HNSW rebuild** (`dse-merge`)
- **Per-shard timeout + partial results** (`dse-broker`)
- **CDC-driven indexing** (`dse-indexer`)
- **Tier-1/Tier-2 serving** (`dse-tier`)

## Build

Requires JDK 17 (pinned via `jenv local 17.0`).

```sh
./gradlew build
./gradlew :dse-common:test
```

## Pedagogical Scope

This is a **teaching artifact**, not a production system. Choices reflect that:

- Tokenisation is whitespace-only; no language analyzers.
- Embedding pipeline is mocked — vectors are seeded from text hashes for determinism.
- HNSW is a single-layer beam search rather than the full hierarchical variant.
- ColBERT MaxSim uses simple token-level vectors without the full BERT integration.
- No on-disk segment serialisation; everything is in-memory.
- No actual network — broker fans out to in-process shards.
- No GPU embedding pipeline; embedding is a CPU stub.

The pedagogical value is **feeling the data structures**: how BMW prunes, how PQ ADC saves memory, how the ACL bitset passes through the HNSW walk, how RRF fuses without per-corpus tuning, how tiered merge rebuilds the HNSW graph.

## Wiki References

- [unified-lexical-vector-segment pattern](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/patterns/unified-lexical-vector-segment.md)
- [tiered-shard-serving pattern](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/patterns/tiered-shard-serving.md)
- [block-max-wand concept](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/concepts/block-max-wand.md)
- [hnsw-graph concept](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/concepts/hnsw-graph.md)
- [product-quantization concept](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/concepts/product-quantization.md)
- [reciprocal-rank-fusion concept](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/concepts/reciprocal-rank-fusion.md)
- [roaring-bitmap concept](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/concepts/roaring-bitmap.md)
- [unified-vs-bolt-on tradeoff](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/tradeoffs/unified-vs-bolt-on-vector-store.md)
- [pre-filter-vs-post-filter-acl tradeoff](https://github.com/hemantkgupta/CSE-Raw/blob/main/wiki/tradeoffs/pre-filter-vs-post-filter-acl.md)
