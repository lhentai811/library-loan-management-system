package com.library.business;

import com.library.connection.ConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains all business-layer database operations:
 * member registration, book addition, and query methods.
 * All queries use PreparedStatement and try-with-resources.
 */
public class BusinessLogic {

    // ─────────────────────────────────────────────
    //  MEMBER OPERATIONS
    // ─────────────────────────────────────────────

    /**
     * Registers a new library member.
     *
     * @return generated MemberID, or -1 on failure
     */
    public static int registerMember(String name, String email, String phone) throws SQLException {
        String sql = "INSERT INTO Members (Name, Email, Phone) VALUES (?, ?, ?)";
        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    System.out.println("[BL] Member registered: " + name + " (ID=" + id + ")");
                    return id;
                }
            }
        }
        return -1;
    }

    /**
     * Lists all registered members.
     */
    public static void listAllMembers() throws SQLException {
        String sql = "SELECT MemberID, Name, Email, Phone, ActiveLoans, JoinDate FROM Members ORDER BY MemberID";
        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n┌─────┬──────────────────────┬──────────────────────┬─────────────┬──────────────┬────────────┐");
            System.out.printf ("│ %-3s │ %-20s │ %-20s │ %-11s │ %-12s │ %-10s │%n",
                "ID", "Name", "Email", "Phone", "ActiveLoans", "JoinDate");
            System.out.println("├─────┼──────────────────────┼──────────────────────┼─────────────┼──────────────┼────────────┤");
            while (rs.next()) {
                System.out.printf("│ %-3d │ %-20s │ %-20s │ %-11s │ %-12d │ %-10s │%n",
                    rs.getInt("MemberID"), rs.getString("Name"),
                    rs.getString("Email"),   rs.getString("Phone"),
                    rs.getInt("ActiveLoans"), rs.getDate("JoinDate"));
            }
            System.out.println("└─────┴──────────────────────┴──────────────────────┴─────────────┴──────────────┴────────────┘");
        }
    }

    // ─────────────────────────────────────────────
    //  BOOK OPERATIONS
    // ─────────────────────────────────────────────

    /**
     * Adds a new book to the catalog.
     *
     * @return generated BookID, or -1 on failure
     */
    public static int addBook(String isbn, String title, String author, String genre) throws SQLException {
        String sql = "INSERT INTO Books (ISBN, Title, Author, Genre) VALUES (?, ?, ?, ?)";
        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, isbn);
            ps.setString(2, title);
            ps.setString(3, author);
            ps.setString(4, genre);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    System.out.println("[BL] Book added: " + title + " (ID=" + id + ")");
                    return id;
                }
            }
        }
        return -1;
    }

    /**
     * Lists all books with their availability status.
     */
    public static void listAllBooks() throws SQLException {
        String sql = "SELECT BookID, ISBN, Title, Author, Genre, Available FROM Books ORDER BY BookID";
        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n┌──────┬─────────────────────┬──────────────────────────────┬──────────────────┬────────────┬───────────┐");
            System.out.printf ("│ %-4s │ %-19s │ %-28s │ %-16s │ %-10s │ %-9s │%n",
                "ID", "ISBN", "Title", "Author", "Genre", "Available");
            System.out.println("├──────┼─────────────────────┼──────────────────────────────┼──────────────────┼────────────┼───────────┤");
            while (rs.next()) {
                System.out.printf("│ %-4d │ %-19s │ %-28s │ %-16s │ %-10s │ %-9s │%n",
                    rs.getInt("BookID"),
                    rs.getString("ISBN"),
                    truncate(rs.getString("Title"), 28),
                    truncate(rs.getString("Author"), 16),
                    truncate(rs.getString("Genre"), 10),
                    rs.getBoolean("Available") ? "Yes" : "No");
            }
            System.out.println("└──────┴─────────────────────┴──────────────────────────────┴──────────────────┴────────────┴───────────┘");
        }
    }

    /**
     * Searches for a book by ISBN using the indexed column.
     */
    public static void searchByISBN(String isbn) throws SQLException {
        String sql = "SELECT BookID, Title, Author, Genre, Available FROM Books WHERE ISBN = ?";
        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.printf("[BL] Found — ID: %d | Title: %s | Author: %s | Available: %s%n",
                        rs.getInt("BookID"), rs.getString("Title"),
                        rs.getString("Author"), rs.getBoolean("Available") ? "Yes" : "No");
                } else {
                    System.out.println("[BL] No book found with ISBN: " + isbn);
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    //  LOAN QUERIES
    // ─────────────────────────────────────────────

    /**
     * Lists all active loans for a specific member.
     */
    public static void getActiveLoansByMember(int memberId) throws SQLException {
        String sql =
            "SELECT l.LoanID, b.Title, b.Author, l.LoanDate, l.DueDate " +
            "FROM Loans l JOIN Books b ON l.BookID = b.BookID " +
            "WHERE l.MemberID = ? AND l.Status = 'ACTIVE' " +
            "ORDER BY l.DueDate";

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n--- Active Loans for MemberID=" + memberId + " ---");
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("  LoanID: %-4d | Book: %-30s | Author: %-18s | Loaned: %s | Due: %s%n",
                        rs.getInt("LoanID"), rs.getString("Title"),
                        rs.getString("Author"), rs.getDate("LoanDate"), rs.getDate("DueDate"));
                }
                if (!found) System.out.println("  No active loans found.");
            }
        }
    }

    /**
     * Lists all overdue books (due date passed, status still ACTIVE).
     */
    public static void getOverdueBooks() throws SQLException {
        String sql =
            "SELECT l.LoanID, m.Name, m.Email, b.Title, l.DueDate, " +
            "       (CURRENT_DATE - l.DueDate) AS DaysOverdue " +
            "FROM Loans l " +
            "JOIN Members m ON l.MemberID = m.MemberID " +
            "JOIN Books   b ON l.BookID   = b.BookID " +
            "WHERE l.Status = 'ACTIVE' AND l.DueDate < CURRENT_DATE " +
            "ORDER BY l.DueDate";

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n--- Overdue Books ---");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf("  LoanID: %-4d | Member: %-20s | Book: %-30s | Due: %s | Days Overdue: %d%n",
                    rs.getInt("LoanID"), rs.getString("Name"),
                    rs.getString("Title"), rs.getDate("DueDate"),
                    rs.getInt("DaysOverdue"));
            }
            if (!found) System.out.println("  No overdue books. Great!");
        }
    }

    /**
     * Lists all loans (active and returned).
     */
    public static void listAllLoans() throws SQLException {
        String sql =
            "SELECT l.LoanID, m.Name, b.Title, l.LoanDate, l.DueDate, l.ReturnDate, l.Status " +
            "FROM Loans l " +
            "JOIN Members m ON l.MemberID = m.MemberID " +
            "JOIN Books   b ON l.BookID   = b.BookID " +
            "ORDER BY l.LoanID";

        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n--- All Loans ---");
            while (rs.next()) {
                System.out.printf("  LoanID: %-4d | Member: %-20s | Book: %-30s | Status: %-10s | Due: %s%n",
                    rs.getInt("LoanID"), rs.getString("Name"),
                    rs.getString("Title"), rs.getString("Status"), rs.getDate("DueDate"));
            }
        }
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────

    /**
     * Returns true if a MemberID exists in the database.
     */
    public static boolean memberExists(int id) throws SQLException {
        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM Members WHERE MemberID = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    /**
     * Returns true if a BookID exists in the database.
     */
    public static boolean bookExists(int id) throws SQLException {
        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM Books WHERE BookID = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    /**
     * Returns a list of [MemberID, Name] pairs for display in menus.
     */
    public static List<int[]> getMemberIds() throws SQLException {
        List<int[]> result = new ArrayList<>();
        try (Connection conn = ConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT MemberID FROM Members ORDER BY MemberID");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(new int[]{rs.getInt(1)});
        }
        return result;
    }
}
