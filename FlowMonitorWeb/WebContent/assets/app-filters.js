
(function(w){
  'use strict';
  var FM = w.FM;

  var Filters = FM.Filters = {};

  Filters.init = function(){
    // load persisted filters
    FM.state.filters = FM.loadJSON(FM.STORAGE_FILTERS) || {sender:[],receiver:[],msgType:[],state:[]};

    Filters.bindDropdownOpenClose();
    Filters.updateChipRow();
    Filters.updateDropdownBadges();
  };

  Filters.matches = function(r){
    var q = (FM.state.query||'').trim().toLowerCase();
    if(q){
      var hay=(String(r.sender||'')+' '+String(r.receiver||'')+' '+String(r.msgType||'')+' '+String(r.state||'')).toLowerCase();
      if(hay.indexOf(q)===-1) return false;
    }
    var f = FM.state.filters || {};
    if(f.sender && f.sender.length && f.sender.indexOf(String(r.sender||''))===-1) return false;
    if(f.receiver && f.receiver.length && f.receiver.indexOf(String(r.receiver||''))===-1) return false;
    if(f.msgType && f.msgType.length && f.msgType.indexOf(String(r.msgType||''))===-1) return false;
    if(f.state && f.state.length && f.state.indexOf(String(r.state||''))===-1) return false;
    return true;
  };

  function uniqSorted(values){
    var set=Object.create(null);
    for(var i=0;i<values.length;i++){
      var v=String(values[i]==null?'':values[i]);
      if(!v) continue;
      set[v]=true;
    }
    var out=Object.keys(set);
    out.sort(function(a,b){ return a.localeCompare(b); });
    return out;
  }

  Filters.syncDropdownOptions = function(data){
    var sender=[],receiver=[],msgType=[],state=[];
    for(var i=0;i<data.length;i++){
      sender.push(data[i].sender);
      receiver.push(data[i].receiver);
      msgType.push(data[i].msgType);
      state.push(data[i].state);
    }

    var optSender = uniqSorted(sender);
    var optReceiver = uniqSorted(receiver);
    var optMsgType = uniqSorted(msgType);
    var optState = uniqSorted(state);

    // Prune selected filters that no longer exist in the current dataset.
    // This prevents "path-dependent" results when switching ranges.
    var f = FM.state.filters || {sender:[],receiver:[],msgType:[],state:[]};
    var changed = false;

    function toSet(arr){
      var s = Object.create(null);
      for(var i=0;i<arr.length;i++) s[arr[i]] = true;
      return s;
    }
    function prune(key, allowed){
      var arr = f[key] || [];
      if(!arr.length) return;
      var out = [];
      for(var i=0;i<arr.length;i++){
        if(allowed[arr[i]]) out.push(arr[i]);
        else changed = true;
      }
      f[key] = out;
    }

    prune('sender',   toSet(optSender));
    prune('receiver', toSet(optReceiver));
    prune('msgType',  toSet(optMsgType));
    prune('state',    toSet(optState));

    if(changed){
      FM.state.filters = f;
      FM.saveJSON(FM.STORAGE_FILTERS, f);
      // Also reset pagination so the user immediately sees results.
      FM.state.visibleCount = FM.state.pageSize;
    }

    Filters.updateDropdown('sender', optSender);
    Filters.updateDropdown('receiver', optReceiver);
    Filters.updateDropdown('msgType', optMsgType);
    Filters.updateDropdown('state', optState);
    Filters.updateChipRow();
    Filters.updateDropdownBadges();
  };

  Filters.updateDropdownBadges = function(){
    var dds=document.querySelectorAll('.dd[data-dd]');
    for(var i=0;i<dds.length;i++){
      var key=dds[i].getAttribute('data-dd');
      var btn=dds[i].querySelector('.ddBtn');
      if(!btn) continue;

      var old=btn.querySelector('.countBadge');
      if(old) old.parentNode.removeChild(old);

      var f = FM.state.filters || {};
      var count=(f[key]&&f[key].length)?f[key].length:0;
      if(count){
        var badge=document.createElement('span');
        badge.className='countBadge';
        badge.textContent=String(count);
        var label = btn.querySelector('.ddLabel');
        if(label) btn.insertBefore(badge, label);
        else btn.insertBefore(badge, btn.firstChild);
      }
    }
  };

  Filters.updateDropdown = function(key, options){
    var dd=document.querySelector('.dd[data-dd="'+key+'"]');
    if(!dd) return;
    var list=dd.querySelector('[data-dd-list]');
    if(!list) return;
    var current=(FM.state.filters && FM.state.filters[key]) ? FM.state.filters[key] : [];
    list.innerHTML='';

    if(!options.length){
      list.innerHTML='<div class="ddEmpty">Inga värden</div>';
      return;
    }

    for(var i=0;i<options.length;i++){
      (function(value){
        var row=document.createElement('label');
        row.className='ddItem';
        var cb=document.createElement('input');
        cb.type='checkbox';
        cb.checked=current.indexOf(value)!==-1;
        if(cb.checked) row.classList.add('selected');

        var span=document.createElement('span');
        span.textContent=FM.guiText(value);

        cb.addEventListener('change', function(){
          if(cb.checked) row.classList.add('selected'); else row.classList.remove('selected');
          Filters.toggle(key, value, cb.checked);
        });

        row.addEventListener('click', function(e){
          if(e.target === cb) return;
          cb.checked = !cb.checked;
          var ev=document.createEvent('HTMLEvents');
          ev.initEvent('change', true, false);
          cb.dispatchEvent(ev);
        });

        row.appendChild(cb); row.appendChild(span);
        list.appendChild(row);
      })(options[i]);
    }

    var s=dd.querySelector('[data-dd-search]');
    if(s && !s.__wired){
      s.__wired=true;
      s.addEventListener('input',function(){ Filters.filterDropdownList(dd, s.value||''); });
    }
  };

  Filters.filterDropdownList = function(dd, term){
    term=(term||'').trim().toLowerCase();
    var items=dd.querySelectorAll('.ddItem');
    for(var i=0;i<items.length;i++){
      var txt=(items[i].textContent||'').toLowerCase();
      items[i].style.display=(!term||txt.indexOf(term)!==-1)?'':'none';
    }
  };

  Filters.toggle = function(key, value, on){
    var f = FM.state.filters || {sender:[],receiver:[],msgType:[],state:[]};
    var arr=f[key]||[];
    var idx=arr.indexOf(value);
    if(on && idx===-1) arr.push(value);
    if(!on && idx!==-1) arr.splice(idx,1);
    f[key]=arr;
    FM.state.filters = f;
    FM.saveJSON(FM.STORAGE_FILTERS, f);
    Filters.updateChipRow();
    Filters.updateDropdownBadges();
    FM.state.visibleCount = FM.state.pageSize; // reset "Visa fler"
    FM.scheduleRender();
  };

  Filters.clearAll = function(){
    FM.state.query='';
    var qEl = document.getElementById('q');
    if(qEl) qEl.value='';
    FM.state.filters = {sender:[],receiver:[],msgType:[],state:[]};
    FM.saveJSON(FM.STORAGE_FILTERS, FM.state.filters);

    var cbs=document.querySelectorAll('.ddItem input[type="checkbox"]');
    for(var i=0;i<cbs.length;i++){
      cbs[i].checked=false;
      var lab=cbs[i].closest && cbs[i].closest('.ddItem');
      if(lab) lab.classList.remove('selected');
    }
    Filters.updateChipRow();
    Filters.updateDropdownBadges();
    FM.state.visibleCount = FM.state.pageSize;
    FM.scheduleRender();
  };

  Filters.updateChipRow = function(){
    var chipRow=document.getElementById('chipRow');
    if(!chipRow) return;
    chipRow.innerHTML='';

    function addChip(key,value){
      var chip=document.createElement('span');
      chip.className='chip';
      chip.textContent=FM.guiText(value);
      var x=document.createElement('button');
      x.type='button';
      x.setAttribute('aria-label','Ta bort filter');
      x.addEventListener('click',function(){
        Filters.toggle(key,value,false);
        var dd=document.querySelector('.dd[data-dd="'+key+'"]');
        if(dd){
          var items=dd.querySelectorAll('.ddItem');
          for(var i=0;i<items.length;i++){
            var txt=(items[i].textContent||'').trim();
            if(txt===FM.guiText(value) || txt===value){
              var cb=items[i].querySelector('input[type="checkbox"]');
              if(cb){ cb.checked=false; var lab=cb.closest && cb.closest('.ddItem'); if(lab) lab.classList.remove('selected'); }
              break;
            }
          }
        }
      });
      chip.appendChild(x);
      chipRow.appendChild(chip);
    }

    var keys=['sender','receiver','msgType','state'];
    var f = FM.state.filters || {};
    for(var i=0;i<keys.length;i++){
      var k=keys[i], arr=f[k]||[];
      for(var j=0;j<arr.length;j++) addChip(k, arr[j]);
    }
  };

  Filters.bindDropdownOpenClose = function(){
    var dds=document.querySelectorAll('.dd[data-dd]');
    for(var i=0;i<dds.length;i++){
      (function(dd){
        var btn=dd.querySelector('.ddBtn'); if(!btn) return;
        btn.addEventListener('click',function(e){
          e.stopPropagation();
          Filters.closeAllExcept(dd);
          dd.classList.toggle('open');
          if(dd.classList.contains('open')){
            var s=dd.querySelector('[data-dd-search]');
            if(s){ s.focus(); if(s.select) s.select(); }
          }
        });
      })(dds[i]);
    }
    document.addEventListener('click',function(){ Filters.closeAllExcept(null); });
    document.addEventListener('keydown',function(e){ if(e.key==='Escape') Filters.closeAllExcept(null); });
  };

  Filters.closeAllExcept = function(keep){
    var open=document.querySelectorAll('.dd.open');
    for(var i=0;i<open.length;i++){
      if(keep && open[i]===keep) continue;
      open[i].classList.remove('open');
    }
  };

})(window);
