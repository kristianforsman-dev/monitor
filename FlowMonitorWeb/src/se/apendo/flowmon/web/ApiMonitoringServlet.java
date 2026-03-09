
package se.apendo.flowmon.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import se.apendo.flowmon.core.PollerService;

/**
 * /api/monitoring
 *
 * Supports modes:
 * - intervals: large volumes across a time span (progress by buckets)
 * - exactTimes: exact expected count by specific time(s) each day
 *
 * Output contract (kept stable for GUI):
 * - status: INFO|WARNING|ERROR (used for sort/color)
 * - mode: shown as the card "message" (clean)
 * - message: short reason (only meaningful for WARNING/ERROR)
 * - details: compact context (only meaningful for WARNING/ERROR)
 */
public class ApiMonitoringServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private static final ZoneId ZONE = ZoneId.of("Europe/Stockholm");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("application/json");

    SafeJsonWriter jw = null;
    try {
      jw = new SafeJsonWriter(resp.getWriter());

      // Frontend may send from/to, but monitoring status must not depend on GUI-selected range.
      // Only validate input format so the endpoint contract stays predictable.
      String fromS = req.getParameter("from");
      String toS   = req.getParameter("to");
      try {
        if (fromS != null && !fromS.trim().isEmpty()) LocalDate.parse(fromS.trim());
        if (toS != null && !toS.trim().isEmpty()) LocalDate.parse(toS.trim());
      } catch (DateTimeParseException dtpe) {
        resp.setStatus(400);
        jw.beginObject().name("error").value("Ogiltigt datumformat. Använd YYYY-MM-DD.").endObject();
        jw.flush();
        return;
      }

      ServletContext ctx = req.getServletContext();
      PollerService poller = (PollerService) ctx.getAttribute(PollerService.CTX_KEY);

      File cfgFile = new File(ctx.getRealPath("/assets/monitoring-config.json"));
      Config cfg = readConfig(cfgFile);

      ZonedDateTime now = ZonedDateTime.now(ZONE);

      List<Row> rows = new ArrayList<Row>();
      if (cfg != null && cfg.flows != null) {
        for (Flow f : cfg.flows) {
          if (f == null || !f.enabled) continue;
          String flowId = t(f.flowId);
          if (flowId.isEmpty()) continue;

          String name = t(f.name);
          if (name.isEmpty()) name = flowId.replace("||", "-");

          String mode = t(f.mode);
          if (mode.isEmpty()) mode = "intervals";

          EvalResult er;
          if ("exactTimes".equalsIgnoreCase(mode)) {
            er = evalExactTimes(now, poller, flowId, f, cfg.defaults);
          } else {
            // default: intervals
            er = evalIntervals(now, poller, flowId, f, cfg.defaults);
          }

          long finishedToday = (poller == null) ? 0L : poller.getFinishedToday(flowId);

          // Append "Senast" to details only when we are warning/error (requested by user)
          if (er != null && er.rank > 1 && poller != null) {
            java.sql.Timestamp last = poller.getLastFinishedCompleted(flowId);
            if (last != null) {
              java.time.LocalTime lt = last.toLocalDateTime().toLocalTime();
              String ts = lt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
              if (er.details != null && !er.details.isEmpty()) er.details = er.details + ", ";
              er.details = (er.details == null ? "" : er.details) + "Senast " + ts;
            }
          }

          rows.add(new Row(flowId, name, er.status, mode, er.details, finishedToday, er.message));
        }
      }

      rows.sort(new Comparator<Row>() {
        public int compare(Row a, Row b) { return rank(b.status) - rank(a.status); }
        private int rank(String s) {
          if ("ERROR".equalsIgnoreCase(s)) return 3;
          if ("WARNING".equalsIgnoreCase(s)) return 2;
          return 1;
        }
      });

      int nErr=0,nWar=0,nInf=0;
      for (Row r: rows) {
        if ("ERROR".equalsIgnoreCase(r.status)) nErr++;
        else if ("WARNING".equalsIgnoreCase(r.status)) nWar++;
        else nInf++;
      }

      jw.beginObject();
        jw.name("counters").beginObject();
          jw.name("error").value(nErr);
          jw.name("warning").value(nWar);
          jw.name("info").value(nInf);
        jw.endObject();
        jw.name("rows").beginArray();
          for (Row r: rows) {
            jw.beginObject()
              .name("flowId").value(r.flowId)
              .name("name").value(r.name)
              .name("status").value(r.status)
              .name("mode").value(r.mode == null ? "" : r.mode)
              .name("details").value(r.details == null ? "" : r.details)
              .name("today").value(r.today)
              .name("message").value(r.message == null ? "" : r.message)
            .endObject();
          }
        jw.endArray();
      jw.endObject();
      jw.flush();

    } catch (Exception e) {
      try {
        resp.setStatus(500);
        if (jw != null) {
          jw.beginObject().name("error").value("monitoring failed: " + e.getMessage()).endObject();
          jw.flush();
        }
      } catch (Exception ignore) {}
    }
  }

  // =========================
  // intervals (existing style)
  // =========================
  private static EvalResult evalIntervals(ZonedDateTime now, PollerService poller, String flowId, Flow f, Defaults d) {
    if (f.intervals == null || f.intervals.isEmpty()) return EvalResult.info("", "");

    int warnLead = (f.warningLeadMinutes != null) ? f.warningLeadMinutes.intValue() : d.warningLeadMinutes;
    int grace = (f.errorGraceMinutes != null) ? f.errorGraceMinutes.intValue() : d.errorGraceMinutes;

    StringBuilder details = new StringBuilder();
    EvalResult worst = EvalResult.info("", "");
    String worstMsg = "";

    int i = 0;
    for (Interval in : f.intervals) {
      if (in == null) continue;

      LocalTime start = in.start;
      LocalTime end = in.end;
      int expected = in.expected;
      if (start == null || end == null || expected <= 0) continue;

      ZonedDateTime endZ = now.toLocalDate().atTime(end).atZone(ZONE);
      ZonedDateTime warnAt = endZ.minusMinutes(warnLead);
      ZonedDateTime errAt = endZ.plusMinutes(grace);

      long actual = (poller == null) ? 0L : poller.getFinishedBetween(flowId, start, end);

      if (i > 0) details.append(", ");
      details.append(start.format(TIME)).append("-").append(end.format(TIME))
             .append(" ").append(actual).append("/").append(expected);

      EvalResult cur = EvalResult.info("", "");

      double tol = (in.tolerancePct != null) ? in.tolerancePct.doubleValue() : 0.0;
      long minOk = (long)Math.floor(expected * (1.0 - tol));

      if (now.isAfter(errAt)) {
        if (actual < minOk) {
          long missing = (minOk - actual);
          cur = EvalResult.error("Intervall " + start.format(TIME) + "-" + end.format(TIME) + ": ligger efter. Saknar " + missing, "");
        }
      } else if (!now.isBefore(warnAt)) {
        double warnAtPct = (in.warnAtPct != null) ? in.warnAtPct.doubleValue() : 1.0;
        long warnMin = (long)Math.floor(expected * warnAtPct);
        if (actual < warnMin) {
          long missing = (warnMin - actual);
          cur = EvalResult.warning("Intervall " + start.format(TIME) + "-" + end.format(TIME) + ": ligger efter. Saknar " + missing, "");
        }
      }

      if (cur.rank > worst.rank) {
        worst = cur;
        worstMsg = cur.message;
      }

      i++;
    }

    worst.details = details.toString();
    worst.message = worstMsg;
    return worst;
  }

  // =========================
  // exactTimes (NEW)
  // =========================
  private static EvalResult evalExactTimes(ZonedDateTime now, PollerService poller, String flowId, Flow f, Defaults d) {
    if (f.times == null || f.times.isEmpty()) return EvalResult.info("", "");

    int warnLead = (f.warningLeadMinutes != null) ? f.warningLeadMinutes.intValue() : d.warningLeadMinutes;
    int grace = (f.errorGraceMinutes != null) ? f.errorGraceMinutes.intValue() : d.errorGraceMinutes;

    StringBuilder details = new StringBuilder();
    EvalResult worst = EvalResult.info("", "");
    String worstMsg = "";

    int i = 0;
    for (ExactTime et : f.times) {
      if (et == null || et.time == null || et.expected <= 0) continue;

      LocalTime t = et.time;
      int expected = et.expected;

      ZonedDateTime tZ = now.toLocalDate().atTime(t).atZone(ZONE);
      ZonedDateTime warnAt = tZ.minusMinutes(warnLead);
      ZonedDateTime errAt = tZ.plusMinutes(grace);

      // Count finished from start of day until the configured time (inclusive-ish)
      long actual = (poller == null) ? 0L : poller.getFinishedBetween(flowId, LocalTime.MIDNIGHT, t);

      if (i > 0) details.append(", ");
      details.append(t.format(TIME)).append(" ").append(actual).append("/").append(expected);

      EvalResult cur = EvalResult.info("", "");

      if (now.isAfter(errAt)) {
        if (actual < expected) {
          long missing = expected - actual;
          cur = EvalResult.error("Tid " + t.format(TIME) + ": saknar " + missing, "");
        }
      } else if (!now.isBefore(warnAt)) {
        if (actual < expected) {
          long missing = expected - actual;
          cur = EvalResult.warning("Tid " + t.format(TIME) + ": saknar " + missing, "");
        }
      }

      if (cur.rank > worst.rank) {
        worst = cur;
        worstMsg = cur.message;
      }

      i++;
    }

    worst.details = details.toString();
    worst.message = worstMsg;
    return worst;
  }

  // ---------------- config parsing ----------------

  private static Config readConfig(File f) throws Exception {
    Config cfg = new Config();
    cfg.flows = new ArrayList<Flow>();
    cfg.defaults = Defaults.defaults();

    if (f == null || !f.exists()) return cfg;

    String json = slurp(f);
    ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
    if (engine == null) throw new IllegalStateException("Nashorn saknas (javascript engine)");

    Object raw = engine.eval("Java.asJSONCompatible(JSON.parse(" + toJsStringLiteral(json) + "))");
    if (!(raw instanceof Map)) return cfg;

    Map<?, ?> m = (Map<?, ?>) raw;

    Object defaultsObj = m.get("defaults");
    if (defaultsObj instanceof Map) cfg.defaults = Defaults.from((Map<?, ?>) defaultsObj);

    Object flowsObj = m.get("flows");
    if (!(flowsObj instanceof List)) return cfg;

    for (Object o : (List<?>) flowsObj) {
      if (!(o instanceof Map)) continue;
      Map<?, ?> fm = (Map<?, ?>) o;

      Flow flow = new Flow();
      flow.flowId = s(fm.get("flowId"));
      flow.name = s(fm.get("name"));
      flow.mode = s(fm.get("mode"));
      flow.enabled = b(fm.get("enabled"), true);
      flow.warningLeadMinutes = i(fm.get("warningLeadMinutes"));
      flow.errorGraceMinutes = i(fm.get("errorGraceMinutes"));
      flow.intervals = toIntervals(fm.get("intervals"));
      flow.times = toExactTimes(fm.get("times"));
      cfg.flows.add(flow);
    }

    return cfg;
  }

  private static List<Interval> toIntervals(Object o) {
    List<Interval> out = new ArrayList<Interval>();
    if (!(o instanceof List)) return out;
    for (Object x : (List<?>) o) {
      if (!(x instanceof Map)) continue;
      Map<?, ?> im = (Map<?, ?>) x;
      LocalTime start = parseTime(s(im.get("start")));
      LocalTime end = parseTime(s(im.get("end")));
      Integer exp = i(im.get("expected"));
      if (start == null || end == null || exp == null) continue;

      Interval in = new Interval();
      in.start = start;
      in.end = end;
      in.expected = exp.intValue();
      in.warnAtPct = d(im.get("warnAtPct"));
      in.tolerancePct = d(im.get("tolerancePct"));
      out.add(in);
    }
    return out;
  }

  private static List<ExactTime> toExactTimes(Object o) {
    List<ExactTime> out = new ArrayList<ExactTime>();
    if (!(o instanceof List)) return out;
    for (Object x : (List<?>) o) {
      if (!(x instanceof Map)) continue;
      Map<?, ?> tm = (Map<?, ?>) x;
      LocalTime time = parseTime(s(tm.get("time")));
      Integer exp = i(tm.get("expected"));
      if (time == null || exp == null) continue;
      ExactTime et = new ExactTime();
      et.time = time;
      et.expected = exp.intValue();
      out.add(et);
    }
    return out;
  }

  private static LocalTime parseTime(String t) {
    if (t == null) return null;
    String x = t.trim();
    if (x.isEmpty()) return null;
    try { return LocalTime.parse(x, TIME); } catch (Exception e) { return null; }
  }

  private static Integer i(Object o) {
    if (o == null) return null;
    if (o instanceof Number) return Integer.valueOf(((Number)o).intValue());
    try { return Integer.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
  }

  private static Double d(Object o) {
    if (o == null) return null;
    if (o instanceof Number) return Double.valueOf(((Number)o).doubleValue());
    try { return Double.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
  }

  private static String slurp(File f) throws Exception {
    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
    try {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) sb.append(line).append("\n");
      return sb.toString();
    } finally { try { br.close(); } catch (Exception ignore) {} }
  }

  private static String toJsStringLiteral(String s) {
    String t = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    return "\"" + t + "\"";
  }

  private static String s(Object o) { return (o == null) ? "" : String.valueOf(o); }
  private static String t(String s) { return (s == null) ? "" : s.trim(); }
  private static boolean b(Object o, boolean dflt) {
    if (o == null) return dflt;
    if (o instanceof Boolean) return ((Boolean)o).booleanValue();
    String s = String.valueOf(o).trim().toLowerCase();
    if ("true".equals(s)) return true;
    if ("false".equals(s)) return false;
    return dflt;
  }

  // ---------------- models ----------------

  private static final class Config { Defaults defaults; List<Flow> flows; }
  private static final class Defaults {
    int warningLeadMinutes;
    int errorGraceMinutes;
    static Defaults defaults() {
      Defaults d = new Defaults();
      d.warningLeadMinutes = 15;
      d.errorGraceMinutes = 0;
      return d;
    }
    static Defaults from(Map<?, ?> m) {
      Defaults d = defaults();
      Integer wl = i(m.get("warningLeadMinutes"));
      Integer gr = i(m.get("errorGraceMinutes"));
      if (wl != null) d.warningLeadMinutes = wl.intValue();
      if (gr != null) d.errorGraceMinutes = gr.intValue();
      return d;
    }
  }

  private static final class Flow {
    String flowId;
    String name;
    String mode;
    boolean enabled;
    Integer warningLeadMinutes;
    Integer errorGraceMinutes;
    List<Interval> intervals = new ArrayList<Interval>();
    List<ExactTime> times = new ArrayList<ExactTime>();
  }

  private static final class Interval {
    LocalTime start;
    LocalTime end;
    int expected;
    Double warnAtPct;
    Double tolerancePct;
  }

  private static final class ExactTime {
    LocalTime time;
    int expected;
  }

  private static final class Row {
    final String flowId, name, status, mode, details, message;
    final long today;
    Row(String flowId, String name, String status, String mode, String details, long today, String message) {
      this.flowId=flowId; this.name=name; this.status=status; this.mode=mode; this.details=details; this.today=today; this.message=message;
    }
  }

  private static final class EvalResult {
    String status;
    String message;
    String details;
    int rank;

    static EvalResult info(String msg, String details) {
      EvalResult r = new EvalResult();
      r.status="INFO"; r.message=(msg==null?"":msg); r.details=(details==null?"":details); r.rank=1;
      return r;
    }
    static EvalResult warning(String msg, String details) {
      EvalResult r = new EvalResult();
      r.status="WARNING"; r.message=(msg==null?"":msg); r.details=(details==null?"":details); r.rank=2;
      return r;
    }
    static EvalResult error(String msg, String details) {
      EvalResult r = new EvalResult();
      r.status="ERROR"; r.message=(msg==null?"":msg); r.details=(details==null?"":details); r.rank=3;
      return r;
    }
  }
}

