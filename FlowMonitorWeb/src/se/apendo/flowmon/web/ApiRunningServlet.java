package se.apendo.flowmon.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletContext;

import se.apendo.flowmon.core.Models.LiveRow;
import se.apendo.flowmon.core.Models.Snapshot;
import se.apendo.flowmon.core.PollerService;

import java.util.HashMap;
import java.util.Map;

/**
 * GET /api/running
 *
 * Data source: PollerService snapshot (no DB calls here).
 *
 * Debug:
 *   /api/running?debug=1  -> adds a "debug" object with counts and snapshot metadata.
 */
public class ApiRunningServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("application/json");
    resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    resp.setHeader("Pragma", "no-cache");

    final boolean debug = "1".equals(req.getParameter("debug")) || "true".equalsIgnoreCase(req.getParameter("debug"));

    SafeJsonWriter jw = null;
    try {
      jw = new SafeJsonWriter(resp.getWriter());

      // Live endpoints are today-only. Accept no range or exactly today..today.
      String fromS = req.getParameter("from");
      String toS   = req.getParameter("to");
      try {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Stockholm"));
        if ((fromS != null && !fromS.trim().isEmpty()) || (toS != null && !toS.trim().isEmpty())) {
          LocalDate from = (fromS == null || fromS.trim().isEmpty()) ? today : LocalDate.parse(fromS.trim());
          LocalDate to   = (toS == null || toS.trim().isEmpty()) ? today : LocalDate.parse(toS.trim());
          if (!today.equals(from) || !today.equals(to)) {
            resp.setStatus(400);
            jw.beginObject().name("error").value("Endpointen stödjer endast dagens datum (from=today&to=today).").endObject();
            jw.flush();
            return;
          }
        }
      } catch (DateTimeParseException dtpe) {
        resp.setStatus(400);
        jw.beginObject().name("error").value("Ogiltigt datumformat. Använd YYYY-MM-DD.").endObject();
        jw.flush();
        return;
      }

      ServletContext ctx = req.getServletContext();
      PollerService poller = (PollerService) ctx.getAttribute(PollerService.CTX_KEY);
      if (poller == null) {
        resp.setStatus(500);
        jw.beginObject().name("error").value("Poller saknas").endObject();
        jw.flush();
        return;
      }

      Snapshot snap = poller.getLatest();

      long totalIn = 0;
      long totalReturned = 0;
      Map<String, Long> stateCounts = debug ? new HashMap<String, Long>() : null;

      jw.beginObject();

      if (debug) {
        jw.name("debug").beginObject();
        if (snap == null) {
          jw.name("hasSnapshot").value(false);
        } else {
          jw.name("hasSnapshot").value(true);
          jw.name("version").value(snap.version);
          jw.name("updatedAtIso").value(snap.updatedAtIso == null ? "" : snap.updatedAtIso);
          jw.name("flowCount").value(snap.flowCount);
        }
        jw.endObject();
      }

      jw.name("rows").beginArray();

      if (snap != null && snap.liveRunning != null) {
        totalIn = snap.liveRunning.size();
        for (LiveRow r : snap.liveRunning) {
          if (r == null) continue;

          String st = r.state == null ? "" : String.valueOf(r.state).trim();
          if (debug) {
            String k = st.toLowerCase();
            Long c = stateCounts.get(k);
            stateCounts.put(k, c == null ? 1L : (c + 1L));
          }

          // Endpoint returns only "Running" rows
          if (!"running".equalsIgnoreCase(st)) continue;

          totalReturned++;

          jw.beginObject()
            .name("sender").value(r.sender == null ? "" : r.sender)
            .name("receiver").value(r.receiver == null ? "" : r.receiver)
            .name("msgType").value(r.msgType == null ? "" : r.msgType)
            .name("state").value(st)
            .name("count").value(r.count)
          .endObject();
        }
      }

      jw.endArray();

      if (debug) {
        // Append debug counts AFTER rows so existing clients that only read rows remain unaffected.
        jw.name("debug").beginObject();
        jw.name("totalLiveRunning").value(totalIn);
        jw.name("totalReturned").value(totalReturned);
        jw.name("states").beginObject();
        if (stateCounts != null) {
          for (Map.Entry<String, Long> e : stateCounts.entrySet()) {
            jw.name(e.getKey()).value(e.getValue());
          }
        }
        jw.endObject();
        jw.endObject();
      }

      jw.endObject();
      jw.flush();

    } catch (Exception e) {
      try {
        resp.setStatus(500);
        if (jw != null) {
          jw.beginObject().name("error").value("running failed: " + e.getMessage()).endObject();
          jw.flush();
        }
      } catch (Exception ignore) {}
    }
  }
}




