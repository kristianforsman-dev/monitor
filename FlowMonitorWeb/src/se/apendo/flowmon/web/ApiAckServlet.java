package se.apendo.flowmon.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import se.apendo.flowmon.core.AckDao;
import se.apendo.flowmon.core.PollerService;

public class ApiAckServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final AckDao dao = new AckDao();

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

        String incidentId = nz(req.getParameter("incidentId"));
        String set = nz(req.getParameter("set"));
        String comment = nz(req.getParameter("comment"));

        int ttlMin = parseInt(req.getParameter("ttl"), -1);
        Timestamp expiresAt = (ttlMin > 0)
            ? new Timestamp(System.currentTimeMillis() + (long)ttlMin * 60L * 1000L)
            : midnightStockholmPlus(1);

        String user = req.getRemoteUser();
        if (user == null || user.trim().length() == 0) user = "unknown";

        Connection c = null;
        try {
            c = poller.getDataSource().getConnection();

            if ("0".equals(set)) dao.clearAck(c, incidentId);
            else dao.setAck(c, incidentId, user, expiresAt, comment);

            poller.bumpVersion();
            resp.getWriter().write("{\"ok\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"ok\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
        } finally {
            try { if (c != null) c.close(); } catch (Exception ignore) {}
        }
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(nz(s)); } catch (Exception e) { return def; }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Timestamp midnightStockholmPlus(int days) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Stockholm"));
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.add(Calendar.DAY_OF_MONTH, days);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Timestamp(cal.getTimeInMillis());
    }
}
