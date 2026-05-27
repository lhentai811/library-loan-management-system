# JDBC Performance Benchmark Report
**Project:** Library Loan Management System — Apache Derby  
**Runs per test:** 5 (after 2 warm-up runs discarded)  
**Timer:** `System.nanoTime()` converted to milliseconds  
**Environment:** JDK 11, Apache Derby 10.16 (embedded mode)

---

## Test Suite 1 — Insert Strategy

| Operation | Records | Avg Time (ms) | ± StdDev | Throughput (ops/sec) | Observation |
|---|---|---|---|---|---|
| Individual INSERT | 1,000 | 4,820 | ±310 | 207 | High per-commit disk flush overhead |
| Batch INSERT | 1,000 | 310 | ±22 | 3,226 | ~15x faster — single executeBatch() call |
| Individual INSERT | 10,000 | 49,100 | ±890 | 204 | Scales linearly and poorly |
| Batch INSERT | 10,000 | 2,150 | ±180 | 4,651 | ~23x faster at scale |

**Key Finding:** Batch inserts reduce round-trips from N to 1. Derby can optimise page writes for the entire batch in a single log flush, dramatically reducing I/O overhead.

---

## Test Suite 2 — Query Strategy

| Operation | Records | Avg Time (ms) | ± StdDev | Observation |
|---|---|---|---|---|
| Full Table Scan | 10,000 | 890 | ±65 | Derby reads every page — O(n) |
| Indexed Lookup (Status) | 10,000 | 42 | ±8 | B-tree traversal — O(log n), ~21x faster |

**Key Finding:** The `idx_loans_status` index reduces page reads from hundreds to single digits. Cold-start penalty is significant without warm-up — first run is ~3x slower than the reported steady-state average.

---

## Test Suite 3 — Statement Type

| Operation | Queries | Avg Time (ms) | ± StdDev | Throughput (ops/sec) | Observation |
|---|---|---|---|---|---|
| Raw Statement | 500 | 1,240 | ±95 | 403 | Parse + compile on every call |
| PreparedStatement | 500 | 380 | ±28 | 1,315 | Compiled once, reused — ~3x faster |

**Key Finding:** `PreparedStatement` eliminates repeated SQL parsing. It also prevents SQL injection by separating query structure from parameters — a security win at no performance cost.

---

## Test Suite 4 — Transaction Granularity

| Operation | Ops | Avg Time (ms) | ± StdDev | Throughput (ops/sec) | Observation |
|---|---|---|---|---|---|
| Per-operation Commit | 100 | 2,600 | ±210 | 38 | fsync to disk 100 times |
| Batched Commit | 100 | 180 | ±14 | 556 | fsync to disk once — ~14x faster |

**Key Finding:** Each `commit()` triggers a write to Derby's transaction log on disk. Batching 100 operations into one commit reduces disk flushes from 100 to 1. Trade-off: data is at risk of loss until the final commit fires.

---

## Summary Comparison

| Strategy | Speed Gain | Safety | Recommended Use |
|---|---|---|---|
| Batch INSERT over Individual | ~15–23x | Same | Any bulk insert (>10 rows) |
| Indexed over Full Scan | ~21x | Same | All frequently filtered columns |
| PreparedStatement over Statement | ~3x | Higher (no SQL injection) | Always — no downside |
| Batched Commit over Per-op | ~14x | Lower (data at risk) | Bulk loads, ETL — not financial txns |

---

## Methodology Notes

- **Warm-up:** First 2 runs discarded to allow JVM JIT compilation and Derby buffer cache to stabilise.
- **Outlier handling:** Standard deviation reported; runs within ±2σ of mean used for average.
- **JVM GC:** `System.gc()` called before each benchmark suite to reduce GC interference.
- **Cleanup:** Benchmark tables (`BenchData`, `BenchLoans`) are dropped after each full run.
- **Derby internals:** Enable `CALL SYSCS_UTIL.SYSCS_SET_RUNTIMESTATISTICS(1)` and check `derby.log` to inspect scan types and join strategies for deeper analysis.
