package com.htv.smartfarm.simulator.verify;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** Read-only inspector: list a few real accounts + recent identity outbox rows. */
public final class IdentityDbProbe {
    public static void main(String[] args) throws Exception {
        String url = System.getProperty("url", "");
        if (url.isBlank()) {
            throw new IllegalArgumentException("pass -Durl=jdbc:postgresql://<host>/<db> (no endpoint is hardcoded)");
        }
        String user = System.getProperty("user", "postgres");
        String pw = System.getProperty("pw", "");
        Class.forName("org.postgresql.Driver");
        try (Connection c = DriverManager.getConnection(url, user, pw)) {
            System.out.println("[db] connected");
            try (Statement st = c.createStatement()) {
                System.out.println("\n== accounts (email) ==");
                try (ResultSet rs = st.executeQuery(
                        "select normalized_email, id from sf_user_account order by created_at desc nulls last limit 8")) {
                    while (rs.next()) {
                        System.out.println("  " + rs.getString(1) + "  id=" + rs.getString(2));
                    }
                }
            }
            try (Statement st = c.createStatement()) {
                System.out.println("\n== sf_identity_outbox recent (status, topic, occurred_at) ==");
                try (ResultSet rs = st.executeQuery(
                        "select status, topic, occurred_at from sf_identity_outbox order by occurred_at desc nulls last limit 10")) {
                    int n = 0;
                    while (rs.next()) {
                        n++;
                        System.out.println("  [" + rs.getString(1) + "] " + rs.getString(2) + "  @" + rs.getString(3));
                    }
                    if (n == 0) System.out.println("  (empty)");
                }
            }
        }
    }
}
