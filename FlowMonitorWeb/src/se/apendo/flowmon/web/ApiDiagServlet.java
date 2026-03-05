package se.apendo.flowmon.web;


import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import se.apendo.flowmon.core.PollerService;

/**
 * GET /api/diag
 * Snabb diagnos: visar schema + count idag/running/failed baserat på PROCESS_INSTANCE.
 */
public class ApiDiagServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");

        PollerService poller = (PollerService) req.getServletContext().getAttribute(PollerService.CTX_KEY);
        if (poller == null) {
            resp.setStatus(500);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Poller saknas\"}");
            return;
        }
        DataSource ds = poller.getDataSource();
        if (ds == null) {
            resp.setStatus(500);
            resp.getWriter().write("{\"ok\":false,\"error\":\"DataSource saknas\"}");
            return;
        }

        Connection c = null;
        try {
            c = ds.getConnection();

            String schema = scalar(c, "VALUES CURRENT SCHEMA");
            long today = count(c,
                "SELECT COUNT(*) FROM PROCESS_INSTANCE PI " +
                "WHERE DATE(PI.STARTED + CURRENT TIMEZONE) = CURRENT DATE WITH UR");
            long running = count(c,
                "SELECT COUNT(*) FROM PROCESS_INSTANCE PI " +
                "WHERE PI.STATE=2 AND DATE(PI.STARTED + CURRENT TIMEZONE) = CURRENT DATE WITH UR");
            long failed = count(c,
                "SELECT COUNT(*) FROM PROCESS_INSTANCE PI " +
                "WHERE PI.STATE=7 AND DATE(PI.STARTED + CURRENT TIMEZONE) = CURRENT DATE WITH UR");

            resp.getWriter().write("{\"ok\":true,\"schema\":\"" + esc(schema) + "\",\"todayCount\":" + today +
                    ",\"runningCount\":" + running + ",\"failedCount\":" + failed + "}");
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"ok\":false,\"error\":\"" + esc(e.getClass().getName() + ": " + e.getMessage()) + "\"}");
        } finally {
            try { if (c != null) c.close(); } catch (Exception ignore) {}
        }
    }

    private static long count(Connection c, String sql) throws Exception {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sql);
            rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0L;
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    private static String scalar(Connection c, String sql) throws Exception {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sql);
            rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : "";
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n"," ").replace("\r"," ");
    }
}
