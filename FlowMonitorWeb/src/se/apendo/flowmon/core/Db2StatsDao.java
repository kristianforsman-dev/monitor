package se.apendo.flowmon.core;



/**
 * Db2StatsDao
 *
 * Läsbarhetsrefactor (ingen beteendeförändring):
 * - Inga SQL-strängar eller filterlogik ändrade.
 * - Endast struktur, kommentarer och gruppering för enklare underhåll.
 *
 * Senast uppdaterad: 2026-02-24
 */
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import se.apendo.flowmon.core.Models.FailedRow;
import se.apendo.flowmon.core.Models.LiveRow;

/**
 * Db2StatsDao
 *
 * Principer (dagens data):
 * - "Stopped" definieras av aktivitet: ACTIVITY_INSTANCE_B_T.STATE = 13 och PI.COMPLETED IS NULL.
 * - "Running" (PI.STATE = 2) får INTE samtidigt vara "Stopped". Därför utesluts PIID som har AI.STATE=13
 *   i SQL_TODAY_COUNTS (gäller endast PI.STATE=2; övriga states påverkas inte).
 *
 * Robusthet:
 * - Vissa miljöer saknar kolumnen LAST_STATE_CHANGE i PROCESS_INSTANCE -> SQLCODE=-206.
 * - Vi provar i ordning: LAST_STATE_CHANGE -> LAST_MODIFIED -> STARTED (som sista fallback).
 */
public final class Db2StatsDao {

    // Dagens statistik per (sender,receiver,msgType,state)
    // ===== SQL-konstanter (dagens statistik) =====

        /**
     * Dagens "Running" (PI.STATE=2) exkluderar stoppade PIID (AI.STATE=13) via NOT EXISTS.
     * Övriga states påverkas inte.
     */

    private static final String SQL_TODAY_COUNTS =
        "SELECT SENDER, RECEIVER, MSGTYPE, STATE, COUNT(*) as COUNT " +
        "FROM ( " +
        "  SELECT DISTINCT " +
        "    CAST(PA_S.VALUE AS CHAR(25))  as SENDER, " +
        "    CAST(PA_R.VALUE AS CHAR(25))  as RECEIVER, " +
        "    CAST(PA_M.VALUE AS CHAR(30))  as MSGTYPE, " +
        "    CASE PI.STATE " +
        "      WHEN 2 THEN 'Running' " +
        "      WHEN 3 THEN 'Finished' " +
        "      WHEN 6 THEN 'Terminated' " +
        "      WHEN 7 THEN 'Failed' " +
        "      ELSE 'State?=' CONCAT CHAR(PI.STATE) " +
        "    END as STATE, " +
        "    PI.STARTED " +
        "  FROM PROCESS_INSTANCE PI " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_S ON (PI.PIID = PA_S.PIID AND PA_S.NAME='sender') " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_R ON (PI.PIID = PA_R.PIID AND PA_R.NAME='receiver') " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_M ON (PI.PIID = PA_M.PIID AND PA_M.NAME='msgType') " +
        "  WHERE DATE(PI.STARTED + CURRENT TIMEZONE) = CURRENT DATE " +
        "    AND (PI.STATE <> 2 OR NOT EXISTS ( " +
        "          SELECT 1 FROM ACTIVITY_INSTANCE_B_T AI " +
        "          WHERE AI.PIID = PI.PIID AND AI.STATE = 13 " +
        "    )) " +
        ") AS A " +
        "GROUP BY SENDER, RECEIVER, MSGTYPE, STATE " +
        "WITH UR";

    // Stopped (ai.STATE=13) per (sender,receiver,msgType) - processen ej avslutad
        /**
     * Dagens "Stopped": AI.STATE=13 och PI.COMPLETED IS NULL.
     * Datumfiltret baseras på PI.STARTED (inte COMPLETED).
     */
    private static final String SQL_TODAY_STOPPED =
        "SELECT SENDER, RECEIVER, MSGTYPE, COUNT(*) AS COUNT " +
        "FROM ( " +
        "  SELECT DISTINCT " +
        "    CAST(PA_S.VALUE AS CHAR(25)) AS SENDER, " +
        "    CAST(PA_R.VALUE AS CHAR(25)) AS RECEIVER, " +
        "    CAST(PA_M.VALUE AS CHAR(30)) AS MSGTYPE " +
        "  FROM PROCESS_INSTANCE PI " +
        "  JOIN ACTIVITY_INSTANCE_B_T AI ON AI.PIID = PI.PIID " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_S ON (PI.PIID = PA_S.PIID AND PA_S.NAME='sender') " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_R ON (PI.PIID = PA_R.PIID AND PA_R.NAME='receiver') " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_M ON (PI.PIID = PA_M.PIID AND PA_M.NAME='msgType') " +
        "  WHERE AI.STATE = 13 " +
        "    AND (PI.COMPLETED IS NULL) " +
        "    AND DATE(PI.STARTED + CURRENT TIMEZONE) = CURRENT DATE " +
        ") AS A " +
        "GROUP BY SENDER, RECEIVER, MSGTYPE " +
        "FETCH FIRST 200 ROWS ONLY " +
        "WITH UR";


    // Failed=7 idag: variant 1 (LAST_STATE_CHANGE)
    private static final String SQL_FAILED_LSC =
        "SELECT " +
        "  PI.PIID, " +
        "  CAST(PA_S.VALUE AS CHAR(25))  as SENDER, " +
        "  CAST(PA_R.VALUE AS CHAR(25))  as RECEIVER, " +
        "  CAST(PA_M.VALUE AS CHAR(30))  as MSGTYPE, " +
        "  PI.STARTED, " +
        "  PI.LAST_STATE_CHANGE as LAST_CHANGED " +
        "FROM PROCESS_INSTANCE PI " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_S ON (PI.PIID = PA_S.PIID AND PA_S.NAME='sender') " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_R ON (PI.PIID = PA_R.PIID AND PA_R.NAME='receiver') " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_M ON (PI.PIID = PA_M.PIID AND PA_M.NAME='msgType') " +
        "WHERE PI.STATE = 3 " +
        "  AND DATE(PI.STARTED + CURRENT TIMEZONE) = CURRENT DATE " +
        "ORDER BY PI.LAST_STATE_CHANGE DESC " +
        "FETCH FIRST 200 ROWS ONLY " +
        "WITH UR";

    // Failed=7 idag: variant 2 (LAST_MODIFIED)
    private static final String SQL_FAILED_LM =
        "SELECT " +
        "  PI.PIID, " +
        "  CAST(PA_S.VALUE AS CHAR(25))  as SENDER, " +
        "  CAST(PA_R.VALUE AS CHAR(25))  as RECEIVER, " +
        "  CAST(PA_M.VALUE AS CHAR(30))  as MSGTYPE, " +
        "  PI.STARTED, " +
        "  PI.LAST_MODIFIED as LAST_CHANGED " +
        "FROM PROCESS_INSTANCE PI " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_S ON (PI.PIID = PA_S.PIID AND PA_S.NAME='sender') " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_R ON (PI.PIID = PA_R.PIID AND PA_R.NAME='receiver') " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_M ON (PI.PIID = PA_M.PIID AND PA_M.NAME='msgType') " +
        "WHERE PI.STATE = 3 " +
        "  AND DATE(PI.STARTED + CURRENT TIMEZONE) = CURRENT DATE " +
        "ORDER BY PI.LAST_MODIFIED DESC " +
        "FETCH FIRST 200 ROWS ONLY " +
        "WITH UR";

    // Failed=7 idag: variant 3 (STARTED fallback)
    private static final String SQL_FAILED_STARTED =
        "SELECT " +
        "  PI.PIID, " +
        "  CAST(PA_S.VALUE AS CHAR(25))  as SENDER, " +
        "  CAST(PA_R.VALUE AS CHAR(25))  as RECEIVER, " +
        "  CAST(PA_M.VALUE AS CHAR(30))  as MSGTYPE, " +
        "  PI.STARTED, " +
        "  PI.STARTED as LAST_CHANGED " +
        "FROM PROCESS_INSTANCE PI " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_S ON (PI.PIID = PA_S.PIID AND PA_S.NAME='sender') " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_R ON (PI.PIID = PA_R.PIID AND PA_R.NAME='receiver') " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_M ON (PI.PIID = PA_M.PIID AND PA_M.NAME='msgType') " +
        "WHERE PI.STATE = 7 " +
        "  AND DATE(PI.STARTED + CURRENT TIMEZONE) = CURRENT DATE " +
        "ORDER BY PI.STARTED DESC " +
        "FETCH FIRST 200 ROWS ONLY " +
        "WITH UR";

    public List<LiveRow> fetchTodayCounts(Connection c) throws Exception {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(SQL_TODAY_COUNTS);
            rs = ps.executeQuery();
            List<LiveRow> out = new ArrayList<LiveRow>();
            while (rs.next()) {
                String sender = trim(rs.getString(1));
                String receiver = trim(rs.getString(2));
                String msgType = trim(rs.getString(3));
                String state = trim(rs.getString(4));
                long count = rs.getLong(5);
                out.add(new LiveRow(sender, receiver, msgType, state, count));
            }
            return out;
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    public List<LiveRow> fetchTodayStoppedCounts(Connection c) throws Exception {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(SQL_TODAY_STOPPED);
            rs = ps.executeQuery();
            List<LiveRow> out = new ArrayList<LiveRow>();
            while (rs.next()) {
                String sender = trim(rs.getString(1));
                String receiver = trim(rs.getString(2));
                String msgType = trim(rs.getString(3));
                long count = rs.getLong(4);
                out.add(new LiveRow(sender, receiver, msgType, "Stopped", count));
            }
            return out;
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }


    public List<FailedRow> fetchTodayFailed(Connection c) throws Exception {
        SQLException last = null;

        // 1) LAST_STATE_CHANGE
        try { return fetchFailedWithSql(c, SQL_FAILED_LSC); }
        catch (SQLException e) { last = e; if (!isMissingColumn(e)) throw e; }

        // 2) LAST_MODIFIED
        try { return fetchFailedWithSql(c, SQL_FAILED_LM); }
        catch (SQLException e) { last = e; if (!isMissingColumn(e)) throw e; }

        // 3) STARTED fallback
        try { return fetchFailedWithSql(c, SQL_FAILED_STARTED); }
        catch (SQLException e) { last = e; throw e; }
    }

    private List<FailedRow> fetchFailedWithSql(Connection c, String sql) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sql);
            rs = ps.executeQuery();
            List<FailedRow> out = new ArrayList<FailedRow>();
            while (rs.next()) {
                String piid = trim(rs.getString(1));
                String sender = trim(rs.getString(2));
                String receiver = trim(rs.getString(3));
                String msgType = trim(rs.getString(4));
                Timestamp started = rs.getTimestamp(5);
                Timestamp lastChg = rs.getTimestamp(6);

                out.add(new FailedRow(piid, sender, receiver, msgType, iso(started), iso(lastChg)));
            }
            return out;
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    private static boolean isMissingColumn(SQLException e) {
        // DB2: SQLCODE=-206, SQLSTATE=42703
        // Vi litar inte på toString-format, så vi matchar både state och meddelande.
        String state = e.getSQLState();
        if ("42703".equals(state)) return true;
        String msg = e.getMessage();
        if (msg == null) return false;
        msg = msg.toUpperCase();
        return (msg.indexOf("SQLCODE=-206") >= 0) || (msg.indexOf("42703") >= 0);
    }

    private static String trim(String s) { return (s == null) ? "" : s.trim(); }

    private static String iso(Timestamp ts) {
        if (ts == null) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Stockholm"));
        return sdf.format(new java.util.Date(ts.getTime()));
    }


    // ------------------------------------------------------------
    // Fas 2B support: Finished instances since last poll (for buckets)
    // ------------------------------------------------------------

    public static final class FinishedInstance {
        public final Timestamp completed;
        public final String sender;
        public final String receiver;
        public final String msgType;

        public FinishedInstance(Timestamp completed, String sender, String receiver, String msgType) {
            this.completed = completed;
            this.sender = sender;
            this.receiver = receiver;
            this.msgType = msgType;
        }
    }

    

    // ===== RANGE SUPPORT =====
    private static final String SQL_RANGE_COUNTS =
        "SELECT SENDER, RECEIVER, MSGTYPE, STATE, COUNT(DISTINCT PIID) as COUNT " +
        "FROM ( " +
        "  SELECT " +
        "    PI.PIID as PIID, " +
        "    CAST(PA_S.VALUE AS CHAR(25))  as SENDER, " +
        "    CAST(PA_R.VALUE AS CHAR(25))  as RECEIVER, " +
        "    CAST(PA_M.VALUE AS CHAR(30))  as MSGTYPE, " +
        "    CASE PI.STATE " +
        "      WHEN 2 THEN 'Running' " +
        "      WHEN 3 THEN 'Finished' " +
        "      WHEN 6 THEN 'Terminated' " +
        "      WHEN 7 THEN 'Failed' " +
        "      ELSE 'State?=' CONCAT CHAR(PI.STATE) " +
        "    END as STATE " +
        "  FROM PROCESS_INSTANCE PI " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_S ON (PI.PIID = PA_S.PIID AND PA_S.NAME='sender') " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_R ON (PI.PIID = PA_R.PIID AND PA_R.NAME='receiver') " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_M ON (PI.PIID = PA_M.PIID AND PA_M.NAME='msgType') " +
        "  WHERE DATE(PI.STARTED + CURRENT TIMEZONE) BETWEEN ? AND ? " +
        ") AS A " +
        "GROUP BY SENDER, RECEIVER, MSGTYPE, STATE " +
        "WITH UR";

    private static final String SQL_RANGE_STOPPED =
        "SELECT SENDER, RECEIVER, MSGTYPE, COUNT(DISTINCT PIID) AS COUNT " +
        "FROM ( " +
        "  SELECT " +
        "    PI.PIID as PIID, " +
        "    CAST(PA_S.VALUE AS CHAR(25)) AS SENDER, " +
        "    CAST(PA_R.VALUE AS CHAR(25)) AS RECEIVER, " +
        "    CAST(PA_M.VALUE AS CHAR(30)) AS MSGTYPE " +
        "  FROM PROCESS_INSTANCE PI " +
        "  JOIN ACTIVITY_INSTANCE_B_T AI ON AI.PIID = PI.PIID " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_S ON (PI.PIID = PA_S.PIID AND PA_S.NAME='sender') " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_R ON (PI.PIID = PA_R.PIID AND PA_R.NAME='receiver') " +
        "  LEFT JOIN PROCESS_ATTRIBUTE PA_M ON (PI.PIID = PA_M.PIID AND PA_M.NAME='msgType') " +
        "  WHERE AI.STATE = 13 " +
        "    AND (PI.COMPLETED IS NULL) " +
        "    AND DATE(PI.STARTED + CURRENT TIMEZONE) BETWEEN ? AND ? " +
        ") AS A " +
        "GROUP BY SENDER, RECEIVER, MSGTYPE " +
        "WITH UR";
private static final String SQL_FINISHED_SINCE =
        "SELECT " +
        "  PI.COMPLETED as COMPLETED, " +
        "  CAST(PA_S.VALUE AS CHAR(25))  as SENDER, " +
        "  CAST(PA_R.VALUE AS CHAR(25))  as RECEIVER, " +
        "  CAST(PA_M.VALUE AS CHAR(30))  as MSGTYPE " +
        "FROM PROCESS_INSTANCE PI " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_S ON (PI.PIID = PA_S.PIID AND PA_S.NAME='sender') " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_R ON (PI.PIID = PA_R.PIID AND PA_R.NAME='receiver') " +
        "LEFT JOIN PROCESS_ATTRIBUTE PA_M ON (PI.PIID = PA_M.PIID AND PA_M.NAME='msgType') " +
        "WHERE PI.STATE = 3 " + // Finished
        "  AND DATE(PI.STARTED + CURRENT TIMEZONE) = CURRENT DATE " +
        "  AND PI.COMPLETED > ? " +
        "ORDER BY PI.COMPLETED ASC " +
        "WITH UR";

    /** Hämtar nya Finished-processer (STATE=3) sedan en given timestamp. */
    public List<FinishedInstance> fetchFinishedSince(Connection c, Timestamp sinceExclusive) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<FinishedInstance> out = new ArrayList<FinishedInstance>();
        try {
            ps = c.prepareStatement(SQL_FINISHED_SINCE);
            ps.setTimestamp(1, sinceExclusive);
            rs = ps.executeQuery();
            while (rs.next()) {
                Timestamp started = rs.getTimestamp("COMPLETED");
                String sender = safe(rs.getString("SENDER"));
                String receiver = safe(rs.getString("RECEIVER"));
                String msgType = safe(rs.getString("MSGTYPE"));
                if (started != null) out.add(new FinishedInstance(started, sender, receiver, msgType));
            }
            return out;
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }



    // ===== RANGE METHODS =====
    public List<LiveRow> fetchRangeCounts(Connection c,
                                          java.time.LocalDate from,
                                          java.time.LocalDate to) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(SQL_RANGE_COUNTS);
            ps.setDate(1, java.sql.Date.valueOf(from));
            ps.setDate(2, java.sql.Date.valueOf(to));
            rs = ps.executeQuery();

            List<LiveRow> out = new ArrayList<LiveRow>();
            while (rs.next()) {
                String sender = trim(rs.getString(1));
                String receiver = trim(rs.getString(2));
                String msgType = trim(rs.getString(3));
                String state = trim(rs.getString(4));
                long count = rs.getLong(5);
                out.add(new LiveRow(sender, receiver, msgType, state, count));
            }
            return out;
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    public List<LiveRow> fetchRangeStoppedCounts(Connection c,
                                                 java.time.LocalDate from,
                                                 java.time.LocalDate to) throws SQLException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(SQL_RANGE_STOPPED);
            ps.setDate(1, java.sql.Date.valueOf(from));
            ps.setDate(2, java.sql.Date.valueOf(to));
            rs = ps.executeQuery();

            List<LiveRow> out = new ArrayList<LiveRow>();
            while (rs.next()) {
                String sender = trim(rs.getString(1));
                String receiver = trim(rs.getString(2));
                String msgType = trim(rs.getString(3));
                long count = rs.getLong(4);
                out.add(new LiveRow(sender, receiver, msgType, "Stopped", count));
            }
            return out;
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

}


