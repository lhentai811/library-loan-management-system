# Library Loan Management System — JDBC + Apache Derby

End-to-End JDBC application demonstrating **explicit transaction management**, **ACID compliance**, and **performance benchmarking** using Apache Derby embedded database.

---

## Project Structure

```
jdbc-library/
├── src/main/java/com/library/
│   ├── connection/
│   │   └── ConnectionManager.java      # DB init, connection lifecycle, shutdown
│   ├── transaction/
│   │   └── TransactionService.java     # commit / rollback / savepoint logic
│   ├── business/
│   │   └── BusinessLogic.java          # CRUD operations, queries
│   ├── benchmark/
│   │   └── PerformanceEvaluator.java   # Benchmark suites, report generator
│   └── ui/
│       └── MainApp.java                # CLI menu, entry point
├── lib/
│   └── derby.jar                       # Place Apache Derby JAR here
├── README.md
└── ANALYSIS.md
```

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java JDK | 11 or higher |
| Apache Derby | 10.16+ |

**Download Derby:**  
https://db.apache.org/derby/derby_downloads.html  
→ Download `db-derby-X.X.X-bin.zip` → extract → copy `lib/derby.jar` into the project's `lib/` folder.

---

## Build & Run

### Option 1 — Command Line (javac)

```bash
# 1. Compile all source files
javac -cp lib/derby.jar -d out \
  src/main/java/com/library/connection/ConnectionManager.java \
  src/main/java/com/library/transaction/TransactionService.java \
  src/main/java/com/library/business/BusinessLogic.java \
  src/main/java/com/library/benchmark/PerformanceEvaluator.java \
  src/main/java/com/library/ui/MainApp.java

# 2. Run the application
java -cp out:lib/derby.jar com.library.ui.MainApp

# Windows users — use semicolons:
java -cp "out;lib/derby.jar" com.library.ui.MainApp
```

### Option 2 — IntelliJ IDEA

1. Open the project folder in IntelliJ.
2. Go to **File → Project Structure → Libraries → + → Java** and add `lib/derby.jar`.
3. Set `MainApp` as the run configuration.
4. Click **Run**.

### Option 3 — Maven (optional)

Add Derby dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.apache.derby</groupId>
    <artifactId>derby</artifactId>
    <version>10.16.1.1</version>
</dependency>
```

---

## Features

### Core Functionality
- Register members and add books to the catalog
- Process book loans with full ACID transaction support
- Process returns — updates loan status, book availability, and member loan count
- Query active loans by member
- Query overdue books
- Transaction isolation demo (duplicate constraint violation + rollback)

### Transaction Management
- `setAutoCommit(false)` on all data-modifying operations
- Explicit `commit()` on success
- `rollback()` on any `SQLException`
- `setSavepoint()` for partial rollback (e.g. roll back only loan insertion if member update fails)

### Performance Benchmarks
Runs automatically with warm-up phases and averages over 5 runs:

| Test Suite | Strategies Compared |
|---|---|
| Insert Strategy | Individual INSERT vs Batch INSERT (1K and 10K rows) |
| Query Strategy | Full table scan vs Indexed lookup |
| Statement Type | `Statement` (string concat) vs `PreparedStatement` |
| Transaction Granularity | Per-operation commit vs Batched commit (100 ops) |

---

## Sample CLI Session

```
╔═══════════════════════════════════════════════════╗
║    Library Loan Management System — JDBC/Derby    ║
╚═══════════════════════════════════════════════════╝
[DB] Connected to Apache Derby: librarydb
[DB] Seed data inserted (5 members, 8 books)

╔══════════════ MAIN MENU ══════════════╗
║  1. Member Management                 ║
║  2. Book Management                   ║
║  3. Loan Management                   ║
║  4. Reports & Queries                 ║
║  5. Performance Benchmarks            ║
║  6. Transaction Demo (Isolation)      ║
║  0. Exit                              ║
╚═══════════════════════════════════════╝
Select option: 3

── Loan Management ──
  1. Process a loan
  ...
  MemberID : 1
  BookID   : 2
  Due date (YYYY-MM-DD): 2026-06-30
[TX] Loan processed successfully for MemberID=1, BookID=2.
```

---

## Derby Notes

- Database files are created in the working directory as `librarydb/`
- On exit, the shutdown hook calls `jdbc:derby:librarydb;shutdown=true` — the `XJ015` exception is **expected** and indicates a clean shutdown
- To reset the database, simply delete the `librarydb/` folder and re-run

---

## GitHub

```
https://github.com/YOUR_USERNAME/jdbc-derby-library
```
