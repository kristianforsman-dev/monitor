(function(w){
  'use strict';

  var App = w.FMCleanApp || {};
  var state = App.state || {};
  var Util = App.Util || {};

  function byId(id){
    return Util.byId ? Util.byId(id) : document.getElementById(id);
  }

  function bindFlowTable(){
    var body = byId('fmTableBody');
    if(!body || body.__wireupBound) return;
    body.__wireupBound = true;

    body.addEventListener('click', function(e){
      var tr = e.target && e.target.closest ? e.target.closest('tr[data-rowid]') : null;
      if(!tr) return;
      state.selectedId = tr.getAttribute('data-rowid') || '';
      if(App.Flows && App.Flows.renderTable) App.Flows.renderTable();
    });

    body.addEventListener('dblclick', function(e){
      var tr = e.target && e.target.closest ? e.target.closest('tr[data-rowid]') : null;
      if(!tr) return;
      var id = tr.getAttribute('data-rowid') || '';
      state.selectedId = id;
      if(App.Flows && App.Flows.renderTable) App.Flows.renderTable();
      if(App.Flows && App.Flows.findRowById && App.Details && App.Details.open){
        App.Details.open(App.Flows.findRowById(id));
      }
    });
  }

  function bindMonitoringList(){
    var host = byId('fmMonitorList');
    if(!host || host.__wireupBound) return;
    host.__wireupBound = true;

    host.addEventListener('dblclick', function(e){
      var card = e.target && e.target.closest ? e.target.closest('.fmMonitorItem[data-flowid]') : null;
      if(!card) return;
      var fid = card.getAttribute('data-flowid') || '';
      if(App.MonitorEditor && App.MonitorEditor.open){
        App.MonitorEditor.open(fid);
      }
    });
  }

  function bindNewButton(){
    var btn = byId('fmNewMonitor');
    if(!btn || btn.__wireupBound) return;
    btn.__wireupBound = true;

    btn.addEventListener('click', function(){
      if(App.MonitorEditor && App.MonitorEditor.open){
        App.MonitorEditor.open('__NEW__');
      }
    });
  }

  function bindDetailClose(){
    var ids = ['fmDetailCloseBtn', 'fmDetailCloseX', 'fmDetailBackdrop'];
    for(var i=0;i<ids.length;i++){
      var el = byId(ids[i]);
      if(!el || el.__wireupBound) continue;
      el.__wireupBound = true;
      el.addEventListener('click', function(e){
        if(this.id === 'fmDetailBackdrop' && e.target !== this) return;
        if(App.Details && App.Details.close) App.Details.close();
      });
    }
  }

  function bindEditorClose(){
    var ids = ['fmMonitorCloseX', 'fmMonitorCancel', 'fmMonitorBackdrop'];
    for(var i=0;i<ids.length;i++){
      var el = byId(ids[i]);
      if(!el || el.__wireupBound) continue;
      el.__wireupBound = true;
      el.addEventListener('click', function(e){
        if(this.id === 'fmMonitorBackdrop' && e.target !== this) return;
        if(App.MonitorEditor && App.MonitorEditor.close) App.MonitorEditor.close();
      });
    }
  }

  function bindEditorSave(){
    var btn = byId('fmMonitorSave');
    if(!btn || btn.__wireupBound) return;
    btn.__wireupBound = true;

    btn.addEventListener('click', function(){
      if(App.MonitorEditor && App.MonitorEditor.save){
        App.MonitorEditor.save().catch(function(err){
          if(App.MonitorEditor && App.MonitorEditor.showErrorList){
            App.MonitorEditor.showErrorList([String(err && err.message ? err.message : err)]);
          } else {
            console.error(err);
          }
        });
      }
    });
  }

  function ensureEditor(){
    if(App.MonitorEditor && App.MonitorEditor.ensureDom){
      App.MonitorEditor.ensureDom();
    }
    if(App.MonitorEditor && App.MonitorEditor.bind){
      App.MonitorEditor.bind();
    }
  }

  function robustBind(){
    ensureEditor();
    bindFlowTable();
    bindMonitoringList();
    bindNewButton();
    bindDetailClose();
    bindEditorClose();
    bindEditorSave();
  }

  var origRender = App.render;
  App.render = function(){
    if(typeof origRender === 'function'){
      origRender();
    }
    robustBind();
  };

  function bootBind(){
    robustBind();
  }

  if(document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', bootBind);
  } else {
    bootBind();
  }
})(window);
