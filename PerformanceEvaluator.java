package com.library.benchmark;

import com.library.connection.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Benchmarks multiple JDBC strategies and produces a structured report.
 *
 * Test suites:
 *  1. Individual INSERT vs Batch INSERT (1,000 and 10,000 records)
 *  2. Full table scan vs Indexed lookup on Loans
 *  3. Statement (string concat) vs PreparedStatement
 *  4. Per-operation commit vs Batched commit (100 operations)
 */
public class PerformanceEvaluator {

    private static final int RUNS        = 5;   // repetitions per test
    private static final int WARM_UP     = 2;   // warm-up runs (discarded)
    private static final int BATCH_SMALL = 1000;
    private static final int BATCH_LARGE = 10000;
    private static final int COMMIT_OPS  = 100;

    // ─────────────────────────────────────────────
    //  PUBLIC ENTRY POINT
    // ─────────────────────────────────────────────

    public static void runAllBenchmarks() throws SQLException {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║           JDBC PERFORMANCE EVALUATION REPORT             ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        List<BenchmarkResult> results = new ArrayList<>();

        results.addAll(benchmarkInsertStrategies());
        results.addAll(benchmarkQueryStrategies());
        results.addAll(benchmarkStatementTypes());
        results.addAll(benchmarkTransactionGranularity());

        printReport(results);
        cleanupBenchmarkData();
    }

    // ─────────────────────────────────────────────
    //  TEST SUITE 1 — INSERT STRATEGIES
    // ─────────────────────────────────────────────

    private static List<BenchmarkResult> benchmarkInsertStrategies() throws SQLException {
        List<BenchmarkResult> results = new ArrayList<>();
        System.out.println("[Benchmark] Running INSERT strategy tests...");

        for (int count : new int[]{BATCH_SMALL, BATCH_LARGE}) {
            // Individual inserts
            List<Long> times = new ArrayList<>();
            for (int run = 0; run < RUNS + WARM_UP; run++) {
                cleanupBenchTable();
                long ms = timeIndividualInsert(count);
                if (run >= WARM_UP) times.add(ms);
            }
            results.add(new BenchmarkResult(
                "Individual INSERT", count, mean(times), stddev(times),
                throughput(count, mean(times)),
                "High per-commit overhead"));

            // Batch inserts
            List<Long> batchTimes = new ArrayList<>();
            for (int run = 0; run < RUNS + WARM_UP; run++) {
                cleanupBenchTable();
                long ms = timeBatchInsert(count);
                if (run >= WARM_UP) batchTimes.add(ms);
            }
            results.add(new BenchmarkResult(
                "Batch INSERT", count, mean(batchTimes), stddev(batchTimes),
                throughput(count, mean(batchTimes)),
                "Reduced round-trips to DB"));
        }
        return results;
    }

    // ─────────────────────────────────────────────
    //  TEST SUITE 2 — QUERY STRATEGIES
    // ─────────────────────────────────────────────

    private static List<BenchmarkResult> benchmarkQueryStrategies() throws SQLException {
        List<BenchmarkResult> results = new ArrayList<>();
        System.out.println("[Benchmark] Running QUERY strategy tests...");

        // Ensure there's data to query
        seedBenchLoans(BATCH_LARGE);

        // Full table scan (no index hint — Derby will scan)
        List<Long> scanTimes = new ArrayList<>();
        for (int run = 0; run < RUNS + WARM_UP; run++) {
            long ms = timeFullTableScan();
            if (run >= WARM_UP) scanTimes.add(ms);
        }
        results.add(new BenchmarkResult(
            "Full Table Scan", BATCH_LARGE, mean(scanTimes), stddev(scanTimes),
            0,
            "Derby buffer helps but scales poorly"));

        // Indexed lookup on Loans.Status
        List<Long> indexTimes = new ArrayList<>();
        for (int run = 0; run < RUNS + WARM_UP; run++) {
            long ms = timeIndexedLookup();
            if (run >= WARM_UP) indexTimes.add(ms);
        }
        results.add(new BenchmarkResult(
            "Indexed Lookup", BATCH_LARGE, mean(indexTimes), stddev(indexTimes),
            0,
            "Index on Status dramatically faster"));

        return results;
    }

    // ─────────────────────────────────────────────
    //  TEST SUITE 3 — STATEMENT TYPE
    // ─────────────────────────────────────────────

    private static List<BenchmarkResult> benchmarkStatementTypes() throws SQLException {
        List<BenchmarkResult> results = new ArrayList<>();
        System.out.println("[Benchmark] Running STATEMENT TYPE tests...");
        int ops = 500;

        // Raw Statement (string concat — avoid in production!)
        List<Long> stmtTimes = new ArrayList<>();
        for (int run = 0; run < RUNS + WARM_UP; run++) {
            long ms = timeRawStatement(ops);
            if (run >= WARM_UP) stmtTimes.add(ms);
        }
        results.add(new BenchmarkResult(
            "Raw Statement", ops, mean(stmtTimes), stddev(stmtTimes),
            throughput(ops, mean(stmtTimes)),
            "Parse overhead every execution"));

        // PreparedStatement
        List<Long> prepTimes = new ArrayList<>();
        for (int run = 0; run < RUNS + WARM_UP; run++) {
            long ms = timePreparedStatement(ops);
            if (run >= WARM_UP) prepTimes.add(ms);
        }
        results.add(new BenchmarkResult(
            "PreparedStatement", ops, mean(prepTimes), stddev(prepTimes),
            throughput(ops, mean(prepTimes)),
            "Compiled once, reused for all ops"));

        return results;
    }

    // ─────────────────────────────────────────────
    //  TEST SUITE 4 — TRANSACTION GRANULARITY
    // ─────────────────────────────────────────────

    private static List<BenchmarkResult> benchmarkTransactionGranularity() throws SQLException {
        List<BenchmarkResult> results = new ArrayList<>();
        System.out.println("[Benchmark] Running TRANSACTION GRANULARITY tests...");

        // Per-operation commit
        List<Long> perOpTimes = new ArrayList<>();
        for (int run = 0; run < RUNS + WARM_UP; run++) {
            cleanupBenchTable();
            long ms = timePerOpCommit(COMMIT_OPS);
            if (run >= WARM_UP) perOpTimes.add(ms);
        }
        results.add(new BenchmarkResult(
            "Per-op Commit", COMMIT_OPS, mean(perOpTimes), stddev(perOpTimes),
            throughput(COMMIT_OPS, mean(perOpTimes)),
            "Disk flush per transaction"));

        // Batched commit
        List<Long> batchedTimes = new ArrayList<>();
        for (int run = 0; run < RUNS + WARM_UP; run++) {
            cleanupBenchTable();
            long ms = timeBatchedCommit(COMMIT_OPS);
            if (run >= WARM_UP) batchedTimes.add(ms);
        }
        results.add(new BenchmarkResult(
            "Batched Commit", COMMIT_OPS, mean(batchedTimes), stddev(batchedTimes),
            throughput(COMMIT_OPS, mean(batchedTimes)),
            "Single flush for all ops"));

        return results;
    }

    // ─────────────────────────────────────────────
    //  TIMING HELPERS
    // ─────────────────────────────────────────────

    private static long timeIndividualInsert(int count) throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        ensureBenchTable(conn);
        conn.setAutoCommit(false);
        long start = System.nanoTime();
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO BenchData (Val) VALUES (?)");
        for (int i = 0; i < count; i++) {
            ps.setInt(1, i);
            ps.executeUpdate();
            conn.commit();
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        ps.close();
        conn.setAutoCommit(true);
        return elapsed;
    }

    private static long timeBatchInsert(int count) throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        ensureBenchTable(conn);
        conn.setAutoCommit(false);
        long start = System.nanoTime();
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO BenchData (Val) VALUES (?)");
        for (int i = 0; i < count; i++) {
            ps.setInt(1, i);
            ps.addBatch();
        }
        ps.executeBatch();
        conn.commit();
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        ps.close();
        conn.setAutoCommit(true);
        return elapsed;
    }

    private static long timeFullTableScan() throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        long start = System.nanoTime();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM BenchLoans")) {
            int count = 0;
            while (rs.next()) count++;
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static long timeIndexedLookup() throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        long start = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM BenchLoans WHERE Status = ?")) {
            ps.setString(1, "ACTIVE");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {}
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    @SuppressWarnings("deprecation")
    private static long timeRawStatement(int ops) throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        long start = System.nanoTime();
        try (Statement st = conn.createStatement()) {
            for (int i = 0; i < ops; i++) {
                int id = (i % 5) + 1;
                ResultSet rs = st.executeQuery(
                    "SELECT Name FROM Members WHERE MemberID = " + id);
                while (rs.next()) {}
                rs.close();
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static long timePreparedStatement(int ops) throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        long start = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT Name FROM Members WHERE MemberID = ?")) {
            for (int i = 0; i < ops; i++) {
                ps.setInt(1, (i % 5) + 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {}
                }
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static long timePerOpCommit(int ops) throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        ensureBenchTable(conn);
        conn.setAutoCommit(false);
        long start = System.nanoTime();
        PreparedStatement ps = conn.prepareStatement("INSERT INTO BenchData (Val) VALUES (?)");
        for (int i = 0; i < ops; i++) {
            ps.setInt(1, i);
            ps.executeUpdate();
            conn.commit();
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        ps.close();
        conn.setAutoCommit(true);
        return elapsed;
    }

    private static long timeBatchedCommit(int ops) throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        ensureBenchTable(conn);
        conn.setAutoCommit(false);
        long start = System.nanoTime();
        PreparedStatement ps = conn.prepareStatement("INSERT INTO BenchData (Val) VALUES (?)");
        for (int i = 0; i < ops; i++) {
            ps.setInt(1, i);
            ps.addBatch();
        }
        ps.executeBatch();
        conn.commit();
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        ps.close();
        conn.setAutoCommit(true);
        return elapsed;
    }

    // ─────────────────────────────────────────────
    //  TABLE SETUP / TEARDOWN
    // ─────────────────────────────────────────────

    private static void ensureBenchTable(Connection conn) throws SQLException {
        if (!ConnectionManager.tableExists(conn, "BENCHDATA")) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE BenchData (ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, Val INT)");
            }
        }
    }

    private static void cleanupBenchTable() throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        ensureBenchTable(conn);
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM BenchData");
        }
    }

    private static void seedBenchLoans(int count) throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        if (!ConnectionManager.tableExists(conn, "BENCHLOANS")) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(
                    "CREATE TABLE BenchLoans (ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, Status VARCHAR(10))");
                st.executeUpdate("CREATE INDEX idx_bench_status ON BenchLoans(Status)");
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM BenchLoans")) {
            rs.next();
            if (rs.getInt(1) >= count) return;
        }
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO BenchLoans (Status) VALUES (?)")) {
            for (int i = 0; i < count; i++) {
                ps.setString(1, i % 3 == 0 ? "RETURNED" : "ACTIVE");
                ps.addBatch();
                if (i % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static void cleanupBenchmarkData() throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        try (Statement st = conn.createStatement()) {
            if (ConnectionManager.tableExists(conn, "BENCHDATA"))
                st.executeUpdate("DROP TABLE BenchData");
            if (ConnectionManager.tableExists(conn, "BENCHLOANS"))
                st.executeUpdate("DROP TABLE BenchLoans");
        }
        System.out.println("[Benchmark] Cleanup complete.");
    }

    // ─────────────────────────────────────────────
    //  STATS & REPORT
    // ─────────────────────────────────────────────

    private static double mean(List<Long> times) {
        return times.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static double stddev(List<Long> times) {
        double avg = mean(times);
        double variance = times.stream()
            .mapToDouble(t -> Math.pow(t - avg, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }

    private static double throughput(int ops, double ms) {
        return ms > 0 ? (ops / (ms / 1000.0)) : 0;
    }

    private static void printReport(List<BenchmarkResult> results) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                    BENCHMARK RESULTS TABLE                                              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ %-25s │ %-10s │ %-14s │ %-10s │ %-16s │ %-20s ║%n",
            "Operation", "Records", "Avg Time (ms)", "± StdDev", "Throughput/sec", "Observation");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════╣");
        for (BenchmarkResult r : results) {
            System.out.printf("║ %-25s │ %-10d │ %-14.1f │ ±%-9.1f │ %-16.0f │ %-20s ║%n",
                r.operation, r.records, r.avgMs, r.stdDev, r.throughput, r.observation);
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println("\n* All averages computed over " + RUNS + " runs after " + WARM_UP + " warm-up runs.");
    }

    // ─────────────────────────────────────────────
    //  RESULT DTO
    // ─────────────────────────────────────────────

    static class BenchmarkResult {
        String operation;
        int    records;
        double avgMs;
        double stdDev;
        double throughput;
        String observation;

        BenchmarkResult(String op, int rec, double avg, double sd, double tp, String obs) {
            this.operation   = op;
            this.records     = rec;
            this.avgMs       = avg;
            this.stdDev      = sd;
            this.throughput  = tp;
            this.observation = obs;
        }
    }
}
