
(function(w){
  'use strict';
  var FM = w.FM;
  if(!FM) return;

  var Monitor = FM.Monitor = FM.Monitor || {};
  Monitor.state = Monitor.state || { onlyIssues:false, data:null, open:true };

    // Icons are optional; never let missing icons break rendering
    var MonitorIcons = w.MonitorIcons || (w.MonitorIcons = {
      link: '<svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M10 14a4 4 0 0 1 0-8h4a4 4 0 1 1 0 8h-1" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M14 10a4 4 0 0 1 0 8H10a4 4 0 1 1 0-8h1" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
      edit: '<svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M12 20h9" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>'
    });


  var LS_MON_OPEN = 'flowmonitor.monOpen';

  function loadOpen(){
    try{
      var v = localStorage.getItem(LS_MON_OPEN);
      if(v === null || v === undefined) return true; // default open
      return v === '1';
    }catch(e){ return true; }
  }
  function saveOpen(open){
    try{ localStorage.setItem(LS_MON_OPEN, open ? '1' : '0'); }catch(e){}
  }

  function setOpen(open){
    Monitor.state.open = !!open;
    var btn = document.getElementById('monToggle');
    var wrap = document.getElementById('monListWrap');
    if(btn) btn.setAttribute('aria-expanded', open ? 'true' : 'false');
    if(wrap) wrap.style.display = open ? '' : 'none';
    saveOpen(open);
  }

  function badgeClass(status){
    status = String(status||'').toUpperCase();
    if(status === 'ERROR') return 'err';
    if(status === 'WARNING') return 'war';
    return 'inf';
  }
  function rank(status){
    status = String(status||'').toUpperCase();
    if(status === 'ERROR') return 3;
    if(status === 'WARNING') return 2;
    return 1;
  }

  function applyFlowIdAsFilters(flowId){
    // flowId format: sender||receiver||msgType (backend/config)
    try{
      if(!flowId) return;
      var parts = String(flowId).split('||');
      if(parts.length < 3) return;

      var sender = parts[0] || '';
      var receiver = parts[1] || '';
      var msgType = parts[2] || '';

      var f = { sender:[], receiver:[], msgType:[], state:[] };
      if(sender) f.sender.push(sender);
      if(receiver) f.receiver.push(receiver);
      if(msgType) f.msgType.push(msgType);

      FM.state.filters = f;
      FM.saveJSON(FM.STORAGE_FILTERS, f);

      // Reset pagination so results are visible immediately
      FM.state.visibleCount = FM.state.pageSize;

      // Rebuild dropdowns + chips using current table dataset (no DB call)
      if(FM.Filters && FM.Filters.syncDropdownOptions){
        FM.Filters.syncDropdownOptions(FM.state.data || []);
      }else{
        if(FM.Filters && FM.Filters.updateChipRow) FM.Filters.updateChipRow();
        if(FM.Filters && FM.Filters.updateDropdownBadges) FM.Filters.updateDropdownBadges();
      }

      FM.scheduleRender();

      // Small UX hint: close dropdowns if open
      try{
        var open = document.querySelectorAll('.dd.open');
        for(var i=0;i<open.length;i++) open[i].classList.remove('open');
      }catch(e){}
    }catch(e){}
  }
  function ensureHint(){ /* hint removed */ }

  Monitor.init = function(){
    // Collapsible panel
    var toggle = document.getElementById('monToggle');
    if(toggle && !toggle.__wired){
      toggle.__wired = true;
      toggle.addEventListener('click', function(){
        setOpen(!Monitor.state.open);
      });
    }
    setOpen(loadOpen());
    ensureHint();
    if(Monitor.ensureCreateButton) Monitor.ensureCreateButton();

    // Issues/all buttons
    var bAll = document.getElementById('monShowAll');
    var bIssues = document.getElementById('monShowIssues');

    if(bAll && !bAll.__wired){
      bAll.__wired=true;
      bAll.addEventListener('click', function(){
        Monitor.state.onlyIssues=false;
        try{ bAll.classList.add('active'); }catch(e){}
        try{ if(bIssues) bIssues.classList.remove('active'); }catch(e){}
        Monitor.render();
      });
    }
    if(bIssues && !bIssues.__wired){
      bIssues.__wired=true;
      bIssues.addEventListener('click', function(){
        Monitor.state.onlyIssues=true;
        try{ bIssues.classList.add('active'); }catch(e){}
        try{ if(bAll) bAll.classList.remove('active'); }catch(e){}
        Monitor.render();
      });
    }

    // Click-to-filter (event delegation)
    var list = document.getElementById('monList');
    if(list && !list.__wired){
      list.__wired = true;
      list.addEventListener('click', function(e){
        try{
          var t = e.target;

          // If user clicked the explicit link/button
          if(t && t.classList && t.classList.contains('monLink')){
            var fid = t.getAttribute('data-flowid');
            if(fid) applyFlowIdAsFilters(fid);
            return;
          }

          // Else: click anywhere on the row
          while(t && t !== list){
            if(t.classList && t.classList.contains('monItem')){
              var fid2 = t.getAttribute('data-flowid');
              if(fid2) applyFlowIdAsFilters(fid2);
              return;
            }
            t = t.parentNode;
          }
        }catch(ignore){}
      });
    }
  };

  Monitor.fetch = async function(){
    try{
      var from = '';
      var to = '';
      try{
        var raw = localStorage.getItem(FM.STORAGE_RANGE) || '';
        var r = FM.parseRange ? FM.parseRange(raw) : null;
        if(r && r.from && r.to){
          from = r.from;
          to = r.to;
        }
      }catch(ignore){}

      var ep = 'api/monitoring';
      if(from && to){
        ep += '?from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(to);
      }

      var res = await fetch(ep, { cache:'no-store' });
      if(!res.ok) return null;
      return await res.json();
    }catch(e){
      return null;
    }
  };

    Monitor.loadConfig = async function(){
      var res = await fetch('api/monitoring-config', { cache:'no-store' });
      if(!res.ok) throw new Error('Kunde inte läsa monitoring-config');
      return await res.json();
    };

    Monitor.saveConfig = async function(cfg){
      var res = await fetch('api/monitoring-config', {
        method:'POST',
        headers:{ 'Content-Type':'application/json; charset=UTF-8' },
        body: JSON.stringify(cfg, null, 2)
      });
      var data = null;
      try{ data = await res.json(); }catch(ignore){}
      if(!res.ok || !data || data.ok === false){
        throw new Error((data && data.error) ? data.error : 'Kunde inte spara monitoring-config');
      }
      return data;
    };

    function editorGet(id){
      return document.getElementById(id);
    }

    function editorJsonArray(text, label){
      var val = (text == null ? '' : String(text)).trim();
      if(!val) return [];
      var out = JSON.parse(val);
      if(!Array.isArray(out)) throw new Error(label + ' måste vara en JSON-array');
      return out;
    }

    function editorRowBtn(label, cls){
      return '<button type="button" class="' + cls + '">' + label + '</button>';
    }

    function editorEscape(v){
      return String(v == null ? '' : v)
        .replace(/&/g,'&amp;')
        .replace(/</g,'&lt;')
        .replace(/>/g,'&gt;')
        .replace(/"/g,'&quot;');
    }

    function editorNormalizeFlow(flow){
      var x = {
        mode: 'interval',
        intervals: [],
        times: [],
        weekdays: [],
        monthDays: [],
        dates: [],
        expected: '',
        dueTime: '',
        carryOverMode: 'sameDay'
      };

      var sched = (flow && flow.schedule && typeof flow.schedule === 'object') ? flow.schedule : null;
      if(sched){
        var t = String(sched.type || '').trim();
        if(t === 'interval'){
          x.mode = 'interval';
          x.intervals = Array.isArray(sched.intervals) ? sched.intervals : [];
        }else if(t === 'exactTimes' || t === 'exacttimes'){
          x.mode = 'exactTimes';
          x.times = Array.isArray(sched.times) ? sched.times : [];
        }else if(t === 'weekdays'){
          x.mode = 'weekdays';
          x.weekdays = Array.isArray(sched.weekdays) ? sched.weekdays : [];
          x.expected = sched.expected == null ? '' : String(sched.expected);
          x.dueTime = String(sched.dueTime || '');
          x.carryOverMode = String(sched.carryOverMode || 'sameDay');
        }else if(t === 'monthDays' || t === 'monthdays'){
          x.mode = 'monthDays';
          x.monthDays = Array.isArray(sched.monthDays) ? sched.monthDays : [];
          x.expected = sched.expected == null ? '' : String(sched.expected);
          x.dueTime = String(sched.dueTime || '');
          x.carryOverMode = String(sched.carryOverMode || 'sameDay');
        }else if(t === 'dates'){
          x.mode = 'dates';
          x.dates = Array.isArray(sched.dates) ? sched.dates : [];
          x.expected = sched.expected == null ? '' : String(sched.expected);
          x.dueTime = String(sched.dueTime || '');
          x.carryOverMode = String(sched.carryOverMode || 'sameDay');
        }
        return x;
      }

      var legacyMode = String((flow && flow.mode) || 'intervals');
      if(legacyMode === 'exactTimes'){
        x.mode = 'exactTimes';
        x.times = Array.isArray(flow && flow.times) ? flow.times : [];
      }else{
        x.mode = 'interval';
        x.intervals = Array.isArray(flow && flow.intervals) ? flow.intervals : [];
      }
      return x;
    }

    function editorRenderIntervals(items){
      items = Array.isArray(items) ? items : [];
      var host = editorGet('monIntervalsGrid');
      if(!host) return;
      var html = '';
      for(var i=0;i<items.length;i++){
        var it = items[i] || {};
        html += ''
          + '<div class="monRuleRow" data-kind="interval">'
          + '  <input class="monIntStart" type="time" value="' + editorEscape(it.start || '') + '" />'
          + '  <input class="monIntEnd" type="time" value="' + editorEscape(it.end || '') + '" />'
          + '  <input class="monIntExpected" type="number" step="1" value="' + editorEscape(it.expected == null ? '' : it.expected) + '" />'
          + '  <input class="monIntWarnPct" type="number" step="0.01" value="' + editorEscape(it.warnAtPct == null ? '' : it.warnAtPct) + '" />'
          + '  <input class="monIntTolPct" type="number" step="0.01" value="' + editorEscape(it.tolerancePct == null ? '' : it.tolerancePct) + '" />'
          + '  ' + editorRowBtn('−', 'monRuleRemoveBtn')
          + '</div>';
      }
      host.innerHTML = html;
    }

    function editorCollectIntervals(){
      var host = editorGet('monIntervalsGrid');
      if(!host) return [];
      var rows = host.querySelectorAll('.monRuleRow[data-kind="interval"]');
      var out = [];
      for(var i=0;i<rows.length;i++){
        var r = rows[i];
        out.push({
          start: (r.querySelector('.monIntStart') || {}).value || '',
          end: (r.querySelector('.monIntEnd') || {}).value || '',
          expected: Number(((r.querySelector('.monIntExpected') || {}).value) || 0),
          warnAtPct: Number(((r.querySelector('.monIntWarnPct') || {}).value) || 0),
          tolerancePct: Number(((r.querySelector('.monIntTolPct') || {}).value) || 0)
        });
      }
      return out.filter(function(x){ return x.start && x.end && x.expected > 0; });
    }

    function editorRenderTimes(items){
      items = Array.isArray(items) ? items : [];
      var host = editorGet('monTimesGrid');
      if(!host) return;
      var html = '';
      for(var i=0;i<items.length;i++){
        var it = items[i] || {};
        html += ''
          + '<div class="monRuleRow" data-kind="time">'
          + '  <input class="monTimeAt" type="time" value="' + editorEscape(it.time || '') + '" />'
          + '  <input class="monTimeExpected" type="number" step="1" value="' + editorEscape(it.expected == null ? '' : it.expected) + '" />'
          + '  ' + editorRowBtn('−', 'monRuleRemoveBtn')
          + '</div>';
      }
      host.innerHTML = html;
    }

    function editorCollectTimes(){
      var host = editorGet('monTimesGrid');
      if(!host) return [];
      var rows = host.querySelectorAll('.monRuleRow[data-kind="time"]');
      var out = [];
      for(var i=0;i<rows.length;i++){
        var r = rows[i];
        out.push({
          time: (r.querySelector('.monTimeAt') || {}).value || '',
          expected: Number(((r.querySelector('.monTimeExpected') || {}).value) || 0)
        });
      }
      return out.filter(function(x){ return x.time && x.expected > 0; });
    }

    function editorAddIntervalRow(){
      var current = editorCollectIntervals();
      current.push({ start:'', end:'', expected:0, warnAtPct:0.7, tolerancePct:0.05 });
      editorRenderIntervals(current);
    }

    function editorAddTimeRow(){
      var current = editorCollectTimes();
      current.push({ time:'', expected:0 });
      editorRenderTimes(current);
    }

    function editorRemoveRuleRow(btn){
      var row = btn && btn.closest ? btn.closest('.monRuleRow') : null;
      if(row && row.parentNode) row.parentNode.removeChild(row);
    }

    Monitor.ensureEditorDom = function(){
      if(editorGet('monEditorBackdrop')) return;

      var root = document.createElement('div');
      root.innerHTML = ''
        + '<div id="monEditorBackdrop" class="monEditorBackdrop" hidden>'
        + '  <div class="monEditorShell">'
        + '    <div class="monEditorCard" role="dialog" aria-modal="true" aria-labelledby="monEditorTitle">'
        + '      <div class="monEditorHead">'
        + '        <div id="monEditorTitle" class="monEditorTitle">Redigera bevakning</div>'
        + '        <button type="button" id="monEditorClose" class="iconBtn monEditorCloseBtn" aria-label="Stäng">×</button>'
        + '      </div>'
        + '      <div class="monEditorBody">'
        + '        <label class="monField">'
        + '          <span>Avsändare</span>'
        + '          <input id="monEditSender" type="text" />'
        + '        </label>'
        + '        <label class="monField">'
        + '          <span>Mottagare</span>'
        + '          <input id="monEditReceiver" type="text" />'
        + '        </label>'
        + '        <label class="monField">'
        + '          <span>Meddelandetyp</span>'
        + '          <input id="monEditMsgType" type="text" />'
        + '        </label>'
        + '        <label class="monField">'
        + '          <span>Namn</span>'
        + '          <input id="monEditName" type="text" />'
        + '        </label>'
        + '        <label class="monField monFieldSwitch">'
        + '          <span>Aktiv bevakning</span>'
        + '          <span class="monSwitchWrap">'
        + '            <input id="monEditEnabled" class="monSwitchInput" type="checkbox" />'
        + '            <span class="monSwitch" aria-hidden="true"></span>'
        + '          </span>'
        + '        </label>'
        + '        <label class="monField">'
        + '          <span>Typ</span>'
        + '          <select id="monEditMode">'
        + '            <option value="interval">Intervall</option>'
        + '            <option value="exactTimes">Exakta tider</option>'
        + '            <option value="weekdays">Veckodagar</option>'
        + '            <option value="monthDays">Månadsdagar</option>'
        + '            <option value="dates">Datum</option>'
        + '          </select>'
        + '        </label>'
        + '        <label class="monField">'
        + '          <span>Varning före (min)</span>'
        + '          <input id="monEditWarnLead" type="number" step="1" />'
        + '        </label>'
        + '        <label class="monField">'
        + '          <span>Felmarginal efter (min)</span>'
        + '          <input id="monEditErrorGrace" type="number" step="1" />'
        + '        </label>'
        + '        <label id="monFieldExpected" class="monField">'
        + '          <span>Förväntat antal</span>'
        + '          <input id="monEditExpected" type="number" step="1" />'
        + '        </label>'
        + '        <label id="monFieldDueTime" class="monField">'
        + '          <span>Förfallotid</span>'
        + '          <input id="monEditDueTime" type="time" />'
        + '        </label>'
        + '        <label id="monFieldCarry" class="monField">'
        + '          <span>Hantering efter förfall</span>'
        + '          <select id="monEditCarryOver">'
        + '            <option value="sameDay">sameDay</option>'
        + '            <option value="nextBusinessDayMorning">nextBusinessDayMorning</option>'
        + '          </select>'
        + '        </label>'
        + '        <div id="monFieldIntervals" class="monField">'
        + '          <span>Intervallregler</span>'
        + '          <div class="monRuleHdr monRuleHdrInterval">'
        + '            <span>Start</span><span>Slut</span><span>Förväntat</span><span>Varn %</span><span>Tol %</span><span></span>'
        + '          </div>'
        + '          <div id="monIntervalsGrid" class="monRulesGrid"></div>'
        + '          <button type="button" id="monAddIntervalBtn" class="btn btnGhost">+ Lägg till intervall</button>'
        + '        </div>'
        + '        <div id="monFieldTimes" class="monField">'
        + '          <span>Exakta tider</span>'
        + '          <div class="monRuleHdr monRuleHdrTime">'
        + '            <span>Tid</span><span>Förväntat</span><span></span>'
        + '          </div>'
        + '          <div id="monTimesGrid" class="monRulesGrid"></div>'
        + '          <button type="button" id="monAddTimeBtn" class="btn btnGhost">+ Lägg till tid</button>'
        + '        </div>'
        + '        <label id="monFieldWeekdays" class="monField">'
        + '          <span>Veckodagar (JSON)</span>'
        + '          <textarea id="monEditWeekdays" rows="5"></textarea>'
        + '        </label>'
        + '        <label id="monFieldMonthDays" class="monField">'
        + '          <span>Månadsdagar (JSON)</span>'
        + '          <textarea id="monEditMonthDays" rows="5"></textarea>'
        + '        </label>'
        + '        <label id="monFieldDates" class="monField">'
        + '          <span>Datum (JSON)</span>'
        + '          <textarea id="monEditDates" rows="5"></textarea>'
        + '        </label>'
        + '        <div id="monEditorError" class="monEditorError" hidden></div>'
        + '        <div id="monEditorSuccess" class="monEditorSuccess" hidden>Sparat</div>'
        + '      </div>'
        + '      <div class="monEditorFoot">'
        + '        <button type="button" id="monEditorCancel" class="btn">Avbryt</button>'
        + '        <button type="button" id="monEditorSave" class="btn btnPrimary">Spara</button>'
        + '      </div>'
        + '    </div>'
        + '  </div>'
        + '</div>';

      document.body.appendChild(root.firstChild);

      var backdrop = editorGet('monEditorBackdrop');
      var closeBtn = editorGet('monEditorClose');
      var cancelBtn = editorGet('monEditorCancel');
      var saveBtn = editorGet('monEditorSave');
      var modeEl = editorGet('monEditMode');
      var addIntervalBtn = editorGet('monAddIntervalBtn');
      var addTimeBtn = editorGet('monAddTimeBtn');

      function closeEditor(){
        if(backdrop){
          backdrop.hidden = true;
          backdrop.style.display = 'none';
        }
        Monitor.state.editingFlowId = '';
        Monitor.state.editingConfig = null;
      }
      Monitor.closeEditor = closeEditor;

      function showEl(id, yes){
        var el = editorGet(id);
        if(el) el.style.display = yes ? '' : 'none';
      }

      function syncModeUi(){
        var mode = modeEl ? String(modeEl.value || 'interval') : 'interval';
        showEl('monFieldIntervals', mode === 'interval');
        showEl('monFieldTimes', mode === 'exactTimes');
        showEl('monFieldExpected', mode === 'weekdays' || mode === 'monthDays' || mode === 'dates');
        showEl('monFieldDueTime', mode === 'weekdays' || mode === 'monthDays' || mode === 'dates');
        showEl('monFieldCarry', mode === 'weekdays' || mode === 'monthDays' || mode === 'dates');
        showEl('monFieldWeekdays', mode === 'weekdays');
        showEl('monFieldMonthDays', mode === 'monthDays');
        showEl('monFieldDates', mode === 'dates');
      }

      if(closeBtn && !closeBtn.__wired){
        closeBtn.__wired = true;
        closeBtn.addEventListener('click', function(e){
          try{ e.preventDefault(); e.stopPropagation(); }catch(ignore){}
          closeEditor();
        });
      }
      if(cancelBtn && !cancelBtn.__wired){
        cancelBtn.__wired = true;
        cancelBtn.addEventListener('click', function(e){
          try{ e.preventDefault(); e.stopPropagation(); }catch(ignore){}
          closeEditor();
        });
      }
      if(backdrop && !backdrop.__wiredBackdrop){
        backdrop.__wiredBackdrop = true;
        backdrop.addEventListener('click', function(e){
          if(e.target === backdrop) closeEditor();
        });
      }
      if(modeEl && !modeEl.__wiredModeUi){
        modeEl.__wiredModeUi = true;
        modeEl.addEventListener('change', function(){
          var mode = String(modeEl.value || 'interval');
          syncModeUi();
          if(mode === 'interval'){
            var cur = editorCollectIntervals();
            if(!cur.length) editorRenderIntervals([{ start:'', end:'', expected:0, warnAtPct:0.7, tolerancePct:0.05 }]);
          }else if(mode === 'exactTimes'){
            var cur2 = editorCollectTimes();
            if(!cur2.length) editorRenderTimes([{ time:'', expected:0 }]);
          }
        });
      }
      if(addIntervalBtn && !addIntervalBtn.__wired){
        addIntervalBtn.__wired = true;
        addIntervalBtn.addEventListener('click', editorAddIntervalRow);
      }
      if(addTimeBtn && !addTimeBtn.__wired){
        addTimeBtn.__wired = true;
        addTimeBtn.addEventListener('click', editorAddTimeRow);
      }
      if(backdrop && !backdrop.__wiredRules){
        backdrop.__wiredRules = true;
        backdrop.addEventListener('click', function(e){
          var btn = e.target && (e.target.closest ? e.target.closest('.monRuleRemoveBtn') : null);
          if(btn){
            try{ e.preventDefault(); e.stopPropagation(); }catch(ignore){}
            editorRemoveRuleRow(btn);
          }
        });
      }

      if(saveBtn && !saveBtn.__wired){
        saveBtn.__wired = true;
        saveBtn.addEventListener('click', async function(){
          try{
            var cfg = Monitor.state.editingConfig;
            var flowId = Monitor.state.editingFlowId;
            if(!cfg){
              cfg = await Monitor.loadConfig();
              cfg = cfg || {};
              cfg.defaults = cfg.defaults || {};
              cfg.flows = Array.isArray(cfg.flows) ? cfg.flows : [];
              Monitor.state.editingConfig = cfg;
            }

            cfg.flows = Array.isArray(cfg.flows) ? cfg.flows : [];
            var isNew = (flowId === '__NEW__');
            var flow = null;

            if(isNew){
              flow = {};
              cfg.flows.push(flow);
            }else{
              for(var i=0;i<cfg.flows.length;i++){
                if(String(cfg.flows[i].flowId || '') === String(flowId)){
                  flow = cfg.flows[i];
                  break;
                }
              }
              if(!flow) throw new Error('Kunde inte hitta flow i config');
            }

            var errEl = editorGet('monEditorError');
            var okEl = editorGet('monEditorSuccess');
            if(errEl){ errEl.hidden = true; errEl.textContent = ''; }
            if(okEl){ okEl.hidden = true; okEl.textContent = 'Sparat'; }

            saveBtn.disabled = true;
            saveBtn.textContent = 'Sparar...';

            var sender = (editorGet('monEditSender').value || '').trim();
            var receiver = (editorGet('monEditReceiver').value || '').trim();
            var msgType = (editorGet('monEditMsgType').value || '').trim();
            if(!sender || !receiver || !msgType) throw new Error('Avsändare, Mottagare och Meddelandetyp måste anges');

            flow.flowId = sender + '||' + receiver + '||' + msgType;
            flow.name = (editorGet('monEditName').value || '').trim();
            flow.enabled = !!editorGet('monEditEnabled').checked;

            var warnLeadRaw = editorGet('monEditWarnLead').value;
            var errGraceRaw = editorGet('monEditErrorGrace').value;
            flow.warningLeadMinutes = warnLeadRaw === '' ? null : Number(warnLeadRaw);
            flow.errorGraceMinutes = errGraceRaw === '' ? null : Number(errGraceRaw);

            delete flow.mode;
            delete flow.intervals;
            delete flow.times;

            var mode = String(editorGet('monEditMode').value || 'interval').trim() || 'interval';
            var schedule = { type: mode };

            if(mode === 'interval'){
              schedule.intervals = editorCollectIntervals();
            }else if(mode === 'exactTimes'){
              schedule.times = editorCollectTimes();
            }else if(mode === 'weekdays'){
              schedule.expected = Number(editorGet('monEditExpected').value || 0);
              schedule.dueTime = String(editorGet('monEditDueTime').value || '').trim();
              schedule.carryOverMode = String(editorGet('monEditCarryOver').value || 'sameDay');
              schedule.weekdays = editorJsonArray(editorGet('monEditWeekdays').value, 'Veckodagar').map(function(x){ return Number(x); });
            }else if(mode === 'monthDays'){
              schedule.expected = Number(editorGet('monEditExpected').value || 0);
              schedule.dueTime = String(editorGet('monEditDueTime').value || '').trim();
              schedule.carryOverMode = String(editorGet('monEditCarryOver').value || 'sameDay');
              schedule.monthDays = editorJsonArray(editorGet('monEditMonthDays').value, 'Månadsdagar').map(function(x){ return Number(x); });
            }else if(mode === 'dates'){
              schedule.expected = Number(editorGet('monEditExpected').value || 0);
              schedule.dueTime = String(editorGet('monEditDueTime').value || '').trim();
              schedule.carryOverMode = String(editorGet('monEditCarryOver').value || 'sameDay');
              schedule.dates = editorJsonArray(editorGet('monEditDates').value, 'Datum').map(function(x){ return String(x); });
            }else{
              throw new Error('Okänd typ: ' + mode);
            }

            flow.schedule = schedule;

            await Monitor.saveConfig(cfg);

            try{
              var md = await Monitor.fetch();
              if(md){
                Monitor.state.data = md;
                if(Monitor.render) Monitor.render();
              }
            }catch(ignore){}

            if(okEl){
              okEl.hidden = false;
              okEl.textContent = 'Sparat';
            }

            setTimeout(function(){
              closeEditor();
            }, 300);

          }catch(e){
            var err = editorGet('monEditorError');
            if(err){
              err.hidden = false;
              err.textContent = 'Kunde inte spara: ' + (e && e.message ? e.message : e);
            }
            try{ console.error('mon editor save failed', e); }catch(ignore2){}
          }finally{
            saveBtn.disabled = false;
            saveBtn.textContent = 'Spara';
          }
        });
      }

      syncModeUi();
    };

    Monitor.openEditor = async function(flowId){
      try{
        Monitor.ensureEditorDom();

        var cfg = await Monitor.loadConfig();
        cfg = cfg || {};
        cfg.defaults = cfg.defaults || {};
        cfg.flows = Array.isArray(cfg.flows) ? cfg.flows : [];

        var flow = null;
        var isNew = (flowId === '__NEW__');

        if(!isNew){
          for(var i=0;i<cfg.flows.length;i++){
            if(String(cfg.flows[i].flowId || '') === String(flowId)){
              flow = cfg.flows[i];
              break;
            }
          }
          if(!flow) throw new Error('Kunde inte hitta bevakning för ' + flowId);
        }else{
          flow = {
            flowId: '',
            name: '',
            enabled: true,
            schedule: {
              type: 'interval',
              intervals: [
                { start:'', end:'', expected:0, warnAtPct:0.7, tolerancePct:0.05 }
              ]
            }
          };
        }

        var nf = editorNormalizeFlow(flow);
        Monitor.state.editingFlowId = flowId;
        Monitor.state.editingConfig = cfg;

        var parts = String(flow.flowId || '').split('||');
        editorGet('monEditSender').value = parts[0] || '';
        editorGet('monEditReceiver').value = parts[1] || '';
        editorGet('monEditMsgType').value = parts.slice(2).join('||') || '';
        editorGet('monEditName').value = flow.name || '';
        editorGet('monEditEnabled').checked = flow.enabled !== false;
        editorGet('monEditMode').value = nf.mode;
        editorGet('monEditWarnLead').value = flow.warningLeadMinutes == null ? '' : String(flow.warningLeadMinutes);
        editorGet('monEditErrorGrace').value = flow.errorGraceMinutes == null ? '' : String(flow.errorGraceMinutes);
        editorGet('monEditExpected').value = nf.expected;
        editorGet('monEditDueTime').value = nf.dueTime || '';
        editorGet('monEditCarryOver').value = nf.carryOverMode || 'sameDay';
        editorRenderIntervals((nf.intervals && nf.intervals.length) ? nf.intervals : [{ start:'', end:'', expected:0, warnAtPct:0.7, tolerancePct:0.05 }]);
        editorRenderTimes((nf.times && nf.times.length) ? nf.times : [{ time:'', expected:0 }]);
        editorGet('monEditWeekdays').value = JSON.stringify(nf.weekdays || [], null, 2);
        editorGet('monEditMonthDays').value = JSON.stringify(nf.monthDays || [], null, 2);
        editorGet('monEditDates').value = JSON.stringify(nf.dates || [], null, 2);

        var err = editorGet('monEditorError');
        var ok = editorGet('monEditorSuccess');
        if(err){ err.hidden = true; err.textContent = ''; }
        if(ok){ ok.hidden = true; ok.textContent = 'Sparat'; }

        var evt = document.createEvent('HTMLEvents');
        evt.initEvent('change', true, false);
        editorGet('monEditMode').dispatchEvent(evt);

        var title = editorGet('monEditorTitle');
        if(title) title.textContent = isNew ? 'Ny bevakning' : 'Redigera bevakning';

        editorGet('monEditorBackdrop').hidden = false;
        editorGet('monEditorBackdrop').style.display = 'flex';
        try{ editorGet('monEditSender').focus(); }catch(ignore3){}
      }catch(e){
        try{ console.error('openEditor failed', e); }catch(ignore){}
        alert('Kunde inte öppna editor: ' + (e && e.message ? e.message : e));
      }
    };

    Monitor.ensureCreateButton = function(){
      var host = document.getElementById('monShowIssues');
      if(!host || document.getElementById('monCreateBtn')) return;
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.id = 'monCreateBtn';
      btn.className = 'btn btnGhost';
      btn.textContent = '+ Ny bevakning';
      btn.addEventListener('click', function(){
        Monitor.openEditor('__NEW__');
      });
      if(host.parentNode) host.parentNode.appendChild(btn);
    };

  Monitor.render = function(){
    var data = Monitor.state.data;
    if(!data) return;

    // Counters
    var errEl=document.getElementById('monErr');
    var warEl=document.getElementById('monWar');
    var infEl=document.getElementById('monInf');
    if(data.counters){
      if(errEl) errEl.textContent = String(data.counters.error||0);
      if(warEl) warEl.textContent = String(data.counters.warning||0);
      if(infEl) infEl.textContent = String(data.counters.info||0);
    }

    var list=document.getElementById('monList');
    if(!list) return;
    list.innerHTML='';

    var rows = Array.isArray(data.rows) ? data.rows.slice() : [];
    rows.sort(function(a,b){ return rank(b.status)-rank(a.status); });

    if(Monitor.state.onlyIssues){
      rows = rows.filter(function(r){
        return String(r.status||'').toUpperCase() !== 'INFO';
      });
    }

    if(!rows.length){
      list.innerHTML='<div class="monEmpty">Inga avvikelser</div>';
      return;
    }

    for(var i=0;i<rows.length;i++){
      var r = rows[i] || {};
      var status = String(r.status||'INFO').toUpperCase();
      var cls = badgeClass(status);

      var name = FM.guiText(r.name || r.flowId || '');
      var mode = r.mode || '';
      var today = (r.today==null) ? '' : String(r.today);
      var msg = r.message || '';
      var details = r.details || '';

      var item = document.createElement('div');
      item.className = 'monItem';
      item.setAttribute('data-status', status);
      if(r.flowId) item.setAttribute('data-flowid', String(r.flowId));

      var left = document.createElement('div');
      left.className = 'monMain';
      left.style.minWidth = '0';

      var nm = document.createElement('div');
      nm.className = 'monName';
      nm.textContent = name;

      var meta = document.createElement('div');
      meta.className = 'monMeta';
      var metaParts = [];
      if(mode) metaParts.push(mode);
      if(today !== '') metaParts.push('Idag ' + today);
      if(msg) metaParts.push(msg);
      if(details) metaParts.push(details);
      meta.textContent = metaParts.join(' • ');

      left.appendChild(nm);
      left.appendChild(meta);

      var right = document.createElement('div');
      right.className = 'monActions';

      // explicit, discoverable action (uses your existing .monLink style)
      var link = document.createElement('button');
      link.type='button';
      link.className='monLink iconBtn';
      link.setAttribute('title','Visa i flöden');
      link.setAttribute('aria-label','Visa i flöden');
      link.innerHTML = MonitorIcons.link;
      if(r.flowId) link.setAttribute('data-flowid', String(r.flowId));
      right.appendChild(link);

      
      // Edit monitoring (opens editor drawer if available)
      var editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'monEditBtn iconBtn';
      editBtn.setAttribute('title','Redigera bevakning');
      editBtn.setAttribute('aria-label','Redigera bevakning');
      if(r.flowId) editBtn.setAttribute('data-flowid', String(r.flowId));
      editBtn.innerHTML = MonitorIcons.edit;
      editBtn.addEventListener('click', function(e){
        try{
          e.preventDefault();
          e.stopPropagation();
        }catch(ignore){}
        try{
          var fid = this.getAttribute('data-flowid') || '';
          if(Monitor && typeof Monitor.openEditor==='function') Monitor.openEditor(fid);
          else if(typeof w.openMonDrawer==='function') w.openMonDrawer(fid);
          else alert('Editor saknas i frontend');
        }catch(err){
          try{ console.error('edit click failed', err); }catch(ignore2){}
          alert('Kunde inte öppna editor');
        }
      });
      right.appendChild(editBtn);
var badge = document.createElement('span');
      badge.className = 'monBadge ' + cls;
      badge.textContent = status;

      right.appendChild(badge);

      item.appendChild(left);
      item.appendChild(right);
      list.appendChild(item);
    }
  };

})(window);
