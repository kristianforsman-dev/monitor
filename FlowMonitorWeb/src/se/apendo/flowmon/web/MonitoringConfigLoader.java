package se.apendo.flowmon.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public final class MonitoringConfigLoader {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private MonitoringConfigLoader() {}

    public static Config read(File f) throws Exception {
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
        } finally {
            try { br.close(); } catch (Exception ignore) {}
        }
    }

    private static String toJsStringLiteral(String s) {
        String t = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
        return "\"" + t + "\"";
    }

    private static String s(Object o) { return (o == null) ? "" : String.valueOf(o); }

    private static boolean b(Object o, boolean dflt) {
        if (o == null) return dflt;
        if (o instanceof Boolean) return ((Boolean)o).booleanValue();
        String s = String.valueOf(o).trim().toLowerCase();
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;
        return dflt;
    }

    public static final class Config {
        public Defaults defaults;
        public List<Flow> flows;
    }

    public static final class Defaults {
        public int warningLeadMinutes;
        public int errorGraceMinutes;

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

    public static final class Flow {
        public String flowId;
        public String name;
        public String mode;
        public boolean enabled;
        public Integer warningLeadMinutes;
        public Integer errorGraceMinutes;
        public List<Interval> intervals = new ArrayList<Interval>();
        public List<ExactTime> times = new ArrayList<ExactTime>();
    }

    public static final class Interval {
        public LocalTime start;
        public LocalTime end;
        public int expected;
        public Double warnAtPct;
        public Double tolerancePct;
    }

    public static final class ExactTime {
        public LocalTime time;
        public int expected;
    }
}
