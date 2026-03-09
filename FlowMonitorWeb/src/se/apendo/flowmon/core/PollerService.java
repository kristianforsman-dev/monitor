
package se.apendo.flowmon.core;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.sql.Timestamp;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.naming.InitialContext;
import javax.sql.DataSource;

import se.apendo.flowmon.core.Models.CheckRow;
import se.apendo.flowmon.core.Models.FailedRow;
import se.apendo.flowmon.core.Models.LiveRow;
import se.apendo.flowmon.core.Models.Snapshot;
import se.apendo.flowmon.core.FlowIds;

/**
 * Pollar DB2 via JNDI jdbc/WPSDMP och bygger en Snapshot i minnet.
 * Uppdateringsfrekvens: var 2:e sekund (kan justeras).
 *
 * OBS: Check-tabellerna fylls här som tomma listor för att hålla datavägen ren
 * och undvika spagetti. Vi bygger checks-logiken stegvis ovanpå stabil data (live+failed).
 */
public final class PollerService {

    public static final String CTX_KEY = "flowmon.poller";

    private static final String JNDI_DS_1 = "java:comp/env/jdbc/WPSDB";
    private static final String JNDI_DS_2 = "jdbc/WPSDB";

    private final ConfigLoader config;
    private final Db2StatsDao dao = new Db2StatsDao();

    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong version = new AtomicLong(0);

    private volatile Snapshot latest;
    private volatile DataSource ds;

    // DB2 schema handling: avoid environment-dependent CURRENT SCHEMA (e.g. db2admin vs db2inst1)
    // Default is DB2ADMIN, can be overridden with JVM system property: -Dflowmon.db.schema=SCHEMA
    private final String dbSchema;
    private volatile String lastError;

    // -------- RANGE cache (protect DB2) --------
    private static final int RANGE_MAX_DAYS = 31;
    private static final long RANGE_CACHE_TTL_MS = 30_000L; // 30s
    private static final int RANGE_CACHE_MAX_ENTRIES = 64;
    private final Map<String, RangeCacheEntry> rangeCache = new HashMap<String, RangeCacheEntry>();

    private static final class RangeCacheEntry {
        final long ts;
        final List<LiveRow> rows;
        RangeCacheEntry(long ts, List<LiveRow> rows){ this.ts = ts; this.rows = rows; }
    }


    // -------- Fas 2B: finished time buckets (5-min) for "aktiesida" feel --------
    private static final ZoneId ZONE = ZoneId.of("Europe/Stockholm");
    private static final int BUCKET_MINUTES = 5;
    private static final int BUCKETS_PER_DAY = (24 * 60) / BUCKET_MINUTES; // 288

    // bucketDate: vilken dag buckets gäller för (Europe/Stockholm)
    private volatile LocalDate bucketDate = LocalDate.now(ZONE);
    private final Map<String, int[]> finishedBuckets5m = new HashMap<String, int[]>();
  private final Map<String, java.sql.Timestamp> lastFinishedCompletedByFlow = new HashMap<String, java.sql.Timestamp>();
    private volatile Timestamp lastFinishedCompleted = new Timestamp(0L);

    public PollerService(ConfigLoader config) {
        this.config = config;
        this.latest = emptySnapshot(0L, "Init…");
        // starta buckets på dagens midnatt (minskar risk för duplikat vid deploy)
        ZonedDateTime z = ZonedDateTime.now(ZONE);
        this.bucketDate = z.toLocalDate();
        this.lastFinishedCompleted = Timestamp.valueOf(z.toLocalDate().atStartOfDay());

        String s = System.getProperty("flowmon.db.schema");
        if (s == null || s.trim().length() == 0) {
            s = "DB2ADMIN";
        }
        this.dbSchema = s.trim();
    }

    public void start() {
        this.ds = lookupDataSource();
        this.lastError = null;

        ses.scheduleAtFixedRate(new Runnable() {
            @Override public void run() { tick(); }
        }, 0, 2, TimeUnit.SECONDS);
    }

    public void stop() {
        try { ses.shutdownNow(); } catch (Exception ignore) {}
    }

    public Snapshot getLatest() { return latest; }

    public boolean hasChangedSince(long sinceVersion) {
        Snapshot s = latest;
        return s != null && s.version > sinceVersion;
    }

    public String getLastError() { return lastError; }

    public DataSource getDataSource() { return ds; }

    /** Används när ACK ändras för att trigga UI att hämta ny snapshot direkt. */
    public void bumpVersion() {
        Snapshot s = latest;
        if (s == null) return;
        long v = version.incrementAndGet();
        latest = new Snapshot(v, isoNow(),
                s.liveRunning, s.failed,
                s.interval, s.exactTimes, s.weekdays, s.monthdays, s.dates,
                s.flowCount);
    }


    private void applyCurrentSchema(Connection c) throws SQLException {
        if (c == null) return;
        // DB2: SET CURRENT SCHEMA <schema>
        Statement st = null;
        try {
            st = c.createStatement();
            st.execute("SET CURRENT SCHEMA " + dbSchema);
        } finally {
            try { if (st != null) st.close(); } catch (Exception ignore) {}
        }
    }

    private void tick() {
        Connection c = null;
        try {
            c = ds.getConnection();
            applyCurrentSchema(c);

            // Live: endast "Running" (STATE=2) hanteras i Db2StatsDao SQL via CASE->'Running'
            List<LiveRow> live = dao.fetchTodayCounts(c);
            live.addAll(dao.fetchTodayStoppedCounts(c));
            List<FailedRow> failed = dao.fetchTodayFailed(c);

            // Uppdatera Finished-buckets med nya instanser sedan sist (STATE=3)
            updateFinishedBuckets(c);

            // Just nu: checks tomma (vi bygger check-logiken senare baserat på config och dagens data)
            List<CheckRow> empty = new ArrayList<CheckRow>();

            int flowCount = (config == null || config.getFlows() == null) ? 0 : config.getFlows().size();

            long v = version.incrementAndGet();
            latest = new Snapshot(v, isoNow(), live, failed, empty, empty, empty, empty, empty, flowCount);
            lastError = null;

        } catch (Exception e) {
            lastError = safe(e);
            // bump version så longpoll släpper även vid fel (UI kan visa felet)
            long v = version.incrementAndGet();
            latest = emptySnapshot(v, lastError);
        } finally {
            try { if (c != null) c.close(); } catch (Exception ignore) {}
        }
    }


    // ---------------- Finished buckets API (used by monitoring) ----------------

    /**
     * Summerar Finished (STATE=3) mellan start (inkl) och end (exkl) för given flowId, med 5-min upplösning.
     * Returnerar 0 om flowId saknas eller om dag rullat.
     */
    public long getFinishedBetween(String flowId, LocalTime startInclusive, LocalTime endExclusive) {
        if (flowId == null) return 0L;
        LocalDate today = LocalDate.now(ZONE);
        if (!today.equals(bucketDate)) return 0L;

        int[] buckets = finishedBuckets5m.get(flowId);
        if (buckets == null) return 0L;

        int s = bucketIndex(startInclusive);
        int e = bucketIndex(endExclusive);

        if (e < s) { // över midnatt hanteras inte här (konfig bör ligga samma dag)
            return 0L;
        }
        if (s < 0) s = 0;
        if (e > BUCKETS_PER_DAY) e = BUCKETS_PER_DAY;

        long sum = 0L;
        for (int i = s; i < e; i++) sum += buckets[i];
        return sum;
    }

    /** Total finished idag för ett flowId (summerar alla 5-min buckets). */
    
  /** Senaste COMPLETED-tid för Finished per flow (för UI). */
  public java.sql.Timestamp getLastFinishedCompleted(String flowId) {
    if (flowId == null) return null;
    synchronized (finishedBuckets5m) {
      return lastFinishedCompletedByFlow.get(flowId);
    }
  }

public long getFinishedToday(String flowId) {
        int[] buckets = finishedBuckets5m.get(flowId);
        if (buckets == null) return 0L;
        long sum = 0L;
        for (int v : buckets) sum += (long) v;
        return sum;
    }


    public long getFinishedUntil(String flowId, LocalTime endExclusive) {
        return getFinishedBetween(flowId, LocalTime.MIDNIGHT, endExclusive);
    }

    private int bucketIndex(LocalTime t) {
        int mins = t.getHour() * 60 + t.getMinute();
        int idx = mins / BUCKET_MINUTES;
        if (idx < 0) return 0;
        if (idx > BUCKETS_PER_DAY) return BUCKETS_PER_DAY;
        return idx;
    }

    private void updateFinishedBuckets(Connection c) throws Exception {
        // dag rollover -> reset
        LocalDate today = LocalDate.now(ZONE);
        if (!today.equals(bucketDate)) {
            finishedBuckets5m.clear();
            bucketDate = today;
            lastFinishedCompleted = Timestamp.valueOf(today.atStartOfDay());
        }

        List<Db2StatsDao.FinishedInstance> news = dao.fetchFinishedSince(c, lastFinishedCompleted);
        if (news == null || news.isEmpty()) return;

        Timestamp max = lastFinishedCompleted;
        for (Db2StatsDao.FinishedInstance fi : news) {
            if (fi == null || fi.completed == null) continue;
            if (fi.completed.after(max)) max = fi.completed;

            LocalTime lt = fi.completed.toLocalDateTime().atZone(ZONE).toLocalTime();
            int idx = bucketIndex(lt);

            String flowId = FlowIds.build(fi.sender, fi.receiver, fi.msgType);

            int[] arr = finishedBuckets5m.get(flowId);
            if (arr == null) {
                arr = new int[BUCKETS_PER_DAY];
                finishedBuckets5m.put(flowId, arr);
            }
            arr[idx] += 1;
        // track latest completed timestamp per flow (for UI)
        java.sql.Timestamp prev = lastFinishedCompletedByFlow.get(flowId);
        if (prev == null || fi.completed.after(prev)) lastFinishedCompletedByFlow.put(flowId, fi.completed);
        }

        // Advance watermark slightly backwards to reduce risk of missing same-millisecond rows:
        // keep max as-is, query uses ">" so duplicates are unlikely; DB2 timestamp precision is usually microseconds.
        lastFinishedCompleted = max;
    }

    private Snapshot emptySnapshot(long v, String msg) {
        List<LiveRow> live = new ArrayList<LiveRow>();
        List<FailedRow> failed = new ArrayList<FailedRow>();
        List<CheckRow> empty = new ArrayList<CheckRow>();
        int flowCount = (config == null || config.getFlows() == null) ? 0 : config.getFlows().size();
        return new Snapshot(v, isoNow(), live, failed, empty, empty, empty, empty, empty, flowCount);
    }

    private DataSource lookupDataSource() {
        try {
            InitialContext ic = new InitialContext();
            try { return (DataSource) ic.lookup(JNDI_DS_1); }
            catch (Exception ignore) { return (DataSource) ic.lookup(JNDI_DS_2); }
        } catch (Exception e) {
            throw new IllegalStateException("Kunde inte slå upp datasource '" + JNDI_DS_1 + "' eller '" + JNDI_DS_2 + "': " + e.getMessage(), e);
        }
    }

    private static String safe(Exception e) {
        String msg = e.getClass().getName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
        return msg.replaceAll("[\\r\\n\\t]+", " ");
    }

    private static String isoNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Stockholm"));
        String s = sdf.format(new Date());
        if (s != null && s.length() > 5) {
            int n = s.length();
            return s.substring(0, n - 2) + ":" + s.substring(n - 2);
        }
        return s;
    }


    // ===== RANGE QUERY =====
    public List<LiveRow> queryRange(java.time.LocalDate from,
                                    java.time.LocalDate to) throws Exception {
        // Normalize inputs
        if (from == null || to == null) {
            java.time.LocalDate today = java.time.LocalDate.now(ZONE);
            if (from == null) from = today;
            if (to == null) to = today;
        }
        if (from.isAfter(to)) { java.time.LocalDate tmp = from; from = to; to = tmp; }

        long spanDays = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1L;
        if (spanDays > RANGE_MAX_DAYS) {
            throw new IllegalArgumentException("För stort intervall (" + spanDays + " dagar). Max " + RANGE_MAX_DAYS + ".");
        }

        final String key = String.valueOf(from) + ".." + String.valueOf(to);
        final long now = System.currentTimeMillis();

        synchronized (rangeCache) {
            RangeCacheEntry e = rangeCache.get(key);
            if (e != null && (now - e.ts) <= RANGE_CACHE_TTL_MS && e.rows != null) {
                return e.rows;
            }
        }

        Connection c = null;
        try {
            c = ds.getConnection();
            List<LiveRow> out = dao.fetchRangeCounts(c, from, to);
            out.addAll(dao.fetchRangeStoppedCounts(c, from, to));

            synchronized (rangeCache) {
                rangeCache.put(key, new RangeCacheEntry(now, out));
                if (rangeCache.size() > RANGE_CACHE_MAX_ENTRIES) {
                    // remove oldest entry
                    String oldestK = null;
                    long oldestTs = Long.MAX_VALUE;
                    for (Map.Entry<String, RangeCacheEntry> me : rangeCache.entrySet()) {
                        RangeCacheEntry v = me.getValue();
                        if (v != null && v.ts < oldestTs) { oldestTs = v.ts; oldestK = me.getKey(); }
                    }
                    if (oldestK != null) rangeCache.remove(oldestK);
                }
            }

            return out;
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignore) {}
        }
    }
    }




