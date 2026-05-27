package com.library.ui;

import com.library.benchmark.PerformanceEvaluator;
import com.library.business.BusinessLogic;
import com.library.connection.ConnectionManager;
import com.library.transaction.TransactionService;

import java.sql.SQLException;
import java.util.Scanner;

/**
 * Entry point and CLI menu for the Library Loan Management System.
 * Orchestrates all layers: connection, transaction, business logic, benchmarks.
 */
public class MainApp {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        // Register shutdown hook for clean Derby exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[App] Shutdown hook triggered...");
            ConnectionManager.shutdown();
        }));

        try {
            System.out.println("╔═══════════════════════════════════════════════════╗");
            System.out.println("║    Library Loan Management System — JDBC/Derby    ║");
            System.out.println("╚═══════════════════════════════════════════════════╝");

            ConnectionManager.initializeSchema();
            mainMenu();

        } catch (Exception e) {
            System.err.println("[App] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────
    //  MAIN MENU
    // ─────────────────────────────────────────────

    private static void mainMenu() {
        while (true) {
            System.out.println("\n╔══════════════ MAIN MENU ══════════════╗");
            System.out.println("║  1. Member Management                 ║");
            System.out.println("║  2. Book Management                   ║");
            System.out.println("║  3. Loan Management                   ║");
            System.out.println("║  4. Reports & Queries                 ║");
            System.out.println("║  5. Performance Benchmarks            ║");
            System.out.println("║  6. Transaction Demo (Isolation)      ║");
            System.out.println("║  0. Exit                              ║");
            System.out.println("╚═══════════════════════════════════════╝");
            System.out.print("Select option: ");

            String choice = scanner.nextLine().trim();
            try {
                switch (choice) {
                    case "1" -> memberMenu();
                    case "2" -> bookMenu();
                    case "3" -> loanMenu();
                    case "4" -> reportsMenu();
                    case "5" -> runBenchmarks();
                    case "6" -> TransactionService.demonstrateIsolation();
                    case "0" -> { System.out.println("[App] Goodbye!"); return; }
                    default  -> System.out.println("  Invalid option. Try again.");
                }
            } catch (SQLException e) {
                System.err.println("  [Error] " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────
    //  MEMBER MENU
    // ─────────────────────────────────────────────

    private static void memberMenu() throws SQLException {
        System.out.println("\n── Member Management ──");
        System.out.println("  1. Register new member");
        System.out.println("  2. List all members");
        System.out.println("  0. Back");
        System.out.print("Select: ");

        switch (scanner.nextLine().trim()) {
            case "1" -> {
                System.out.print("  Name  : "); String name  = scanner.nextLine().trim();
                System.out.print("  Email : "); String email = scanner.nextLine().trim();
                System.out.print("  Phone : "); String phone = scanner.nextLine().trim();
                if (name.isEmpty() || email.isEmpty()) {
                    System.out.println("  Name and Email are required.");
                    return;
                }
                BusinessLogic.registerMember(name, email, phone);
            }
            case "2" -> BusinessLogic.listAllMembers();
            case "0" -> { /* back */ }
            default  -> System.out.println("  Invalid option.");
        }
    }

    // ─────────────────────────────────────────────
    //  BOOK MENU
    // ─────────────────────────────────────────────

    private static void bookMenu() throws SQLException {
        System.out.println("\n── Book Management ──");
        System.out.println("  1. Add new book");
        System.out.println("  2. List all books");
        System.out.println("  3. Search by ISBN");
        System.out.println("  0. Back");
        System.out.print("Select: ");

        switch (scanner.nextLine().trim()) {
            case "1" -> {
                System.out.print("  ISBN   : "); String isbn   = scanner.nextLine().trim();
                System.out.print("  Title  : "); String title  = scanner.nextLine().trim();
                System.out.print("  Author : "); String author = scanner.nextLine().trim();
                System.out.print("  Genre  : "); String genre  = scanner.nextLine().trim();
                if (isbn.isEmpty() || title.isEmpty() || author.isEmpty()) {
                    System.out.println("  ISBN, Title, and Author are required.");
                    return;
                }
                BusinessLogic.addBook(isbn, title, author, genre);
            }
            case "2" -> BusinessLogic.listAllBooks();
            case "3" -> {
                System.out.print("  Enter ISBN: ");
                BusinessLogic.searchByISBN(scanner.nextLine().trim());
            }
            case "0" -> { /* back */ }
            default  -> System.out.println("  Invalid option.");
        }
    }

    // ─────────────────────────────────────────────
    //  LOAN MENU
    // ─────────────────────────────────────────────

    private static void loanMenu() throws SQLException {
        System.out.println("\n── Loan Management ──");
        System.out.println("  1. Process a loan");
        System.out.println("  2. Process a return");
        System.out.println("  3. View all loans");
        System.out.println("  0. Back");
        System.out.print("Select: ");

        switch (scanner.nextLine().trim()) {
            case "1" -> {
                BusinessLogic.listAllMembers();
                System.out.print("  MemberID : ");
                int memberId = parseIntSafe(scanner.nextLine().trim());

                BusinessLogic.listAllBooks();
                System.out.print("  BookID   : ");
                int bookId = parseIntSafe(scanner.nextLine().trim());

                System.out.print("  Due date (YYYY-MM-DD): ");
                String dueDate = scanner.nextLine().trim();

                if (memberId < 0 || bookId < 0 || dueDate.isEmpty()) {
                    System.out.println("  Invalid input.");
                    return;
                }
                TransactionService.processLoan(bookId, memberId, dueDate);
            }
            case "2" -> {
                BusinessLogic.listAllLoans();
                System.out.print("  LoanID to return: ");
                int loanId = parseIntSafe(scanner.nextLine().trim());
                if (loanId < 0) { System.out.println("  Invalid ID."); return; }
                TransactionService.processReturn(loanId);
            }
            case "3" -> BusinessLogic.listAllLoans();
            case "0" -> { /* back */ }
            default  -> System.out.println("  Invalid option.");
        }
    }

    // ─────────────────────────────────────────────
    //  REPORTS MENU
    // ─────────────────────────────────────────────

    private static void reportsMenu() throws SQLException {
        System.out.println("\n── Reports & Queries ──");
        System.out.println("  1. Active loans by member");
        System.out.println("  2. Overdue books");
        System.out.println("  0. Back");
        System.out.print("Select: ");

        switch (scanner.nextLine().trim()) {
            case "1" -> {
                BusinessLogic.listAllMembers();
                System.out.print("  MemberID: ");
                int id = parseIntSafe(scanner.nextLine().trim());
                if (id < 0) { System.out.println("  Invalid ID."); return; }
                BusinessLogic.getActiveLoansByMember(id);
            }
            case "2" -> BusinessLogic.getOverdueBooks();
            case "0" -> { /* back */ }
            default  -> System.out.println("  Invalid option.");
        }
    }

    // ─────────────────────────────────────────────
    //  BENCHMARKS
    // ─────────────────────────────────────────────

    private static void runBenchmarks() {
        System.out.println("\n  [Warning] Benchmarks insert/delete large amounts of data.");
        System.out.print("  Proceed? (y/n): ");
        if (!"y".equalsIgnoreCase(scanner.nextLine().trim())) return;

        try {
            PerformanceEvaluator.runAllBenchmarks();
        } catch (SQLException e) {
            System.err.println("  [Benchmark Error] " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    //  UTIL
    // ─────────────────────────────────────────────

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return -1; }
    }
}
