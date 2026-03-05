
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


