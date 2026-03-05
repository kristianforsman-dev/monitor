package se.apendo.flowmon.web;


import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import se.apendo.flowmon.core.PollerService;

/**
 * Long-poll endpoint:
 *   GET /api/longpoll?sinceVersion=123
 *
 * Svar:
 *   { "changed": true/false, "version": <latestVersion>, "timeout": true/false }
 *
 * OBS: Inga externa bibliotek. Java 7-kompatibel.
 */
public class ApiLongPollServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // Hur länge vi väntar max innan vi svarar ändå (ms)
    private static final long MAX_WAIT_MS = 25000L;
    // Poll-intervall under väntan (ms)
    private static final long SLEEP_MS = 500L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");

        // Anti-cache så browsern inte fastnar
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        PollerService poller = (PollerService) req.getServletContext().getAttribute(PollerService.CTX_KEY);
        if (poller == null) {
            resp.setStatus(500);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Poller saknas\"}");
            return;
        }

        long since = parseLong(req.getParameter("sinceVersion"), 0L);

        long start = System.currentTimeMillis();
        boolean changed = poller.hasChangedSince(since);

        while (!changed && (System.currentTimeMillis() - start) < MAX_WAIT_MS) {
            try { Thread.sleep(SLEEP_MS); } catch (InterruptedException ie) { break; }
            changed = poller.hasChangedSince(since);
        }

        long latestVersion = 0L;
        try {
            if (poller.getLatest() != null) latestVersion = poller.getLatest().version;
        } catch (Exception ignore) {}

        boolean timeout = !changed;

        resp.getWriter().write(
            "{\"changed\":" + (changed ? "true" : "false") +
            ",\"version\":" + latestVersion +
            ",\"timeout\":" + (timeout ? "true" : "false") +
            "}"
        );
    }

    private static long parseLong(String s, long def) {
        try {
            if (s == null) return def;
            s = s.trim();
            if (s.length() == 0) return def;
            return Long.parseLong(s);
        } catch (Exception e) {
            return def;
        }
    }
}

