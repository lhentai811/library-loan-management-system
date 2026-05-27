# Analysis: Transaction Behavior & Performance Findings

## 1. Transaction Boundaries and Data Integrity

### How Transactions Preserve Integrity During Failure

A **transaction** groups multiple database operations into a single atomic unit — either all succeed or none do. In this project, the `processLoan()` method performs four steps:

1. Verify book availability  
2. Mark book as unavailable  
3. Insert loan record  
4. Increment member's active loan count  

If step 4 fails (e.g. invalid MemberID), without a transaction the database would be left with the book marked unavailable but no loan record — a permanently inconsistent state. With `conn.setAutoCommit(false)` and explicit `conn.rollback()` in the catch block, all four steps are undone atomically.

### Savepoints for Partial Rollback

A **savepoint** lets you roll back to a specific point within a transaction without discarding everything. In `TransactionService`, a savepoint is set after marking the book unavailable:

```java
Savepoint savepointLoan = conn.setSavepoint("LOAN_INSERT");
// insert loan...
// if member update fails:
conn.rollback(savepointLoan);  // undo only loan insertion
conn.rollback();               // then full rollback
```

This demonstrates that savepoints give fine-grained control when multi-step operations have different failure tolerances.

### ACID Properties Demonstrated

| Property | How Demonstrated |
|---|---|
| **Atomicity** | Full rollback on `processLoan()` failure — no partial updates persist |
| **Consistency** | UNIQUE constraint on ISBN prevents duplicates; FK constraints enforce referential integrity |
| **Isolation** | `demonstrateIsolation()` triggers a constraint violation; rollback proves no dirty data bleeds across |
| **Durability** | Derby writes committed data to disk; data survives application restart |

---

## 2. Why Certain JDBC Patterns Outperform Others

### Batch INSERT vs Individual INSERT

Individual inserts perform a network round-trip (even in embedded mode, Derby still processes each statement separately) and commit to disk on every operation. Batch inserts use `addBatch()` + `executeBatch()` to send all records in a single call, drastically reducing parse/compile overhead and allowing Derby to write them in one disk flush.

**Expected speedup: ~10–15x** for 1,000–10,000 records.

### Indexed Lookup vs Full Table Scan

Derby's query planner uses a **B-tree index** on `Loans.Status`. An indexed lookup traverses the tree (O(log n)) instead of scanning every row (O(n)). For 10,000 rows, this reduces the number of page reads from hundreds to single digits.

**Expected speedup: ~15–25x** for selective queries on indexed columns.

### PreparedStatement vs Statement

`Statement` re-parses and re-compiles the SQL on every execution. `PreparedStatement` compiles the query once and reuses the plan for all subsequent calls, only substituting parameters. This eliminates repeated parse overhead and also **prevents SQL injection** since parameters are never interpreted as SQL.

**Expected speedup: ~2–4x** for repeated queries.

### Batched Commit vs Per-Operation Commit

Each `commit()` forces Derby to flush the transaction log to disk (fsync). With per-operation commits, this happens 100 times for 100 operations. With a single batched commit, it happens once. Disk I/O is the dominant bottleneck here.

**Expected speedup: ~10–15x** for 100 small operations.

---

## 3. Trade-offs: Safety vs Raw Speed

| Strategy | Safety | Speed | Use When |
|---|---|---|---|
| Per-op commit | High (each op durable immediately) | Low | Financial transactions, audit logs |
| Batched commit | Medium (data at risk until commit) | High | Bulk loads, ETL pipelines |
| PreparedStatement | High (SQL injection safe, validated) | High | Always — no reason not to use |
| Raw Statement | Low (injection risk) | Low | Never in production |
| Indexed lookup | N/A | High | Frequently filtered columns |
| Full scan | N/A | Low | Only when no filter, or tiny tables |
| Individual INSERT | N/A | Low | Single-row operations only |
| Batch INSERT | N/A | High | Any bulk data operation |

### Recommendation

For production systems:
- Always use `PreparedStatement` — safety and speed benefits are both positive.
- Use batched commits for bulk operations; use per-op commits for critical business transactions.
- Index all columns used in `WHERE` clauses of frequent queries.
- Prefer batch inserts for any operation inserting more than ~10 rows.

---

## 4. Derby-Specific Observations

- **Cold start penalty**: Derby loads pages into its buffer cache on first access. Without a warm-up phase, first-run benchmarks can be 3–5x slower than steady-state. This project discards the first 2 runs of each benchmark.
- **Embedded vs Network mode**: Embedded mode eliminates network overhead but shares the JVM heap with the application. Network mode adds latency but enables concurrent client access.
- **Runtime statistics**: Enable `CALL SYSCS_UTIL.SYSCS_SET_RUNTIMESTATISTICS(1)` to log scan types and join strategies to `derby.log` for deeper query analysis.
