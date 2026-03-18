package se.apendo.flowmon.monitoring.v2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public final class MonitoringConfigLoaderV2 {

    private MonitoringConfigLoaderV2() {}

    public static Config read(File f) throws Exception {
        Config cfg = new Config();
        cfg.defaults = new MonitoringDefaults();
        cfg.rules = new ArrayList<FlowMonitoringRule>();

        if (f == null || !f.exists()) return cfg;

        String json = slurp(f);
        if (json == null || json.trim().isEmpty()) return cfg;

        ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
        if (engine == null) throw new IllegalStateException("Nashorn saknas (javascript engine)");

        Object raw = engine.eval("Java.asJSONCompatible(JSON.parse(" + toJsStringLiteral(json) + "))");
        if (!(raw instanceof Map)) return cfg;

        Map<?, ?> root = (Map<?, ?>) raw;

        Object defaultsObj = root.get("defaults");
        if (defaultsObj instanceof Map) {
            applyDefaults(cfg.defaults, (Map<?, ?>) defaultsObj);
        }

        Object flowsObj = root.get("flows");
        if (!(flowsObj instanceof List)) return cfg;

        for (Object o : (List<?>) flowsObj) {
            if (!(o instanceof Map)) continue;
            FlowMonitoringRule rule = parseRule((Map<?, ?>) o, cfg.defaults);
            if (rule != null) cfg.rules.add(rule);
        }

        return cfg;
    }

    private static void applyDefaults(MonitoringDefaults d, Map<?, ?> m) {
        Integer wl = i(m.get("warningLeadMinutes"));
        Integer eg = i(m.get("errorGraceMinutes"));
        LocalTime be = parseTime(s(m.get("businessEnd")));
        String tz = s(m.get("timezone"));

        if (wl != null) d.warningLeadMinutes = wl.intValue();
        if (eg != null) d.errorGraceMinutes = eg.intValue();
        if (be != null) d.businessEnd = be;
        if (!isBlank(tz)) {
            try { d.zoneId = ZoneId.of(tz.trim()); } catch (Exception ignore) {}
        }
    }

    private static FlowMonitoringRule parseRule(Map<?, ?> m, MonitoringDefaults defaults) {
        FlowMonitoringRule r = new FlowMonitoringRule();
        r.flowId = s(m.get("flowId"));
        r.name = s(m.get("name"));
        r.enabled = b(m.get("enabled"), true);
        r.warningLeadMinutes = i(m.get("warningLeadMinutes"));
        r.errorGraceMinutes = i(m.get("errorGraceMinutes"));
        r.businessEnd = s(m.get("businessEnd"));

        Object schedObj = m.get("schedule");
        if (!(schedObj instanceof Map)) {
            // optional fallback: old format -> map into new format
            r.schedule = parseLegacySchedule(m);
        } else {
            r.schedule = parseSchedule((Map<?, ?>) schedObj);
        }

        if (isBlank(r.flowId) || r.schedule == null) return null;
        if (isBlank(r.name)) r.name = r.flowId.replace("||", " - ");
        return r;
    }

    private static Schedule parseLegacySchedule(Map<?, ?> m) {
        String mode = s(m.get("mode")).trim();
        if (mode.isEmpty()) mode = "intervals";

        if ("intervals".equalsIgnoreCase(mode)) {
            IntervalSchedule s = new IntervalSchedule();
            s.intervals.addAll(parseIntervals(m.get("intervals")));
            return s.intervals.isEmpty() ? null : s;
        }

        if ("exactTimes".equalsIgnoreCase(mode) || "exacttimes".equalsIgnoreCase(mode)) {
            ExactTimesSchedule s = new ExactTimesSchedule();
            s.times.addAll(parseTimes(m.get("times")));
            return s.times.isEmpty() ? null : s;
        }

        return null;
    }

    private static Schedule parseSchedule(Map<?, ?> m) {
        String type = s(m.get("type")).trim();

        if ("interval".equalsIgnoreCase(type)) {
            IntervalSchedule s = new IntervalSchedule();
            s.intervals.addAll(parseIntervals(m.get("intervals")));
            return s.intervals.isEmpty() ? null : s;
        }

        if ("exactTimes".equalsIgnoreCase(type) || "exacttimes".equalsIgnoreCase(type)) {
            ExactTimesSchedule s = new ExactTimesSchedule();
            s.times.addAll(parseTimes(m.get("times")));
            return s.times.isEmpty() ? null : s;
        }

        if ("weekdays".equalsIgnoreCase(type)) {
            WeekdaysSchedule s = new WeekdaysSchedule();
            s.expected = nz(i(m.get("expected")));
            s.dueTime = parseTime(s(m.get("dueTime")));
            s.carryOverMode = parseCarryOverMode(s(m.get("carryOverMode")));
            s.weekdays.addAll(parseWeekdays(m.get("weekdays")));
            return (s.expected > 0 && !s.weekdays.isEmpty()) ? s : null;
        }

        if ("monthDays".equalsIgnoreCase(type) || "monthdays".equalsIgnoreCase(type)) {
            MonthDaysSchedule s = new MonthDaysSchedule();
            s.expected = nz(i(m.get("expected")));
            s.dueTime = parseTime(s(m.get("dueTime")));
            s.carryOverMode = parseCarryOverMode(s(m.get("carryOverMode")));
            s.monthDays.addAll(parseMonthDays(m.get("monthDays")));
            return (s.expected > 0 && !s.monthDays.isEmpty()) ? s : null;
        }

        if ("dates".equalsIgnoreCase(type)) {
            DatesSchedule s = new DatesSchedule();
            s.expected = nz(i(m.get("expected")));
            s.dueTime = parseTime(s(m.get("dueTime")));
            s.carryOverMode = parseCarryOverMode(s(m.get("carryOverMode")));
            s.dates.addAll(parseDates(m.get("dates")));
            return (s.expected > 0 && !s.dates.isEmpty()) ? s : null;
        }

        return null;
    }

    private static List<IntervalRule> parseIntervals(Object o) {
        List<IntervalRule> out = new ArrayList<IntervalRule>();
        if (!(o instanceof List)) return out;

        for (Object x : (List<?>) o) {
            if (!(x instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) x;

            IntervalRule r = new IntervalRule();
            r.start = parseTime(s(m.get("start")));
            r.end = parseTime(s(m.get("end")));
            r.expected = nz(i(m.get("expected")));
            r.warnAtPct = d(m.get("warnAtPct"));
            r.tolerancePct = d(m.get("tolerancePct"));

            if (r.start == null || r.end == null || r.expected <= 0) continue;
            out.add(r);
        }

        return out;
    }

    private static List<ExactTimeRule> parseTimes(Object o) {
        List<ExactTimeRule> out = new ArrayList<ExactTimeRule>();
        if (!(o instanceof List)) return out;

        for (Object x : (List<?>) o) {
            if (!(x instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) x;

            ExactTimeRule r = new ExactTimeRule();
            r.time = parseTime(s(m.get("time")));
            r.expected = nz(i(m.get("expected")));

            if (r.time == null || r.expected <= 0) continue;
            out.add(r);
        }

        return out;
    }

    private static Set<DayOfWeek> parseWeekdays(Object o) {
        Set<DayOfWeek> out = new HashSet<DayOfWeek>();
        if (!(o instanceof List)) return out;

        for (Object x : (List<?>) o) {
            Integer n = i(x);
            if (n == null) continue;
            try {
                out.add(DayOfWeek.of(n.intValue()));
            } catch (Exception ignore) {}
        }

        return out;
    }

    private static Set<Integer> parseMonthDays(Object o) {
        Set<Integer> out = new HashSet<Integer>();
        if (!(o instanceof List)) return out;

        for (Object x : (List<?>) o) {
            Integer n = i(x);
            if (n == null) continue;
            int v = n.intValue();
            if (v >= 1 && v <= 31) out.add(Integer.valueOf(v));
        }

        return out;
    }

    private static Set<LocalDate> parseDates(Object o) {
        Set<LocalDate> out = new HashSet<LocalDate>();
        if (!(o instanceof List)) return out;

        for (Object x : (List<?>) o) {
            String s = s(x).trim();
            if (s.isEmpty()) continue;
            try {
                out.add(LocalDate.parse(s));
            } catch (Exception ignore) {}
        }

        return out;
    }

    private static CarryOverMode parseCarryOverMode(String s) {
        if (isBlank(s)) return CarryOverMode.SAME_DAY;
        String x = s.trim().toUpperCase().replace('-', '_');
        try {
            return CarryOverMode.valueOf(x);
        } catch (Exception e) {
            return CarryOverMode.SAME_DAY;
        }
    }

    private static LocalTime parseTime(String s) {
        if (isBlank(s)) return null;
        try { return LocalTime.parse(s.trim()); } catch (Exception e) { return null; }
    }

    private static Integer i(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return Integer.valueOf(((Number) o).intValue());
        try { return Integer.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }

    private static Double d(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return Double.valueOf(((Number) o).doubleValue());
        try { return Double.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }

    private static boolean b(Object o, boolean dflt) {
        if (o == null) return dflt;
        if (o instanceof Boolean) return ((Boolean) o).booleanValue();
        String s = String.valueOf(o).trim().toLowerCase();
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;
        return dflt;
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static int nz(Integer x) {
        return x == null ? 0 : x.intValue();
    }

    private static String slurp(File f) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } finally {
            try { br.close(); } catch (Exception ignore) {}
        }
    }

    private static String toJsStringLiteral(String s) {
        String t = s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
        return "\"" + t + "\"";
    }

    public static final class Config {
        public MonitoringDefaults defaults;
        public List<FlowMonitoringRule> rules;
    }
}
