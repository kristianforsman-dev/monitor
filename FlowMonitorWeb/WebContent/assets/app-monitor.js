
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
      if(r.flowId) item.setAttribute('data-flowid', String(r.flowId));

      var left = document.createElement('div');
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
      editBtn.innerHTML = MonitorIcons.edit;
      editBtn.addEventListener('click', function(e){
        try{ e.stopPropagation(); }catch(ignore){}
        try{
          if(Monitor && typeof Monitor.openEditor==='function') Monitor.openEditor(r.flowId || '');
          else if(typeof w.openMonDrawer==='function') w.openMonDrawer(r.flowId || '');
        }catch(ignore2){}
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
