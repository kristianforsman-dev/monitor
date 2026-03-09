
(function(w){
  'use strict';

  // Global namespace for modules
  var FM = w.FM = w.FM || {};

  // ---- constants / storage ----
  FM.STORAGE_SORT    = 'flowmonitor.sort';
  FM.STORAGE_FILTERS = 'flowmonitor.filters';
  FM.STORAGE_RANGE   = 'flowmonitor.range';

  FM.PAGE_SIZE_DEFAULT = 5;

  // ---- shared state ----
  FM.state = {
    query: '',
    sort: null,
    filters: null,
    range: null,
    visibleCount: FM.PAGE_SIZE_DEFAULT,
    pageSize: FM.PAGE_SIZE_DEFAULT,
    data: [],
    prevById: Object.create(null)
  };

  // ---- dom refs (filled in bootstrap) ----
  FM.dom = {};

  // ---- helpers ----
  // Base key (no state): used when we intentionally want to de-dup across states (e.g. stopped vs running)
  FM.rowKeyBase = function(r){
    return [r.sender, r.receiver, r.msgType].map(function(v){ return String(v||''); }).join('|');
  };

  // Row id (includes state): ensures Running/Finished/Stopped rows don't collide in the table
  FM.rowKey = function(r){
    return [r.sender, r.receiver, r.msgType, r.state].map(function(v){ return String(v||''); }).join('|');
  };

  FM.rows = function(raw){
    return Array.isArray(raw) ? raw : (raw && Array.isArray(raw.rows) ? raw.rows : []);
  };

  FM.loadJSON = function(k){
    try{ return JSON.parse(localStorage.getItem(k)); }catch(e){ return null; }
  };

  FM.saveJSON = function(k,v){
    try{ localStorage.setItem(k, JSON.stringify(v)); }catch(e){}
  };

  FM.escapeHtml = function(str){
    str = (str==null) ? '' : String(str);
    return str.replace(/[&<>"']/g,function(m){
      return m==='&'?'&amp;':m==='<'?'&lt;':m==='>'?'&gt;':m==='"'?'&quot;':'&#39;';
    });
  };

  FM.normalizeState = function(s){ return (s||'').toString().toLowerCase(); };

  // GUI-only translation: SAP-ECC -> PRIO
  FM.guiText = function(s){
    s = (s==null) ? '' : String(s);
    return s === 'SAP-ECC' ? 'PRIO' : s;
  };

  // Apply guiText to row fields for display only
  FM.displayRow = function(r){
    return {
      sender: FM.guiText(r.sender),
      receiver: FM.guiText(r.receiver),
      msgType: r.msgType,
      state: r.state,
      count: r.count
    };
  };

  FM.compare = function(a,b){
    var st = FM.state.sort || {key:'sender',dir:'asc'};
    var key=st.key, dir=(st.dir==='asc')?1:-1;
    var av=a[key], bv=b[key];
    if(key==='count'){ av=Number(av||0); bv=Number(bv||0); return (av-bv)*dir; }
    av=(av==null?'':String(av)).toLowerCase();
    bv=(bv==null?'':String(bv)).toLowerCase();
    if(av<bv) return -1*dir;
    if(av>bv) return 1*dir;
    return 0;
  };

  // network helper
  FM.fetchRows = async function(endpoint, opts){
    try{
      var fetchOpts = { cache: 'no-store' };
      if(opts){
        for(var k in opts){
          if(Object.prototype.hasOwnProperty.call(opts, k)){
            fetchOpts[k] = opts[k];
          }
        }
      }
      var res = await fetch(endpoint, fetchOpts);
      if(!res.ok) return [];
      var data = await res.json();
      return FM.rows(data);
    }catch(e){
      if(e && e.name === 'AbortError') throw e;
      return [];
    }
  };

  // live indicator (safe)
  FM.setLiveStatus = function(ok, msg){
    try{
      document.documentElement.classList.toggle('status-ok', !!ok);
      document.documentElement.classList.toggle('status-bad', !ok);
      var el = document.getElementById('liveStatus');
      if(el) el.textContent = msg || (ok ? 'Live' : 'Offline');
      var dot = document.getElementById('liveDot');
      if(dot) dot.setAttribute('data-ok', ok ? '1' : '0');
    }catch(e){}
  };

  // requestAnimationFrame scheduler for render
  FM.__renderPending = false;
  FM.scheduleRender = function(){
    if(FM.__renderPending) return;
    FM.__renderPending = true;
    (w.requestAnimationFrame||function(fn){ return setTimeout(fn,0); })(function(){
      FM.__renderPending = false;
      if(FM.Table && FM.Table.render) FM.Table.render();
    });
  };

  // Range parse/store
  FM.parseRange = function(str){
    if(!str) return null;
    var m = String(str).split('..');
    if(m.length!==2) return null;
    return {from:m[0], to:m[1]};
  };
  FM.rangeToString = function(r){
    if(!r || !r.from || !r.to) return '';
    return r.from + '..' + r.to;
  };

})(window);
