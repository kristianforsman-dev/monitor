package se.apendo.flowmon.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import se.apendo.flowmon.core.Models.AckInfo;

public final class AckDao {

    private static final String TBL = "FLOWMON_ACK";

    public Map<String, AckInfo> loadActiveAcks(Connection c, List<String> incidentIds) throws Exception {
        Map<String, AckInfo> out = new HashMap<String, AckInfo>();
        if (incidentIds == null || incidentIds.isEmpty()) return out;

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT INCIDENT_ID, ACKED_BY, ACKED_AT, EXPIRES_AT ");
        sb.append("FROM ").append(TBL).append(" ");
        sb.append("WHERE INCIDENT_ID IN (");
        for (int i = 0; i < incidentIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        sb.append(") AND (EXPIRES_AT IS NULL OR EXPIRES_AT > CURRENT TIMESTAMP) WITH UR");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sb.toString());
            for (int i = 0; i < incidentIds.size(); i++) ps.setString(i + 1, incidentIds.get(i));
            rs = ps.executeQuery();
            while (rs.next()) {
                String id = rs.getString(1);
                String by = rs.getString(2);
                Timestamp at = rs.getTimestamp(3);
                Timestamp until = rs.getTimestamp(4);
                out.put(id, new AckInfo(id, nz(by), iso(at), iso(until)));
            }
            return out;
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    public void setAck(Connection c, String incidentId, String ackedBy, Timestamp expiresAt, String comment) throws Exception {
        if (incidentId == null || incidentId.length() == 0) return;

        Timestamp now = new Timestamp(System.currentTimeMillis());

        PreparedStatement up = null;
        try {
            up = c.prepareStatement("UPDATE " + TBL + " SET ACKED_BY=?, ACKED_AT=?, EXPIRES_AT=?, COMMENT_TXT=? WHERE INCIDENT_ID=?");
            up.setString(1, safe128(ackedBy));
            up.setTimestamp(2, now);
            if (expiresAt != null) up.setTimestamp(3, expiresAt); else up.setNull(3, java.sql.Types.TIMESTAMP);
            up.setString(4, safe512(comment));
            up.setString(5, incidentId);
            int n = up.executeUpdate();
            if (n > 0) return;
        } finally {
            try { if (up != null) up.close(); } catch (Exception ignore) {}
        }

        PreparedStatement ins = null;
        try {
            ins = c.prepareStatement("INSERT INTO " + TBL + " (INCIDENT_ID, ACKED_BY, ACKED_AT, EXPIRES_AT, COMMENT_TXT) VALUES (?,?,?,?,?)");
            ins.setString(1, incidentId);
            ins.setString(2, safe128(ackedBy));
            ins.setTimestamp(3, now);
            if (expiresAt != null) ins.setTimestamp(4, expiresAt); else ins.setNull(4, java.sql.Types.TIMESTAMP);
            ins.setString(5, safe512(comment));
            ins.executeUpdate();
        } finally {
            try { if (ins != null) ins.close(); } catch (Exception ignore) {}
        }
    }

    public void clearAck(Connection c, String incidentId) throws Exception {
        if (incidentId == null || incidentId.length() == 0) return;
        PreparedStatement ps = null;
        try {
            ps = c.prepareStatement("DELETE FROM " + TBL + " WHERE INCIDENT_ID=?");
            ps.setString(1, incidentId);
            ps.executeUpdate();
        } finally {
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    private static String iso(Timestamp ts) {
        if (ts == null) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Stockholm"));
        return sdf.format(new java.util.Date(ts.getTime()));
    }

    private static String safe128(String s) {
        if (s == null) return "unknown";
        s = s.trim();
        if (s.length() == 0) return "unknown";
        if (s.length() > 128) s = s.substring(0, 128);
        return s;
    }

    private static String safe512(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() > 512) s = s.substring(0, 512);
        return s;
    }
}
