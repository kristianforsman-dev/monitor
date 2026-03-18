/* devserver/server.js - deterministic mock dev server for FlowMonitorWeb */
const express = require("express");
const path = require("path");

function loadMockData(){
  try{
    const p = path.join(__dirname, "mock-data.json");
    return JSON.parse(fs.readFileSync(p, "utf8"));
  }catch(e){
    return {};
  }
}

function pickMockRange(range){
  try{
    const data = loadMockData();
    const key = String(range.from || "") + ".." + String(range.to || "");
    return data && data.range && data.range[key] ? data.range[key] : null;
  }catch(e){
    return null;
  }
}

function pickMockSection(name){
  try{
    const data = loadMockData();
    return data && data[name] ? data[name] : null;
  }catch(e){
    return null;
  }
}


function loadMockEvents(){
  try{
    const p = path.join(__dirname, "mock-events.json");
    return JSON.parse(fs.readFileSync(p, "utf8"));
  }catch(e){
    return null;
  }
}

function parseTs(s){
  if(!s) return null;
  const d = new Date(s);
  return isNaN(d.getTime()) ? null : d;
}

function isoDateLocal(d){
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function keyOf(sender, receiver, msgType){
  return String(sender || "") + "||" + String(receiver || "") + "||" + String(msgType || "");
}

function groupCountRows(instances, wantedState){
  const m = new Map();
  for(const x of (instances || [])){
    const st = String(x.state || "");
    if(wantedState && st.toLowerCase() !== String(wantedState).toLowerCase()) continue;
    const k = keyOf(x.sender, x.receiver, x.msgType);
    if(!m.has(k)){
      m.set(k, {
        sender: x.sender || "",
        receiver: x.receiver || "",
        msgType: x.msgType || "",
        count: 0
      });
    }
    m.get(k).count += 1;
  }
  return Array.from(m.values());
}

function mockInstancesInRange(range){
  const data = loadMockEvents();
  if(!data || !Array.isArray(data.instances)) return null;

  const from = String(range && range.from || "");
  const to = String(range && range.to || "");
  if(!from || !to) return [];

  return data.instances.filter(x => {
    const started = parseTs(x.started);
    if(!started) return false;
    const day = isoDateLocal(started);
    return day >= from && day <= to;
  });
}

function mockNow(){
  const data = loadMockEvents();
  if(data && data.now){
    const d = parseTs(data.now);
    if(d) return d;
  }
  return new Date();
}

function mockRangeRows(range){
  const arr = mockInstancesInRange(range);
  if(arr === null) return null;

  const m = new Map();
  for(const x of arr){
    const st = String(x.state || "");
    const normalized =
      st.toLowerCase() === "running" ? "Running" :
      st.toLowerCase() === "stopped" ? "Stopped" :
      st.toLowerCase() === "finished" ? "Finished" :
      st;

    const k = keyOf(x.sender, x.receiver, x.msgType) + "||" + normalized;
    if(!m.has(k)){
      m.set(k, {
        sender: x.sender || "",
        receiver: x.receiver || "",
        msgType: x.msgType || "",
        state: normalized,
        count: 0
      });
    }
    m.get(k).count += 1;
  }
  return { rows: Array.from(m.values()) };
}

function mockTodayLiveRows(stateName){
  const data = loadMockEvents();
  if(!data || !Array.isArray(data.instances)) return null;

  const now = mockNow();
  const today = stockholmDateOnly(now);

  const arr = data.instances.filter(x => {
    const st = String(x.state || "").toLowerCase();
    const wanted = String(stateName || "").toLowerCase();
    if(st !== wanted) return false;
    const started = parseTs(x.started);
    if(!started) return false;
    return stockholmDateOnly(started) === today;
  });

  const rows = groupCountRows(arr, null).map(r => ({
    sender: r.sender,
    receiver: r.receiver,
    msgType: r.msgType,
    count: r.count,
    state:
      stateName === "running" ? "Running" :
      stateName === "stopped" ? "Stopped" :
      stateName === "finished" ? "Finished" :
      stateName
  }));

  return { rows };
}

function countFinishedBetween(instances, flowId, fromHHmm, toHHmm, nowDate){
  const parts = String(flowId || "").split("||");
  if(parts.length < 3) return 0;
  const sender = parts[0], receiver = parts[1], msgType = parts[2];

  const fromTs = parseTs(isoDateLocal(nowDate) + "T" + fromHHmm + ":00+01:00");
  const toTs = parseTs(isoDateLocal(nowDate) + "T" + toHHmm + ":00+01:00");
  if(!fromTs || !toTs) return 0;

  let n = 0;
  for(const x of (instances || [])){
    if(String(x.state || "").toLowerCase() !== "finished") continue;
    if((x.sender || "") !== sender) continue;
    if((x.receiver || "") !== receiver) continue;
    if((x.msgType || "") !== msgType) continue;
    const completed = parseTs(x.completed);
    if(!completed) continue;
    if(completed >= fromTs && completed < toTs) n++;
  }
  return n;
}

function countFinishedUntil(instances, flowId, toHHmm, nowDate){
  return countFinishedBetween(instances, flowId, "00:00", toHHmm, nowDate);
}

function countFinishedToday(instances, flowId, nowDate){
  const parts = String(flowId || "").split("||");
  if(parts.length < 3) return 0;
  const sender = parts[0], receiver = parts[1], msgType = parts[2];
  const today = isoDateLocal(nowDate);

  let n = 0;
  for(const x of (instances || [])){
    if(String(x.state || "").toLowerCase() !== "finished") continue;
    if((x.sender || "") !== sender) continue;
    if((x.receiver || "") !== receiver) continue;
    if((x.msgType || "") !== msgType) continue;
    const completed = parseTs(x.completed);
    if(!completed) continue;
    if(stockholmDateOnly(completed) === today) n++;
  }
  return n;
}

function lastFinishedCompleted(instances, flowId){
  const parts = String(flowId || "").split("||");
  if(parts.length < 3) return null;
  const sender = parts[0], receiver = parts[1], msgType = parts[2];

  let best = null;
  for(const x of (instances || [])){
    if(String(x.state || "").toLowerCase() !== "finished") continue;
    if((x.sender || "") !== sender) continue;
    if((x.receiver || "") !== receiver) continue;
    if((x.msgType || "") !== msgType) continue;
    const completed = parseTs(x.completed);
    if(!completed) continue;
    if(!best || completed > best) best = completed;
  }
  return best;
}

function stockholmParts(d){
  if(!d) return null;
  const parts = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Europe/Stockholm",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23"
  }).formatToParts(d);

  const out = {};
  for(const p of parts){
    if(p.type !== "literal") out[p.type] = p.value;
  }
  return out;
}

function stockholmDateOnly(d){
  const p = stockholmParts(d);
  if(!p) return "";
  return `${p.year}-${p.month}-${p.day}`;
}

function stockholmHHmm(d){
  const p = stockholmParts(d);
  if(!p) return "";
  return `${p.hour}:${p.minute}`;
}

function fmtHHmmss(d){
  const p = stockholmParts(d);
  if(!p) return "";
  return `${p.hour}:${p.minute}:${p.second}`;
}

function mockMonitoringRows(){
  const events = loadMockEvents();
  if(!events || !Array.isArray(events.instances)) return null;

  const { cfg, flows } = loadMonitoringConfig();
  const now = new Date(events.now || Date.now());
  if(Number.isNaN(now.getTime())) return null;

  function pad2(n){ return String(n).padStart(2, "0"); }
  function fmtDateOnly(d){
    return d.getFullYear() + "-" + pad2(d.getMonth()+1) + "-" + pad2(d.getDate());
  }
  function fmtHHmmss(d){
    return pad2(d.getHours()) + ":" + pad2(d.getMinutes()) + ":" + pad2(d.getSeconds());
  }
  function fmtHHmm(d){
    return pad2(d.getHours()) + ":" + pad2(d.getMinutes());
  }
  function parseDateTime(x){
    const d = new Date(x);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  function parseTodayAt(hhmm){
    const m = String(hhmm || "").match(/^(\d{2}):(\d{2})$/);
    if(!m) return null;
    return new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate(),
      Number(m[1]),
      Number(m[2]),
      0,
      0
    );
  }
  function sameLocalDate(a, b){
    return a && b &&
      a.getFullYear() === b.getFullYear() &&
      a.getMonth() === b.getMonth() &&
      a.getDate() === b.getDate();
  }
  function parseFlowIdParts(flowId){
    const p = parseFlowId(flowId || "");
    return {
      sender: String(p.sender || ""),
      receiver: String(p.receiver || ""),
      msgType: String(p.msgType || "")
    };
  }
  function flowMatches(inst, fid){
    return String(inst.sender || "") === fid.sender &&
           String(inst.receiver || "") === fid.receiver &&
           String(inst.msgType || "") === fid.msgType;
  }
  function isFinished(inst){
    return String(inst.state || "").toLowerCase() === "finished";
  }
  function countFinishedToday(fid){
    let n = 0;
    for(const inst of events.instances){
      if(!flowMatches(inst, fid)) continue;
      if(!isFinished(inst)) continue;
      const c = parseDateTime(inst.completed);
      if(!c || !sameLocalDate(c, now)) continue;
      n++;
    }
    return n;
  }
  function countFinishedBetweenToday(fid, startHHmm, endHHmm){
    const start = parseTodayAt(startHHmm);
    const end = parseTodayAt(endHHmm);
    if(!start || !end || !(start < end)) return 0;
    let n = 0;
    for(const inst of events.instances){
      if(!flowMatches(inst, fid)) continue;
      if(!isFinished(inst)) continue;
      const c = parseDateTime(inst.completed);
      if(!c || !sameLocalDate(c, now)) continue;
      if(c >= start && c < end) n++;
    }
    return n;
  }
  function countFinishedUntilToday(fid, endHHmm){
    const end = parseTodayAt(endHHmm);
    if(!end) return 0;
    let n = 0;
    for(const inst of events.instances){
      if(!flowMatches(inst, fid)) continue;
      if(!isFinished(inst)) continue;
      const c = parseDateTime(inst.completed);
      if(!c || !sameLocalDate(c, now)) continue;
      if(c < end) n++;
    }
    return n;
  }
  function lastFinishedCompleted(fid){
    let best = null;
    for(const inst of events.instances){
      if(!flowMatches(inst, fid)) continue;
      if(!isFinished(inst)) continue;
      const c = parseDateTime(inst.completed);
      if(!c || !sameLocalDate(c, now)) continue;
      if(!best || c > best) best = c;
    }
    return best;
  }
  function normalizeFlow(f){
    const out = {
      flowId: String((f && f.flowId) || ""),
      name: String((f && f.name) || ""),
      enabled: !!(f && f.enabled !== false),
      warningLeadMinutes: (f && f.warningLeadMinutes != null) ? Number(f.warningLeadMinutes) : null,
      errorGraceMinutes: (f && f.errorGraceMinutes != null) ? Number(f.errorGraceMinutes) : null,
      businessEnd: String((f && f.businessEnd) || ""),
      mode: "intervals",
      intervals: [],
      times: [],
      weekdays: [],
      monthDays: [],
      dates: [],
      dueTime: "",
      carryOverMode: ""
    };

    const sched = (f && f.schedule && typeof f.schedule === "object") ? f.schedule : null;
    if(sched){
      const t = String(sched.type || "").trim();
      if(t === "interval"){
        out.mode = "interval";
        out.intervals = Array.isArray(sched.intervals) ? sched.intervals.slice() : [];
      }else if(t === "exactTimes" || t === "exacttimes"){
        out.mode = "exactTimes";
        out.times = Array.isArray(sched.times) ? sched.times.slice() : [];
      }else if(t === "weekdays"){
        out.mode = "weekdays";
        out.weekdays = Array.isArray(sched.weekdays) ? sched.weekdays.slice() : [];
        out.dueTime = String(sched.dueTime || "");
        out.carryOverMode = String(sched.carryOverMode || "");
        out.expected = Number(sched.expected || 0);
      }else if(t === "monthDays" || t === "monthdays"){
        out.mode = "monthDays";
        out.monthDays = Array.isArray(sched.monthDays) ? sched.monthDays.slice() : [];
        out.dueTime = String(sched.dueTime || "");
        out.carryOverMode = String(sched.carryOverMode || "");
        out.expected = Number(sched.expected || 0);
      }else if(t === "dates"){
        out.mode = "dates";
        out.dates = Array.isArray(sched.dates) ? sched.dates.slice() : [];
        out.dueTime = String(sched.dueTime || "");
        out.carryOverMode = String(sched.carryOverMode || "");
        out.expected = Number(sched.expected || 0);
      }
      return out;
    }

    // legacy fallback
    const mode = String((f && f.mode) || "intervals");
    out.mode = mode;
    out.intervals = Array.isArray(f && f.intervals) ? f.intervals.slice() : [];
    out.times = Array.isArray(f && f.times) ? f.times.slice() : [];
    return out;
  }

  const businessEndDefault = String((cfg && cfg.defaults && cfg.defaults.businessEnd) || "17:00");
  const warningLeadDefault = Number((cfg && cfg.defaults && cfg.defaults.warningLeadMinutes) || 15);
  const errorGraceDefault = Number((cfg && cfg.defaults && cfg.defaults.errorGraceMinutes) || 0);
  const todayStr = fmtDateOnly(now);
  const todayDow = ((now.getDay() + 6) % 7) + 1; // JS Sun=0 => ISO Mon=1..Sun=7

  function evalIntervalFlow(nf, fid, today){
    let status = "INFO";
    let message = "";
    let details = String(today) + " idag";

    for(const it of (nf.intervals || [])){
      const start = String((it && it.start) || "");
      const end = String((it && it.end) || "");
      const expected = Number((it && it.expected) || 0);
      const warnAtPct = Number((it && it.warnAtPct) == null ? 1 : it.warnAtPct);
      const tolerancePct = Number((it && it.tolerancePct) == null ? 0 : it.tolerancePct);

      const startDt = parseTodayAt(start);
      const endDt = parseTodayAt(end);
      if(!startDt || !endDt || !expected) continue;

      const actual = countFinishedBetweenToday(fid, start, end);
      const minOk = Math.floor(expected * (1 - tolerancePct));
      const warnMin = Math.floor(expected * warnAtPct);

      if(now > endDt){
        if(actual < minOk){
          status = "ERROR";
          message = `Intervall ${start}-${end}: saknar ${minOk - actual}`;
        }
      }else if(now >= startDt && now <= endDt){
        if(status !== "ERROR" && actual < warnMin){
          status = "WARNING";
          message = `Intervall ${start}-${end}: ligger efter`;
        }
      }
    }

    return { status, message, details };
  }

  function evalExactTimesFlow(nf, fid, today){
    let worstRank = 1;
    let message = "";

    for(const tm of (nf.times || [])){
      const exact = String((tm && tm.time) || "");
      const expected = Number((tm && tm.expected) || 0);
      if(!exact || !expected) continue;

      const due = parseTodayAt(exact);
      if(!due) continue;

      const warningLead = Number(
        nf.warningLeadMinutes != null ? nf.warningLeadMinutes : warningLeadDefault
      );
      const warnAt = new Date(due.getTime() - warningLead * 60000);
      const actual = countFinishedUntilToday(fid, exact);

      let rank = 1;
      let msg = "";

      if(now >= due && actual < expected){
        rank = 3;
        msg = `Tid ${exact}: saknar ${expected - actual}`;
      }else if(now >= warnAt && now < due && actual < expected){
        rank = 2;
        msg = `Tid ${exact}: väntar på ${expected - actual}`;
      }

      if(rank > worstRank){
        worstRank = rank;
        message = msg;
      }
    }

    return {
      status: worstRank === 3 ? "ERROR" : (worstRank === 2 ? "WARNING" : "INFO"),
      message,
      details: String(today) + " idag"
    };
  }

  function evalDateLikeFlow(nf, fid, today){
    let active = false;

    if(nf.mode === "weekdays"){
      active = (nf.weekdays || []).map(Number).includes(todayDow);
    }else if(nf.mode === "monthDays"){
      active = (nf.monthDays || []).map(Number).includes(now.getDate());
    }else if(nf.mode === "dates"){
      active = (nf.dates || []).map(String).includes(todayStr);
    }

    if(!active){
      return {
        status: "INFO",
        message: "",
        details: String(today) + " idag"
      };
    }

    const expected = Number(nf.expected || 0);
    const dueTime = String(nf.dueTime || businessEndDefault || "17:00");
    const due = parseTodayAt(dueTime);
    const warningLead = Number(
      nf.warningLeadMinutes != null ? nf.warningLeadMinutes : warningLeadDefault
    );
    const grace = Number(
      nf.errorGraceMinutes != null ? nf.errorGraceMinutes : errorGraceDefault
    );

    if(!due || !expected){
      return {
        status: "INFO",
        message: "",
        details: String(today) + " idag"
      };
    }

    const warnAt = new Date(due.getTime() - warningLead * 60000);
    const errAt = new Date(due.getTime() + grace * 60000);

    let status = "INFO";
    let message = "";

    if(now >= errAt && today < expected){
      status = "ERROR";
      message = `Förväntad leverans ${todayStr}: saknar ${expected - today}`;
    }else if(now >= warnAt && now < errAt && today < expected){
      status = "WARNING";
      message = `Förväntad leverans ${todayStr}: väntar på ${expected - today}`;
    }

    return {
      status,
      message,
      details: String(today) + " idag"
    };
  }

  const rows = (flows || []).map(f => {
    const nf = normalizeFlow(f || {});
    const fid = parseFlowIdParts(nf.flowId);
    const today = countFinishedToday(fid);
    const last = lastFinishedCompleted(fid);

    let evalRes = { status:"INFO", message:"", details:String(today) + " idag" };

    if(nf.mode === "interval" || nf.mode === "intervals"){
      evalRes = evalIntervalFlow(nf, fid, today);
    }else if(nf.mode === "exactTimes"){
      evalRes = evalExactTimesFlow(nf, fid, today);
    }else if(nf.mode === "weekdays" || nf.mode === "monthDays" || nf.mode === "dates"){
      evalRes = evalDateLikeFlow(nf, fid, today);
    }

    if(last){
      evalRes.details += `, Senast ${fmtHHmmss(last)}`;
    }

    return {
      flowId: nf.flowId,
      sender: fid.sender,
      receiver: fid.receiver,
      msgType: fid.msgType,
      name: nf.name || nf.flowId.replace(/\|\|/g, "-"),
      enabled: nf.enabled,
      mode: nf.mode,
      status: evalRes.status,
      today: today,
      message: evalRes.message,
      details: evalRes.details,
      warningLeadMinutes: nf.warningLeadMinutes,
      errorGraceMinutes: nf.errorGraceMinutes,
      intervals: nf.intervals || [],
      times: nf.times || [],
      weekdays: nf.weekdays || [],
      monthDays: nf.monthDays || [],
      dates: nf.dates || [],
      dueTime: nf.dueTime || "",
      carryOverMode: nf.carryOverMode || ""
    };
  });

  const counters = {
    error: rows.filter(r => r.status === "ERROR").length,
    warning: rows.filter(r => r.status === "WARNING").length,
    info: rows.filter(r => r.status === "INFO").length
  };

  return {
    version: cfg.version,
    defaults: cfg.defaults || {},
    counters,
    rows
  };
}

const fs = require("fs");

const app = express();
const PORT = process.env.PORT || 8080;

const WEBROOT = path.resolve(__dirname, "..", "FlowMonitorWeb", "WebContent");
const MON_CFG = path.resolve(WEBROOT, "assets", "monitoring-config.json");

// --- helpers ---
function logReq(req){
  try{
    console.log("[api:req]", req.method, req.url, {
      ip: req.ip,
      fwd: req.headers["x-forwarded-for"],
      ua: req.headers["user-agent"],
      ref: req.headers["referer"],
    });
  }catch(e){}
}

function safeReadJson(p){
  try{
    const txt = fs.readFileSync(p, "utf8");
    return JSON.parse(txt);
  }catch(e){
    return null;
  }
}

function parseFlowId(flowId){
  const s = String(flowId || "");
  const parts = s.split("||");
  return {
    sender: parts[0] || "",
    receiver: parts[1] || "",
    msgType: parts.slice(2).join("||") || ""
  };
}

function todayISO(){
  const d = new Date();
  const mm = String(d.getMonth()+1).padStart(2,"0");
  const dd = String(d.getDate()).padStart(2,"0");
  return `${d.getFullYear()}-${mm}-${dd}`;
}

function getRange(req){
  const qFrom = (req.query && req.query.from) ? String(req.query.from) : null;
  const qTo   = (req.query && req.query.to)   ? String(req.query.to)   : null;
  const t = todayISO();
  const from = qFrom || t;
  const to   = qTo   || t;
  return { from, to };
}

// small stable hash
function hash32(str){
  let h = 2166136261 >>> 0;
  for(let i=0;i<str.length;i++){
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619) >>> 0;
  }
  return h >>> 0;
}

function loadMonitoringConfig(){
  const cfg = safeReadJson(MON_CFG);
  const flows = (cfg && Array.isArray(cfg.flows)) ? cfg.flows : [];
  return { cfg: cfg || {}, flows };
}

// Build "range rows" (structure-correct: sender/receiver/msgType)
// Counts are deterministic per (flowId + range)
function parseDateOnly(s){
  const m = String(s || '').match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if(!m) return null;
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 0, 0, 0, 0);
}

function fmtDateOnly(d){
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function buildRows(range){
  const { flows } = loadMonitoringConfig();

  if(!flows.length) return [];

  const fromD = parseDateOnly(range.from);
  const toD = parseDateOnly(range.to);
  if(!fromD || !toD) return [];

  return flows.map((f, idx) => {
    const id = f.flowId || "";
    const parsed = parseFlowId(id);

    let count = 0;
    for(let d = new Date(fromD.getTime()); d <= toD; d.setDate(d.getDate() + 1)){
      const dayKey = fmtDateOnly(d);
      // stable per flow + day, additive over selected period
      const daily = (hash32(`${id}|${dayKey}|${idx}`) % 10) + 1; // 1..10 per day
      count += daily;
    }

    return {
      sender: parsed.sender || "Unknown",
      receiver: parsed.receiver || "Unknown",
      msgType: parsed.msgType || "Unknown",
      count
    };
  });
}

function withState(rows, state){
  return (rows||[]).map(r => ({...r, state}));
}

// Split "today live" into running/stopped/finished deterministically
function splitLive(rows, range){
  const running=[], stopped=[], finished=[];
  for(const r of rows){
    const k = `${r.sender}|${r.receiver}|${r.msgType}|${range.from}..${range.to}`;
    const h = hash32(k) % 100;
    if(h < 15) stopped.push(r);
    else if(h < 35) finished.push(r);
    else running.push(r);
  }
  return { running, stopped, finished };
}

// --- static ---
app.use(express.static(WEBROOT));

// --- api ---
app.get("/api/health", (req,res)=>{
  logReq(req);
  res.json({ ok: true });
});

app.get("/api/monitoring-config", (req,res)=>{
  logReq(req);
  try{
    const txt = fs.readFileSync(MON_CFG, "utf8");
    res.type("application/json").send(txt && txt.trim() ? txt : '{"defaults":{},"flows":[]}');
  }catch(e){
    res.status(500).json({ ok:false, error:"monitoring-config failed" });
  }
});

app.post("/api/monitoring-config", express.text({ type:"*/*", limit:"1mb" }), (req,res)=>{
  logReq(req);
  try{
    let body = String(req.body || "").trim();
    if(!body) body = '{"defaults":{},"flows":[]}';

    if(!(body.startsWith("{") && body.endsWith("}"))){
      return res.status(400).json({ ok:false, error:"Body must be a JSON object" });
    }

    JSON.parse(body); // validate JSON before write
    fs.writeFileSync(MON_CFG, body + "\n", "utf8");
    res.json({ ok:true });
  }catch(e){
    res.status(500).json({ ok:false, error:"save failed: " + (e && e.message ? e.message : e) });
  }
});

// Monitoring from JSON config
app.get("/api/monitoring", (req,res)=>{
  logReq(req);

  const eventMock = mockMonitoringRows();
  if(eventMock){ return res.json(eventMock); }

  const mock = pickMockSection("monitoring");
  if(mock){ return res.json(mock); }

  const { cfg, flows } = loadMonitoringConfig();

  // Use selected range when provided; otherwise default to today.
  // This makes monitoring and flow table comparable in mock GUI tests.
  const selected = getRange(req);
  const rangeRows = buildRows(selected);

  function toId(r){
    return String(r.sender || "") + "||" + String(r.receiver || "") + "||" + String(r.msgType || "");
  }

  const rangeMap = new Map(rangeRows.map(r => [toId(r), r]));

  const rows = flows.map(f => {
    const parsed = parseFlowId(f.flowId);
    const fid = String(f.flowId || "");
    const hit = rangeMap.get(fid);

    const todayCount = hit ? Number(hit.count || 0) : 0;

    let status = "INFO";
    let message = "";
    let details = "";

    if (todayCount <= 0) {
      status = "ERROR";
      message = "Inga flöden idag";
      details = "0 idag";
    } else if (todayCount < 10) {
      status = "WARNING";
      message = "Låg volym idag";
      details = String(todayCount) + " idag";
    } else {
      status = "INFO";
      message = "";
      details = String(todayCount) + " idag";
    }

    return {
      flowId: fid,
      sender: parsed.sender,
      receiver: parsed.receiver,
      msgType: parsed.msgType,
      name: f.name,
      enabled: !!f.enabled,
      mode: f.mode,
      status: status,
      today: todayCount,
      message: message,
      details: details,
      warningLeadMinutes: f.warningLeadMinutes,
      intervals: f.intervals || [],
      times: f.times || []
    };
  });

  const counters = {
    error: rows.filter(r => r.status === "ERROR").length,
    warning: rows.filter(r => r.status === "WARNING").length,
    info: rows.filter(r => r.status === "INFO").length
  };

  res.json({
    version: cfg.version,
    defaults: cfg.defaults || {},
    counters: counters,
    rows: rows
  });
});

// Range (history view)
app.get("/api/range", (req,res)=>{
  logReq(req);
  const range = getRange(req);

  const eventMock = mockRangeRows(range);
  if(eventMock){ return res.json(eventMock); }

  const mock = pickMockRange(range);
  if(mock){ return res.json(mock); }

  const rows = buildRows(range);
  res.json({ rows: rows });
});

// Live endpoints (today)
app.get("/api/running", (req,res)=>{
  logReq(req);

  const eventMock = mockTodayLiveRows("running");
  if(eventMock){ return res.json(eventMock); }

  const mock = pickMockSection("running");
  if(mock){ return res.json(mock); }

  const range = getRange(req);
  const rows = buildRows(range);
  const { running } = splitLive(rows, range);
  res.json({ rows: withState(running, "running") });
});

app.get("/api/stopped", (req,res)=>{
  logReq(req);

  const eventMock = mockTodayLiveRows("stopped");
  if(eventMock){ return res.json(eventMock); }

  const mock = pickMockSection("stopped");
  if(mock){ return res.json(mock); }

  const range = getRange(req);
  const rows = buildRows(range);
  const { stopped } = splitLive(rows, range);
  res.json({ rows: withState(stopped, "stopped") });
});

app.get("/api/finished", (req,res)=>{
  logReq(req);

  const eventMock = mockTodayLiveRows("finished");
  if(eventMock){ return res.json(eventMock); }

  const mock = pickMockSection("finished");
  if(mock){ return res.json(mock); }

  const range = getRange(req);
  const rows = buildRows(range);
  const { finished } = splitLive(rows, range);
  res.json({ rows: withState(finished, "finished") });
});

app.listen(PORT, ()=>{
  console.log("Dev server listening on", PORT);
  console.log("Serving", WEBROOT);
  console.log("Monitoring config", MON_CFG);
});
