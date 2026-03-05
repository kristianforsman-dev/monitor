
(function(w){
  'use strict';
  var FM = w.FM;
  if(!FM) return;

  var Table = FM.Table = FM.Table || {};

  // Inline SVG icons (no external libs)
  var TableIcons = {
    detail: '<svg class="svgIcon" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 18l6-6-6-6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>'
  };


  function $(id){ return document.getElementById(id); }

  

  function ensureFooter(){
    try{
      var info = document.getElementById('lmInfo');
      var btn  = document.getElementById('btnLoadMore');
      if(!info && !btn) return;

      // If both already share a parent, just ensure class
      var parent = null;
      if(info && info.parentNode) parent = info.parentNode;
      if(btn && btn.parentNode && btn.parentNode === parent) {
        if(parent && parent.classList) parent.classList.add('lmFooter');
        return;
      }

      // Create a footer container and place after the table
      var footer = document.createElement('div');
      footer.className = 'lmFooter';

      if(info) footer.appendChild(info);
      if(btn) footer.appendChild(btn);

      var table = document.getElementById('flows');
      if(table && table.parentNode){
        // insert after table
        if(table.nextSibling) table.parentNode.insertBefore(footer, table.nextSibling);
        else table.parentNode.appendChild(footer);
      }else{
        document.body.appendChild(footer);
      }
    }catch(e){}
  }

function escapeHtml(str){
    str = (str==null)?'':String(str);
    return str.replace(/[&<>"']/g,function(m){
      return m==='&'?'&amp;':m==='<'?'&lt;':m==='>'?'&gt;':m==='"'?'&quot;':'&#39;';
    });
  }

  function normalizeState(s){ return (s||'').toString().toLowerCase(); }

  function compare(a,b){
    var sort = FM.state.sort || {key:'sender',dir:'asc'};
    var key=sort.key, dir=(sort.dir==='asc')?1:-1;
    var av=a[key], bv=b[key];
    if(key==='count'){ av=Number(av||0); bv=Number(bv||0); return (av-bv)*dir; }
    av=(av==null?'':String(av)).toLowerCase();
    bv=(bv==null?'':String(bv)).toLowerCase();
    if(av<bv) return -1*dir; if(av>bv) return 1*dir; return 0;
  }

  function matchesAllFilters(r){
    var q = (FM.state.query||'').trim().toLowerCase();
    if(q){
      var hay=(String(r.sender||'')+' '+String(r.receiver||'')+' '+String(r.msgType||'')+' '+String(r.state||'')).toLowerCase();
      if(hay.indexOf(q)===-1) return false;
    }
    var f = FM.state.filters || {sender:[],receiver:[],msgType:[],state:[]};
    if(f.sender.length && f.sender.indexOf(String(r.sender||''))===-1) return false;
    if(f.receiver.length && f.receiver.indexOf(String(r.receiver||''))===-1) return false;
    if(f.msgType.length && f.msgType.indexOf(String(r.msgType||''))===-1) return false;
    if(f.state.length && f.state.indexOf(String(r.state||''))===-1) return false;
    return true;
  }

  Table.applySortClasses = function(){
    var table = $('flows');
    if(!table) return;
    var ths = table.querySelectorAll('thead th.sortable');
    var sort = FM.state.sort || {key:'sender',dir:'asc'};
    for(var i=0;i<ths.length;i++){
      ths[i].classList.remove('sortedAsc'); ths[i].classList.remove('sortedDesc');
      var key=ths[i].getAttribute('data-key');
      if(key===sort.key) ths[i].classList.add(sort.dir==='asc'?'sortedAsc':'sortedDesc');
    }
  };

  Table.bindSorting = function(){
    var table = $('flows');
    if(!table) return;
    var ths = table.querySelectorAll('thead th.sortable');
    for(var i=0;i<ths.length;i++){
      (function(th){
        th.addEventListener('click', function(){
          var key=th.getAttribute('data-key'); if(!key) return;
          var sort = FM.state.sort || {key:'sender',dir:'asc'};
          if(sort.key===key) sort.dir=(sort.dir==='asc')?'desc':'asc';
          else { sort.key=key; sort.dir='asc'; }
          FM.state.sort = sort;
          FM.saveJSON(FM.STORAGE_SORT, sort);
          Table.applySortClasses();
          FM.scheduleRender();
        });
      })(ths[i]);
    }
    Table.applySortClasses();
  };

  Table.setData = function(rows){
    FM.state.data = rows || [];
    FM.state.visibleCount = FM.state.pageSize;
    FM.state.prevById = Object.create(null);
    FM.state.dataById = Object.create(null);

    var tbody = $('statusBody');
    if(tbody) tbody.innerHTML = '';

    if(FM.Filters && FM.Filters.syncDropdownOptions){
      FM.Filters.syncDropdownOptions(FM.state.data);
    }

    Table.render();
  };

  Table.render = function(){
    var tbody = $('statusBody');
    if(!tbody) return;

    var data = FM.state.data || [];
    var filtered=[];
    for(var i=0;i<data.length;i++) if(matchesAllFilters(data[i])) filtered.push(data[i]);
    filtered.sort(compare);

    var total = filtered.length;
    var max = Math.min(total, FM.state.visibleCount || total);
    var slice = filtered.slice(0, max);

    // update map for detail view
    FM.state.dataById = Object.create(null);

    if(!slice.length){
      tbody.innerHTML = '<tr><td colspan="5" class="placeholder">Inga rader</td></tr>';
    }else{
      var frag=document.createDocumentFragment();
      for(var k=0;k<slice.length;k++){
        var r=slice[k];
        var id=(FM.rowKey ? FM.rowKey(r) : (r.sender+'|'+r.receiver+'|'+r.msgType+'|'+r.state));
        FM.state.dataById[id]=r;

        var tr=document.createElement('tr');
        tr.setAttribute('data-id', id);

        var st=normalizeState(r.state);
        tr.innerHTML =
          '<td class="mono">'+escapeHtml(FM.guiText(r.sender))+'</td>'+
          '<td class="mono">'+escapeHtml(FM.guiText(r.receiver))+'</td>'+
          '<td>'+escapeHtml(r.msgType)+'</td>'+
          '<td class="state '+escapeHtml(st)+'"><span class="badge">'+escapeHtml(r.state)+'</span></td>'+
          '<td class="num mono countCell"><span class="countVal">'+escapeHtml(r.count)+'</span><button type="button" class="rowDetailBtn iconBtn" title="Detaljer" aria-label="Detaljer">'+TableIcons.detail+'</button></td>';

        frag.appendChild(tr);
      }
      tbody.innerHTML='';
      tbody.appendChild(frag);
    }

    var kpi = $('kpiRows');
    if(kpi) kpi.textContent = String(total);

    // footer / load more
    
    ensureFooter();
var info = $('lmInfo');
    if(info){
      if(total===0) info.textContent = '0';
      else info.textContent = '1-' + String(max) + ' av ' + String(total);
    }
    var btnMore = $('btnLoadMore');
    if(btnMore){
      btnMore.style.display = (max < total) ? '' : 'none';
      btnMore.disabled = !(max < total);
    }

    Table.applySortClasses();
  };

  Table.bindLoadMore = function(){
    
        ensureFooter();
    var btn = $('btnLoadMore');
    if(btn && !btn.__wired){
      btn.__wired=true;
      btn.addEventListener('click', function(){
        FM.state.visibleCount = (FM.state.visibleCount||FM.state.pageSize) + (FM.state.pageSize||50);
        Table.render();
      });
    }
  };

  Table.bindRowDetails = function(){
    var tbody = $('statusBody');
    if(!tbody || tbody.__wiredDetails) return;
    tbody.__wiredDetails = true;

    tbody.addEventListener('click', function(e){
      
      // Detail icon button
      try{
        var b = e.target && (e.target.closest ? e.target.closest('.rowDetailBtn') : null);
        if(b){
          var tr = b.closest ? b.closest('tr[data-id]') : null;
          var id = tr ? tr.getAttribute('data-id') : '';
          if(window.FM && FM.Detail){
            // Prefer openRow(row) if implemented, otherwise open(keyOrRow)
            var rowObj = null;
            try{
              if(tr){
                rowObj = {
                  sender: tr.getAttribute('data-sender')||'',
                  receiver: tr.getAttribute('data-receiver')||'',
                  msgType: tr.getAttribute('data-msgtype')||'',
                  state: tr.getAttribute('data-state')||'',
                  count: Number(tr.getAttribute('data-count')||0)
                };
              }
            }catch(ignore3){}
            if(typeof FM.Detail.openRow==='function') FM.Detail.openRow(rowObj);
            else if(typeof FM.Detail.open==='function') FM.Detail.open(rowObj || id);
          }
          e.preventDefault();
          e.stopPropagation();
          return;
        }
      }catch(ignore){}
// ignore clicks on inputs/buttons/links inside rows
      var t = e.target;
      if(t && (t.tagName==='BUTTON' || t.tagName==='A' || t.tagName==='INPUT' || t.closest && t.closest('button,a,input'))) return;

      var tr = t && t.closest ? t.closest('tr[data-id]') : null;
      if(!tr) return;
      var id = tr.getAttribute('data-id');
      if(FM.Detail && FM.Detail.openById){
        FM.Detail.openById(id);
      }
    });
  };

  Table.init = function(){
    Table.bindSorting();
    Table.bindLoadMore();
    Table.bindRowDetails();
    Table.applySortClasses();
  };

})(window);