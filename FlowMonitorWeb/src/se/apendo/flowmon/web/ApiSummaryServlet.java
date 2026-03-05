package se.apendo.flowmon.web;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import se.apendo.flowmon.core.AckDao;
import se.apendo.flowmon.core.Models.AckInfo;
import se.apendo.flowmon.core.Models.CheckRow;
import se.apendo.flowmon.core.Models.FailedRow;
import se.apendo.flowmon.core.Models.LiveRow;
import se.apendo.flowmon.core.Models.Snapshot;
import se.apendo.flowmon.core.PollerService;

/**
 * GET /api/summary
 * Returnerar snapshot + ack-status för incidenter.
 */
public class ApiSummaryServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");

        ServletContext ctx = req.getServletContext();
        PollerService poller = (PollerService) ctx.getAttribute(PollerService.CTX_KEY);
        SafeJsonWriter jw = new SafeJsonWriter(resp.getWriter());

        if (poller == null) {
            resp.setStatus(500);
            jw.beginObject()
              .name("system").beginObject().name("ok").value(false).name("error").value("Poller saknas").endObject()
              .name("version").value(0).name("updatedAt").value("")
              .name("flowCount").value(0)
              .name("liveRunning").beginArray().endArray()
              .name("failed").beginArray().endArray()
              .name("checks").beginObject()
                .name("interval").beginArray().endArray()
                .name("exactTimes").beginArray().endArray()
                .name("weekdays").beginArray().endArray()
                .name("monthdays").beginArray().endArray()
                .name("dates").beginArray().endArray()
              .endObject()
            .endObject();
            jw.flush();
            return;
        }

        Snapshot s = poller.getLatest();
        String lastError = poller.getLastError();

        Map<String, AckInfo> ackMap = new HashMap<String, AckInfo>();
        try {
            List<String> ids = new ArrayList<String>();
            collect(ids, s.interval);
            collect(ids, s.exactTimes);
            collect(ids, s.weekdays);
            collect(ids, s.monthdays);
            collect(ids, s.dates);

            if (!ids.isEmpty()) {
                DataSource ds = poller.getDataSource();
                if (ds != null) {
                    Connection c = null;
                    try {
                        c = ds.getConnection();
                        ackMap = new AckDao().loadActiveAcks(c, ids);
                    } finally {
                        try { if (c != null) c.close(); } catch (Exception ignore) {}
                    }
                }
            }
        } catch (Exception ignore) { ackMap = new HashMap<String, AckInfo>(); }

        jw.beginObject()
          .name("system").beginObject()
            .name("ok").value(lastError == null || lastError.length() == 0)
            .name("error").value(lastError == null ? "" : lastError)
          .endObject()
          .name("version").value((long) s.version)
          .name("updatedAt").value(s.updatedAtIso)
          .name("flowCount").value((long) s.flowCount);

        // Live running
        jw.name("liveRunning").beginArray();
        writeLive(jw, s.liveRunning);
        jw.endArray();

        // Failed (STATE=7)
        jw.name("failed").beginArray();
        writeFailed(jw, s.failed);
        jw.endArray();

        // Checks + ack
        jw.name("checks").beginObject();
        writeChecks(jw, "interval", s.interval, ackMap);
        writeChecks(jw, "exactTimes", s.exactTimes, ackMap);
        writeChecks(jw, "weekdays", s.weekdays, ackMap);
        writeChecks(jw, "monthdays", s.monthdays, ackMap);
        writeChecks(jw, "dates", s.dates, ackMap);
        jw.endObject();

        jw.endObject();
        jw.flush();
    }

    private static void writeLive(SafeJsonWriter jw, List<LiveRow> rows) throws IOException {
        if (rows == null) return;
        for (int i = 0; i < rows.size(); i++) {
            LiveRow r = rows.get(i);
            jw.beginObject()
              .name("sender").value(r.sender)
              .name("receiver").value(r.receiver)
              .name("msgType").value(r.msgType)
              .name("state").value(r.state)
              .name("count").value((long) r.count)
            .endObject();
        }
    }

    private static void writeFailed(SafeJsonWriter jw, List<FailedRow> rows) throws IOException {
        if (rows == null) return;
        for (int i = 0; i < rows.size(); i++) {
            FailedRow r = rows.get(i);
            jw.beginObject()
              .name("piid").value(r.piid)
              .name("sender").value(r.sender)
              .name("receiver").value(r.receiver)
              .name("msgType").value(r.msgType)
              .name("started").value(r.startedIso)
              .name("lastChanged").value(r.lastChangedIso)
            .endObject();
        }
    }

    private static void writeChecks(SafeJsonWriter jw, String name, List<CheckRow> rows, Map<String, AckInfo> ackMap) throws IOException {
        jw.name(name).beginArray();
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                CheckRow r = rows.get(i);
                AckInfo a = (ackMap == null) ? null : ackMap.get(r.incidentId);

                jw.beginObject()
                  .name("flowId").value(r.flowId)
                  .name("status").value(r.status.name())
                  .name("detail").value(r.detail)
                  .name("actual").value((long) r.actual)
                  .name("expected").value((long) r.expected)
                  .name("incidentId").value(r.incidentId == null ? "" : r.incidentId)
                  .name("problemType").value(r.problemType == null ? "" : r.problemType)
                  .name("periodKey").value(r.periodKey == null ? "" : r.periodKey)
                  .name("acked").value(a != null)
                  .name("ackedBy").value(a != null ? a.ackedBy : "")
                  .name("ackedAt").value(a != null ? a.ackedAtIso : "")
                  .name("ackExpiresAt").value(a != null ? a.expiresAtIso : "")
                .endObject();
            }
        }
        jw.endArray();
    }

    private static void collect(List<String> out, List<CheckRow> rows) {
        if (out == null || rows == null) return;
        for (int i = 0; i < rows.size(); i++) {
            CheckRow r = rows.get(i);
            if (r == null) continue;
            String id = r.incidentId;
            if (id != null && id.trim().length() > 0) out.add(id.trim());
        }
    }
}