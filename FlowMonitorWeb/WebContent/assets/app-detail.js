
(function(){
if(!window.FM) window.FM = {};
if(!FM.Detail) FM.Detail = {};


function esc(s){
 s = (s==null)?'':String(s);
 return s.replace(/[&<>"']/g,function(m){
  return m==='&'?'&amp;':m==='<'?'&lt;':m==='>'?'&gt;':m==='\"'?'&quot;':'&#39;';
 });
}

function mount(){
 if(document.getElementById('fmDrawer')) return;

 var root = document.createElement('div');
 root.id='fmDrawer';
 root.className='drawer';

 root.innerHTML =
 '<div class="drawerBackdrop"></div>'+
 '<div class="drawerPanel">'+
 '<div class="drawerHead">'+
 '<div class="drawerTitle">Detaljer</div>'+
 '<button type="button" id="fmDrawerClose" class="drawerClose" aria-label="Stäng">✕</button>'+
 '</div>'+
 '<div id="fmDrawerBody" class="drawerBody"></div>'+
 '</div>';

 document.body.appendChild(root);

 root.querySelector('.drawerBackdrop').onclick = FM.Detail.close;
 document.getElementById('fmDrawerClose').onclick = FM.Detail.close;
 document.addEventListener('keydown', function(e){ if(e.key==='Escape') FM.Detail.close(); });
}

FM.Detail.openById=function(id){
 mount();
 var d=document.getElementById('fmDrawer');
 var body=document.getElementById('fmDrawerBody');

 var row = FM.state && FM.state.dataById ? FM.state.dataById[id] : null;

 if(!row){
  body.innerHTML='<div class="drawerEmpty">Ingen data</div>';
 }else{
  body.innerHTML=
  '<div class="kvGrid">'+
  '<div class="kv"><div class="k">Sender</div><div class="v">'+esc(row.sender)+'</div></div>'+
  '<div class="kv"><div class="k">Receiver</div><div class="v">'+esc(row.receiver)+'</div></div>'+
  '<div class="kv"><div class="k">Typ</div><div class="v">'+esc(row.msgType)+'</div></div>'+
  '<div class="kv"><div class="k">State</div><div class="v">'+esc(row.state)+'</div></div>'+
  '<div class="kv"><div class="k">Count</div><div class="v">'+esc(row.count)+'</div></div>'+
  '</div>';
 }

 d.classList.add('open');
};

FM.Detail.close=function(){
 var d=document.getElementById('fmDrawer');
 if(d) d.classList.remove('open');
};

document.addEventListener('DOMContentLoaded',mount);
})();



(function(w){
  'use strict';
  var FM = w.FM;
  if(!FM) return;

  var Detail = FM.Detail = FM.Detail || {};

  function esc(v){
    return String(v == null ? '' : v)
      .replace(/&/g,'&amp;')
      .replace(/</g,'&lt;')
      .replace(/>/g,'&gt;')
      .replace(/"/g,'&quot;');
  }

  function byId(id){ return document.getElementById(id); }

  function ensureDrawer(){
    if(byId('fmDetailBackdrop')) return;

    var root = document.createElement('div');
    root.innerHTML = ''
      + '<div id="fmDetailBackdrop" class="fmDetailBackdrop" hidden>'
      + '  <div class="fmDetailShell">'
      + '    <div class="fmDetailCard" role="dialog" aria-modal="true" aria-labelledby="fmDetailTitle">'
      + '      <div class="fmDetailHead">'
      + '        <div id="fmDetailTitle" class="fmDetailTitle">Flödesdetaljer</div>'
      + '        <button type="button" id="fmDetailClose" class="iconBtn fmDetailCloseBtn" aria-label="Stäng">×</button>'
      + '      </div>'
      + '      <div id="fmDetailBody" class="fmDetailBody"></div>'
      + '      <div class="fmDetailFoot">'
      + '        <button type="button" id="fmDetailClose2" class="btn">Stäng</button>'
      + '      </div>'
      + '    </div>'
      + '  </div>'
      + '</div>';

    document.body.appendChild(root.firstChild);

    function closeDrawer(){
      var b = byId('fmDetailBackdrop');
      if(!b) return;
      b.hidden = true;
      b.style.display = 'none';
    }

    var b = byId('fmDetailBackdrop');
    var x = byId('fmDetailClose');
    var c = byId('fmDetailClose2');

    if(x) x.addEventListener('click', function(e){
      try{ e.preventDefault(); e.stopPropagation(); }catch(ignore){}
      closeDrawer();
    });

    if(c) c.addEventListener('click', function(e){
      try{ e.preventDefault(); e.stopPropagation(); }catch(ignore){}
      closeDrawer();
    });

    if(b){
      b.addEventListener('click', function(e){
        if(e.target === b) closeDrawer();
      });
    }

    Detail.close = closeDrawer;
  }

  function normalizeRow(rowOrKey){
    if(!rowOrKey) return null;

    if(typeof rowOrKey === 'object'){
      return rowOrKey;
    }

    try{
      if(FM && FM.state && FM.state.dataById && FM.state.dataById[rowOrKey]){
        return FM.state.dataById[rowOrKey];
      }
    }catch(ignore){}

    return null;
  }

  function renderRow(row){
    row = row || {};
    var body = byId('fmDetailBody');
    if(!body) return;

    var html = ''
      + '<div class="fmDetailGrid">'
      + '  <div class="fmDetailItem"><span class="fmDetailLabel">Avsändare</span><span class="fmDetailValue mono">' + esc(row.sender) + '</span></div>'
      + '  <div class="fmDetailItem"><span class="fmDetailLabel">Mottagare</span><span class="fmDetailValue mono">' + esc(row.receiver) + '</span></div>'
      + '  <div class="fmDetailItem"><span class="fmDetailLabel">Meddelandetyp</span><span class="fmDetailValue">' + esc(row.msgType) + '</span></div>'
      + '  <div class="fmDetailItem"><span class="fmDetailLabel">Status</span><span class="fmDetailValue">' + esc(row.state) + '</span></div>'
      + '  <div class="fmDetailItem"><span class="fmDetailLabel">Antal</span><span class="fmDetailValue mono">' + esc(row.count) + '</span></div>'
      + '</div>';

    body.innerHTML = html;
  }

  Detail.openRow = function(row){
    ensureDrawer();
    renderRow(normalizeRow(row));
    var b = byId('fmDetailBackdrop');
    if(b){
      b.hidden = false;
      b.style.display = 'flex';
    }
  };

  Detail.open = function(rowOrKey){
    ensureDrawer();
    renderRow(normalizeRow(rowOrKey));
    var b = byId('fmDetailBackdrop');
    if(b){
      b.hidden = false;
      b.style.display = 'flex';
    }
  };
})(window);

(function(w){
  'use strict';
  var FM = w.FM;
  if(!FM || !FM.Detail) return;

  function killLegacyDetail(){
    var ids = [
      'detailPane','detailPanel','flowDetail','detailDrawerHost','detailHost',
      'detail','details','detailView','flowDetails','detailContainer'
    ];
    for(var i=0;i<ids.length;i++){
      var el = document.getElementById(ids[i]);
      if(el){
        el.innerHTML = '';
        el.style.display = 'none';
        el.hidden = true;
      }
    }
    var sels = ['.legacyDetail','.detailInline','.detailPane','.detailPanel','.flowDetailInline'];
    for(var j=0;j<sels.length;j++){
      var nodes = document.querySelectorAll(sels[j]);
      for(var k=0;k<nodes.length;k++){
        nodes[k].innerHTML = '';
        nodes[k].style.display = 'none';
        nodes[k].hidden = true;
      }
    }
  }

  var oldOpen = FM.Detail.open;
  var oldOpenRow = FM.Detail.openRow;

  FM.Detail.open = function(rowOrKey){
    killLegacyDetail();
    if(typeof oldOpen === 'function') return oldOpen.call(FM.Detail, rowOrKey);
  };

  FM.Detail.openRow = function(row){
    killLegacyDetail();
    if(typeof oldOpenRow === 'function') return oldOpenRow.call(FM.Detail, row);
    if(typeof oldOpen === 'function') return oldOpen.call(FM.Detail, row);
  };

  FM.Detail.hideLegacy = killLegacyDetail;
})(window);
