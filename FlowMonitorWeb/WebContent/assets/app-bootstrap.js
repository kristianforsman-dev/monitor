
(function(w){
  'use strict';
  var FM = w.FM;

  // ---- smart: deep link hash (q/range/filters/sort) ----
  (function(){
    try{
      function getHashParams(){
        var h=(location.hash||'').replace(/^#/,'');
        return new URLSearchParams(h);
      }
      function applyFromHash(){
        var p=getHashParams();
        if(!p.has('q') && !p.has('range') && !p.has('filters') && !p.has('sort')) return;

        try{
          var qv=p.get('q');
          if(qv!=null){
            var qEl=document.getElementById('q');
            if(qEl) qEl.value=qv;
            FM.state.query=(qv||'').trim().toLowerCase();
          }
        }catch(e){}

        try{ var range=p.get('range'); if(range) localStorage.setItem(FM.STORAGE_RANGE, range); }catch(e){}
        try{ var filters=p.get('filters'); if(filters) localStorage.setItem(FM.STORAGE_FILTERS, decodeURIComponent(filters)); }catch(e){}
        try{ var sort=p.get('sort'); if(sort) localStorage.setItem(FM.STORAGE_SORT, decodeURIComponent(sort)); }catch(e){}
      }

      var __lastHash=null;
      function syncHash(){
        try{
          var qEl=document.getElementById('q');
          var qv=qEl?(qEl.value||''):'';
          var range=localStorage.getItem(FM.STORAGE_RANGE)||'';
          var filters=localStorage.getItem(FM.STORAGE_FILTERS)||'';
          var sort=localStorage.getItem(FM.STORAGE_SORT)||'';

          var p=new URLSearchParams();
          if(qv) p.set('q', qv);
          if(range) p.set('range', range);
          if(filters) p.set('filters', encodeURIComponent(filters));
          if(sort) p.set('sort', encodeURIComponent(sort));

          var h=p.toString();
          if(h===__lastHash) return;
          __lastHash=h;

          if(history && history.replaceState){
            history.replaceState(null, document.title, location.pathname + location.search + (h ? '#'+h : ''));
          }else{
            location.hash = h ? '#'+h : '';
          }
        }catch(e){}
      }

      applyFromHash();
      setInterval(syncHash, 1000);
    }catch(e){}
  })();

  // ---- range selection ----
  function todayISO(){
    // browser-local is ok, server validates anyway
    var d=new Date();
    var mm=String(d.getMonth()+1).padStart(2,'0');
    var dd=String(d.getDate()).padStart(2,'0');
    return d.getFullYear()+'-'+mm+'-'+dd;
  }

  function readSelectedRange(){
    var raw = localStorage.getItem(FM.STORAGE_RANGE);
    var r = FM.parseRange(raw);
    if(r && r.from && r.to) return r;
    // default 7d back
    var to = new Date();
    var from = new Date(to.getTime() - 6*24*60*60*1000);
    function iso(x){
      var mm=String(x.getMonth()+1).padStart(2,'0');
      var dd=String(x.getDate()).padStart(2,'0');
      return x.getFullYear()+'-'+mm+'-'+dd;
    }
    return {from: iso(from), to: iso(to)};
  }

  function writeSelectedRange(from,to){
    localStorage.setItem(FM.STORAGE_RANGE, from + '..' + to);
  }

  function isTodayRange(r){
    if(!r) return true;
    var t=todayISO();
    return r.from===t && r.to===t;
  }

  // Debounce (protect DB)
  var __rangeDebounceT = null;
    var FM_DEBUG = false;
  try{ FM_DEBUG = (location.search||'').indexOf('debug=1') !== -1; }catch(e){}
  function debugSet(text){
    if(!FM_DEBUG) return;
    try{
      var el = document.getElementById('rangeDebug');
      if(!el){
        el = document.createElement('div');
        el.id='rangeDebug';
        el.style.fontSize='12px';
        el.style.opacity='0.75';
        el.style.marginTop='6px';
        var host = document.getElementById('lmInfo') ? document.getElementById('lmInfo').parentNode : null;
        if(host) host.appendChild(el);
      }
      if(el) el.textContent = text || '';
    }catch(e){}
  }

function scheduleRangeRefresh(){
    try{ if(__rangeDebounceT) clearTimeout(__rangeDebounceT); }catch(e){}
    __rangeDebounceT = setTimeout(function(){
      refreshNow();
    }, 300);
  }

  // Update UI inputs/buttons (if present)
  function updateRangeUI(){
    var r = readSelectedRange();
    var fromEl=document.getElementById('dateFrom');
    var toEl=document.getElementById('dateTo');
    if(fromEl) fromEl.value=r.from;
    if(toEl) toEl.value=r.to;

    // Determine active preset by actual span (so it stays marked even after reload)
    var active = w.__activePreset;
    try{
      if(!active){
        var f = new Date(r.from + 'T00:00:00');
        var t = new Date(r.to + 'T00:00:00');
        var days = Math.round((t.getTime()-f.getTime())/(24*60*60*1000)) + 1;
        if(days===1) active='1d';
        else if(days===3) active='3d';
        else if(days===7) active='7d';
        else if(days===30) active='30d';
      }
    }catch(e){}

    var btns=document.querySelectorAll('button.preset[data-range], [data-range].preset, [data-range]');
    for(var i=0;i<btns.length;i++){
      var p=btns[i].getAttribute('data-range') || btns[i].getAttribute('data-preset');
      // only mark known presets
      if(p==='1d'||p==='3d'||p==='7d'||p==='30d'){
        btns[i].classList.toggle('active', !!active && p===active);
      }
    }
  }

  // Expose for existing HTML onclicks (if any)
  w.fmPreset = function(preset){
    try{
      var to=new Date();
      var from=new Date(to.getTime());
      if(preset==='1d') from = new Date(to.getTime());
      else if(preset==='3d') from = new Date(to.getTime() - 2*24*60*60*1000);
      else if(preset==='7d') from = new Date(to.getTime() - 6*24*60*60*1000);
      else if(preset==='30d') from = new Date(to.getTime() - 29*24*60*60*1000);

      function iso(x){
        var mm=String(x.getMonth()+1).padStart(2,'0');
        var dd=String(x.getDate()).padStart(2,'0');
        return x.getFullYear()+'-'+mm+'-'+dd;
      }
      var f=iso(from), t=iso(to);
      w.__activePreset=preset;
      writeSelectedRange(f,t);
      updateRangeUI();
      FM.state.visibleCount = FM.state.pageSize;
      refreshNow();
    }catch(e){}
    return false;
  };

  w.fmDateChanged = function(){
    try{
      var fromEl=document.getElementById('dateFrom');
      var toEl=document.getElementById('dateTo');
      if(!fromEl || !toEl) return false;
      var f=fromEl.value, t=toEl.value;
      if(!f || !t) return false;
      w.__activePreset=null;
      writeSelectedRange(f,t);
      updateRangeUI();
      FM.state.visibleCount = FM.state.pageSize;
      scheduleRangeRefresh();
    }catch(e){}
    return false;
  };

  // ---- refresh logic (live + range) ----
  // Prevent out-of-order async responses from overwriting newer data
  var __refreshSeq = 0;

  
    var __refreshAbort = null;
async function pollHealth(){
    try{
      var h = await fetch('api/health', { cache:'no-store' });
      FM.setLiveStatus(!!(h && h.ok), (h && h.ok) ? 'Live' : 'Offline');
    }catch(e){
      FM.setLiveStatus(false,'Offline');
    }
  }

  async function fetchRangeView(rSel, signal){
    // /api/range?from=YYYY-MM-DD&to=YYYY-MM-DD
    var ep='api/range?from='+encodeURIComponent(rSel.from)+'&to='+encodeURIComponent(rSel.to);
    return await FM.fetchRows(ep, { signal: signal });
  }

  async function refreshNow(){
      // Abort any in-flight refresh so old responses can't land after a click
      try{ if(__refreshAbort) __refreshAbort.abort(); }catch(e){}
      __refreshAbort = (w.AbortController ? new AbortController() : null);
      var signal = __refreshAbort ? __refreshAbort.signal : undefined;

      var seq = ++__refreshSeq;
      var tbody=document.getElementById('statusBody');
      if(tbody && (!FM.state.data || !FM.state.data.length)){
        tbody.innerHTML='<tr><td colspan="5" class="placeholder">Loading...</td></tr>';
      }

      // health should never break table
      pollHealth();

      try{
        var rSel = readSelectedRange();
        debugSet('Range: '+rSel.from+'..'+rSel.to+' ('+(isTodayRange(rSel)?'live':'range')+') ...');

        var view;
        if(!isTodayRange(rSel)){
          view = await fetchRangeView(rSel, signal);
        }else{
          // Always include range params (today) so backend logs & behavior are consistent
          var qp='?from='+encodeURIComponent(rSel.from)+'&to='+encodeURIComponent(rSel.to);

          var parts = await Promise.all([
            FM.fetchRows('api/running'+qp,  { signal: signal }),
            FM.fetchRows('api/stopped'+qp,  { signal: signal }),
            FM.fetchRows('api/finished'+qp, { signal: signal })
          ]);

          var running=parts[0]||[], stopped=parts[1]||[], finished=parts[2]||[];
          var keyFn = FM.rowKeyBase || FM.rowKey;

          var stopSet = new Set(stopped.map(keyFn));
          var runOnly = running.filter(function(r){ return !stopSet.has(keyFn(r)); });

          view = runOnly.concat(finished, stopped);
        }

        if(seq !== __refreshSeq) return;

        debugSet('Range: '+rSel.from+'..'+rSel.to+' ('+(isTodayRange(rSel)?'live':'range')+'), rows='+(view?view.length:0));
        FM.Table.setData(view);

        if(FM.Monitor && FM.Monitor.fetch){
          try{
            var md = await FM.Monitor.fetch();
            if(md){
              FM.Monitor.state.data = md;
              if(FM.Monitor.render) FM.Monitor.render();
            }
          }catch(ignore){}
        }
      }catch(e){
        if(e && (e.name === 'AbortError')) return;
        try{ console.error('refresh failed', e); }catch(ignore){}
      }
}


    // ---- init wiring ----
  function init(){
    // query input
    var q=document.getElementById('q');
    if(q && !q.__wired){
      q.__wired=true;
      q.addEventListener('input', function(){
        FM.state.query=(q.value||'').trim().toLowerCase();
        FM.state.visibleCount = FM.state.pageSize;
        FM.scheduleRender();
      });
    }

    // clear button
    var btnClearAll=document.getElementById('btnClearAll');
    if(btnClearAll && !btnClearAll.__wired){
      btnClearAll.__wired=true;
      btnClearAll.addEventListener('click', function(){
        if(FM.Filters) FM.Filters.clearAll();
        // also reset range to today? no: keep range, only clear filters/sök.
        FM.state.visibleCount = FM.state.pageSize;
        FM.scheduleRender();
      });
    }

    // date inputs
    var fromEl=document.getElementById('dateFrom');
    var toEl=document.getElementById('dateTo');
    if(fromEl && !fromEl.__wired){ fromEl.__wired=true; fromEl.addEventListener('change', w.fmDateChanged); }
    if(toEl && !toEl.__wired){ toEl.__wired=true; toEl.addEventListener('change', w.fmDateChanged); }

    // presets by data-preset (works without onclick)
    var btns=document.querySelectorAll('button.preset[data-range], [data-range].preset, [data-range]');
    for(var i=0;i<btns.length;i++){
      (function(b){
        if(b.__wired) return;

          // If HTML already has inline onclick (e.g. onclick="fmPreset('7d')"),
          // do NOT wire a second handler (causes double refresh / "two clicks").
          if(b.getAttribute('onclick') || typeof b.onclick === 'function'){
            b.__wired = true;
            return;
          }

          b.__wired=true;
          b.addEventListener('click', function(e){
            e.preventDefault();
            e.stopPropagation();
            var p=b.getAttribute('data-range') || b.getAttribute('data-preset');
            w.fmPreset(p);
          });
      })(btns[i]);
    }

    // modules init
    if(FM.Filters && FM.Filters.init) FM.Filters.init();
    if(FM.Table && FM.Table.init) FM.Table.init();
    if(FM.Monitor && FM.Monitor.init) FM.Monitor.init();

    updateRangeUI();
    refreshNow();
    setInterval(refreshNow, 30000);
    setInterval(pollHealth, 10000);
  }

  init();

})(window);

