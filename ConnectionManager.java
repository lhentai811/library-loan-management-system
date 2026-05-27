package com.library.connection;

import java.sql.*;

/**
 * Manages the lifecycle of the Apache Derby embedded database connection.
 * Handles initialization, schema creation, seed data, and graceful shutdown.
 */
public class ConnectionManager {

    private static final String DB_URL = "jdbc:derby:librarydb;create=true";
    private static final String SHUTDOWN_URL = "jdbc:derby:librarydb;shutdown=true";
    private static Connection connection = null;

    /**
     * Returns a singleton connection to the Derby embedded database.
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            System.out.println("[DB] Connected to Apache Derby: librarydb");
        }
        return connection;
    }

    /**
     * Initializes schema: creates tables and indexes if they don't exist.
     */
    public static void initializeSchema() throws SQLException {
        Connection conn = getConnection();
        Statement stmt = conn.createStatement();

        // Members table
        if (!tableExists(conn, "MEMBERS")) {
            stmt.executeUpdate(
                "CREATE TABLE Members (" +
                "  MemberID    INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY," +
                "  Name        VARCHAR(100) NOT NULL," +
                "  Email       VARCHAR(100) UNIQUE NOT NULL," +
                "  Phone       VARCHAR(20)," +
                "  ActiveLoans INT DEFAULT 0," +
                "  JoinDate    DATE DEFAULT CURRENT_DATE" +
                ")"
            );
            System.out.println("[DB] Table 'Members' created.");
        }

        // Books table
        if (!tableExists(conn, "BOOKS")) {
            stmt.executeUpdate(
                "CREATE TABLE Books (" +
                "  BookID    INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY," +
                "  ISBN      VARCHAR(20) UNIQUE NOT NULL," +
                "  Title     VARCHAR(200) NOT NULL," +
                "  Author    VARCHAR(100) NOT NULL," +
                "  Genre     VARCHAR(50)," +
                "  Available BOOLEAN DEFAULT TRUE" +
                ")"
            );
            stmt.executeUpdate("CREATE INDEX idx_books_isbn ON Books(ISBN)");
            System.out.println("[DB] Table 'Books' created with index on ISBN.");
        }

        // Loans table
        if (!tableExists(conn, "LOANS")) {
            stmt.executeUpdate(
                "CREATE TABLE Loans (" +
                "  LoanID     INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY," +
                "  MemberID   INT NOT NULL REFERENCES Members(MemberID)," +
                "  BookID     INT NOT NULL REFERENCES Books(BookID)," +
                "  LoanDate   DATE DEFAULT CURRENT_DATE," +
                "  DueDate    DATE NOT NULL," +
                "  ReturnDate DATE," +
                "  Status     VARCHAR(20) DEFAULT 'ACTIVE'" +
                ")"
            );
            stmt.executeUpdate("CREATE INDEX idx_loans_member   ON Loans(MemberID)");
            stmt.executeUpdate("CREATE INDEX idx_loans_return   ON Loans(ReturnDate)");
            stmt.executeUpdate("CREATE INDEX idx_loans_status   ON Loans(Status)");
            System.out.println("[DB] Table 'Loans' created with indexes.");
        }

        stmt.close();
        seedData(conn);
    }

    /**
     * Inserts baseline seed data if tables are empty.
     */
    private static void seedData(Connection conn) throws SQLException {
        Statement check = conn.createStatement();
        ResultSet rs = check.executeQuery("SELECT COUNT(*) FROM Members");
        rs.next();
        if (rs.getInt(1) > 0) { rs.close(); check.close(); return; }
        rs.close(); check.close();

        conn.setAutoCommit(false);
        try {
            PreparedStatement pm = conn.prepareStatement(
                "INSERT INTO Members (Name, Email, Phone) VALUES (?, ?, ?)");
            String[][] members = {
                {"Alice Johnson",  "alice@email.com",  "555-0101"},
                {"Bob Smith",      "bob@email.com",    "555-0102"},
                {"Carol White",    "carol@email.com",  "555-0103"},
                {"David Brown",    "david@email.com",  "555-0104"},
                {"Eve Davis",      "eve@email.com",    "555-0105"}
            };
            for (String[] m : members) {
                pm.setString(1, m[0]); pm.setString(2, m[1]); pm.setString(3, m[2]);
                pm.addBatch();
            }
            pm.executeBatch(); pm.close();

            PreparedStatement pb = conn.prepareStatement(
                "INSERT INTO Books (ISBN, Title, Author, Genre) VALUES (?, ?, ?, ?)");
            String[][] books = {
                {"978-0-13-468599-1", "Effective Java",              "Joshua Bloch",    "Technology"},
                {"978-0-20-163361-5", "The Pragmatic Programmer",    "Andrew Hunt",     "Technology"},
                {"978-0-13-235088-4", "Clean Code",                  "Robert Martin",   "Technology"},
                {"978-0-06-112008-4", "To Kill a Mockingbird",       "Harper Lee",      "Fiction"},
                {"978-0-74-327356-5", "Harry Potter and the Sorcerer's Stone", "J.K. Rowling", "Fiction"},
                {"978-0-14-028329-7", "1984",                        "George Orwell",   "Fiction"},
                {"978-0-38-549362-0", "Cosmos",                      "Carl Sagan",      "Science"},
                {"978-0-55-321376-3", "A Brief History of Time",     "Stephen Hawking", "Science"}
            };
            for (String[] b : books) {
                pb.setString(1, b[0]); pb.setString(2, b[1]);
                pb.setString(3, b[2]); pb.setString(4, b[3]);
                pb.addBatch();
            }
            pb.executeBatch(); pb.close();

            conn.commit();
            System.out.println("[DB] Seed data inserted (5 members, 8 books).");
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * Checks whether a table already exists in the Derby schema.
     */
    public static boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"});
        boolean exists = rs.next();
        rs.close();
        return exists;
    }

    /**
     * Closes connection and shuts down the Derby engine cleanly.
     */
    public static void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}

        try {
            DriverManager.getConnection(SHUTDOWN_URL);
        } catch (SQLException e) {
            // Derby always throws XJ015 on clean shutdown — this is expected
            if ("XJ015".equals(e.getSQLState())) {
                System.out.println("[DB] Derby shut down cleanly.");
            } else {
                System.err.println("[DB] Unexpected shutdown error: " + e.getMessage());
            }
        }
    }
}
