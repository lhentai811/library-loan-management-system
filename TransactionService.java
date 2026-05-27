package com.library.transaction;

import com.library.connection.ConnectionManager;

import java.sql.*;

/**
 * Handles all explicit transaction boundaries:
 * commit, rollback, and savepoint-based partial rollback.
 */
public class TransactionService {

    /**
     * Processes a book loan with full ACID compliance.
     * Steps: verify availability → update book → insert loan → update member loan count.
     * Uses a savepoint so only the loan insertion can be rolled back independently.
     *
     * @param bookId   ID of the book to loan
     * @param memberId ID of the member borrowing the book
     * @param dueDate  Due date string in format YYYY-MM-DD
     * @return true if loan was successfully processed
     */
    public static boolean processLoan(int bookId, int memberId, String dueDate) throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        conn.setAutoCommit(false);
        Savepoint savepointLoan = null;

        try {
            // Step 1: Verify book availability
            PreparedStatement checkBook = conn.prepareStatement(
                "SELECT Available, Title FROM Books WHERE BookID = ?");
            checkBook.setInt(1, bookId);
            ResultSet rs = checkBook.executeQuery();
            if (!rs.next()) {
                throw new SQLException("Book ID " + bookId + " not found.");
            }
            if (!rs.getBoolean("Available")) {
                throw new SQLException("Book '" + rs.getString("Title") + "' is not available.");
            }
            rs.close(); checkBook.close();

            // Step 2: Verify member exists
            PreparedStatement checkMember = conn.prepareStatement(
                "SELECT Name FROM Members WHERE MemberID = ?");
            checkMember.setInt(1, memberId);
            ResultSet rm = checkMember.executeQuery();
            if (!rm.next()) {
                throw new SQLException("Member ID " + memberId + " not found.");
            }
            rm.close(); checkMember.close();

            // Step 3: Mark book as unavailable
            PreparedStatement updateBook = conn.prepareStatement(
                "UPDATE Books SET Available = FALSE WHERE BookID = ?");
            updateBook.setInt(1, bookId);
            updateBook.executeUpdate();
            updateBook.close();

            // Savepoint: if loan insert fails, we can roll back just the insert
            savepointLoan = conn.setSavepoint("LOAN_INSERT");

            // Step 4: Insert loan record
            PreparedStatement insertLoan = conn.prepareStatement(
                "INSERT INTO Loans (MemberID, BookID, DueDate) VALUES (?, ?, ?)");
            insertLoan.setInt(1, memberId);
            insertLoan.setInt(2, bookId);
            insertLoan.setDate(3, Date.valueOf(dueDate));
            insertLoan.executeUpdate();
            insertLoan.close();

            // Step 5: Increment member active loan count
            PreparedStatement updateMember = conn.prepareStatement(
                "UPDATE Members SET ActiveLoans = ActiveLoans + 1 WHERE MemberID = ?");
            updateMember.setInt(1, memberId);
            int rows = updateMember.executeUpdate();
            updateMember.close();

            if (rows == 0) {
                // Member update failed — roll back only the loan insertion
                conn.rollback(savepointLoan);
                throw new SQLException("Failed to update member loan count. Partial rollback applied.");
            }

            conn.commit();
            System.out.println("[TX] Loan processed successfully for MemberID=" + memberId +
                               ", BookID=" + bookId + ".");
            return true;

        } catch (SQLException e) {
            System.err.println("[TX] Rolling back loan transaction: " + e.getMessage());
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * Processes a book return. Updates loan status, return date, book availability,
     * and member active loan count — all in a single atomic transaction.
     *
     * @param loanId ID of the loan to close
     * @return true if return was processed successfully
     */
    public static boolean processReturn(int loanId) throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        conn.setAutoCommit(false);

        try {
            // Fetch loan details
            PreparedStatement fetchLoan = conn.prepareStatement(
                "SELECT BookID, MemberID, Status FROM Loans WHERE LoanID = ?");
            fetchLoan.setInt(1, loanId);
            ResultSet rs = fetchLoan.executeQuery();
            if (!rs.next()) throw new SQLException("Loan ID " + loanId + " not found.");
            if ("RETURNED".equals(rs.getString("Status")))
                throw new SQLException("Loan ID " + loanId + " already returned.");

            int bookId   = rs.getInt("BookID");
            int memberId = rs.getInt("MemberID");
            rs.close(); fetchLoan.close();

            // Update loan record
            PreparedStatement closeLoan = conn.prepareStatement(
                "UPDATE Loans SET Status = 'RETURNED', ReturnDate = CURRENT_DATE WHERE LoanID = ?");
            closeLoan.setInt(1, loanId);
            closeLoan.executeUpdate(); closeLoan.close();

            // Mark book as available again
            PreparedStatement freeBook = conn.prepareStatement(
                "UPDATE Books SET Available = TRUE WHERE BookID = ?");
            freeBook.setInt(1, bookId);
            freeBook.executeUpdate(); freeBook.close();

            // Decrement member loan count
            PreparedStatement updateMember = conn.prepareStatement(
                "UPDATE Members SET ActiveLoans = ActiveLoans - 1 WHERE MemberID = ? AND ActiveLoans > 0");
            updateMember.setInt(1, memberId);
            updateMember.executeUpdate(); updateMember.close();

            conn.commit();
            System.out.println("[TX] Return processed for LoanID=" + loanId + ".");
            return true;

        } catch (SQLException e) {
            System.err.println("[TX] Rolling back return transaction: " + e.getMessage());
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * Demonstrates transaction isolation by intentionally triggering a
     * constraint violation and verifying rollback restores data consistency.
     */
    public static void demonstrateIsolation() throws SQLException {
        Connection conn = ConnectionManager.getConnection();
        conn.setAutoCommit(false);
        System.out.println("\n[TX] --- Isolation Demo: Duplicate ISBN insert ---");

        try {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Books (ISBN, Title, Author, Genre) VALUES (?, ?, ?, ?)");
            ps.setString(1, "978-0-13-468599-1"); // Duplicate ISBN — will violate UNIQUE
            ps.setString(2, "Duplicate Book");
            ps.setString(3, "Test Author");
            ps.setString(4, "Test");
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            System.out.println("[TX] Expected constraint violation caught: " + e.getMessage());
            conn.rollback();
            System.out.println("[TX] Rollback successful. Data integrity preserved.");
        } finally {
            conn.setAutoCommit(true);
        }
    }
}
