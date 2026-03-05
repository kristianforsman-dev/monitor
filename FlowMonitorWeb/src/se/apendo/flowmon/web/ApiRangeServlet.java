

package se.apendo.flowmon.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import se.apendo.flowmon.core.Models.LiveRow;
import se.apendo.flowmon.core.PollerService;

/**
 * GET /api/range?from=YYYY-MM-DD&to=YYYY-MM-DD
 * Returnerar aggregerade rader (sender/receiver/msgType/state/count) för valt datumintervall.
 *
 * Not: Ingen ny dependency. Format hålls kompatibelt med GUI: { rows: [ ... ] }
 */
public class ApiRangeServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final int MAX_RANGE_DAYS = 31;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("application/json");
    resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    resp.setHeader("Pragma", "no-cache");

    SafeJsonWriter jw = null;
    try {
      jw = new SafeJsonWriter(resp.getWriter());

      String fromS = req.getParameter("from");
      String toS   = req.getParameter("to");

      LocalDate from = null, to = null;
      try {
        if (fromS != null && !fromS.trim().isEmpty()) from = LocalDate.parse(fromS.trim());
        if (toS != null && !toS.trim().isEmpty())   to   = LocalDate.parse(toS.trim());
      } catch (DateTimeParseException dtpe) {
        resp.setStatus(400);
        jw.beginObject().name("error").value("Ogiltigt datumformat. Använd YYYY-MM-DD.").endObject();
        jw.flush();
        return;
      }

      if (from == null || to == null) {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Stockholm"));
        from = today; to = today;
      }
      if (from.isAfter(to)) { LocalDate tmp = from; from = to; to = tmp; }

    long spanDays = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1L;
    if (spanDays > MAX_RANGE_DAYS) {
      resp.setStatus(400);
      jw.beginObject()
        .name("error")
        .value("För stort intervall (" + spanDays + " dagar). Max " + MAX_RANGE_DAYS + ".")
      .endObject();
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

      List<LiveRow> rows = poller.queryRange(from, to);

      jw.beginObject();
      jw.name("rows").beginArray();
      for (LiveRow r : rows) {
        if (r == null) continue;
        jw.beginObject()
          .name("sender").value(r.sender == null ? "" : r.sender)
          .name("receiver").value(r.receiver == null ? "" : r.receiver)
          .name("msgType").value(r.msgType == null ? "" : r.msgType)
          .name("state").value(r.state == null ? "" : String.valueOf(r.state))
          .name("count").value(r.count)
        .endObject();
      }
      jw.endArray();
      jw.endObject();
      jw.flush();

    } catch (Exception e) {
      try {
        resp.setStatus(500);
        if (jw != null) {
          jw.beginObject().name("error").value("range failed: " + e.getMessage()).endObject();
          jw.flush();
        }
      } catch (Exception ignore) {}
    }
  }
}
