package se.apendo.flowmon.web;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletContext;

import se.apendo.flowmon.core.Models.LiveRow;
import se.apendo.flowmon.core.Models.Snapshot;
import se.apendo.flowmon.core.PollerService;

/**
 * GET /api/finished – returnerar alla LiveRow med state="Finished" för innevarande snapshot.
 */
public class ApiFinishedServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("application/json");
    resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    resp.setHeader("Pragma", "no-cache");

    SafeJsonWriter jw = null;
    try {
      jw = new SafeJsonWriter(resp.getWriter());

      ServletContext ctx = req.getServletContext();
      PollerService poller = (PollerService) ctx.getAttribute(PollerService.CTX_KEY);
      if (poller == null) {
        resp.setStatus(500);
        jw.beginObject().name("error").value("Poller saknas").endObject();
        jw.flush();
        return;
      }

      Snapshot snap = poller.getLatest();
      jw.beginObject();
      jw.name("rows").beginArray();

      if (snap != null && snap.liveRunning != null) {
        for (LiveRow r : snap.liveRunning) {
          if (r == null) continue;
          String st = (r.state == null) ? "" : String.valueOf(r.state).trim();
          if (!"Finished".equalsIgnoreCase(st)) continue;

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
      jw.endObject();
      jw.flush();

    } catch (Exception e) {
      try {
        resp.setStatus(500);
        if (jw != null) {
          jw.beginObject().name("error").value("finished failed: " + e.getMessage()).endObject();
          jw.flush();
        }
      } catch (Exception ignore) {}
    }
  }
}
