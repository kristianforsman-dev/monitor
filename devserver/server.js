/* devserver/server.js - deterministic mock dev server for FlowMonitorWeb */
const express = require("express");
const path = require("path");
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

// Monitoring from JSON config
app.get("/api/monitoring", (req,res)=>{
  logReq(req);
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
  const rows = buildRows(range);
  res.json({ rows: rows });
});

// Live endpoints (today)
app.get("/api/running", (req,res)=>{
  logReq(req);
  const range = getRange(req);
  const rows = buildRows(range);
  const { running } = splitLive(rows, range);
  res.json({ rows: withState(running, "running") });
});

app.get("/api/stopped", (req,res)=>{
  logReq(req);
  const range = getRange(req);
  const rows = buildRows(range);
  const { stopped } = splitLive(rows, range);
  res.json({ rows: withState(stopped, "stopped") });
});

app.get("/api/finished", (req,res)=>{
  logReq(req);
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
