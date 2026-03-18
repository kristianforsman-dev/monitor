(function(w){
  'use strict';

  var App = w.FMCleanApp || (w.FMCleanApp = {});
  var state = App.state;
  var Util = App.Util;

  App.render = function(){
    if(App.Layout && App.Layout.ensureRoot) App.Layout.ensureRoot();
    if(Util && Util.hideLegacyDetails) Util.hideLegacyDetails();

    var meta = Util && Util.byId ? Util.byId('fmRangeMeta') : null;
    if(meta){
      meta.textContent = state && state.range ? (state.range.from + ' → ' + state.range.to) : '';
    }

    if(App.Flows && App.Flows.renderFilters) App.Flows.renderFilters();
    if(App.Flows && App.Flows.renderTable) App.Flows.renderTable();
    if(App.Monitoring && App.Monitoring.renderList) App.Monitoring.renderList();

    if(App.Flows && App.Flows.bind) App.Flows.bind();
    if(App.Monitoring && App.Monitoring.bindList) App.Monitoring.bindList();
    if(App.MonitorEditor && App.MonitorEditor.bind) App.MonitorEditor.bind();
  };

  App.bindGlobal = function(){
    var root = Util && Util.byId ? Util.byId('fmCleanApp') : null;
    if(!root || root.__globalWired) return;
    root.__globalWired = true;

    root.addEventListener('pointerup', function(e){
      var btn = e.target && e.target.closest ? e.target.closest('.fmBtn, .fmBtnGhost, .fmBtnPrimary, .fmPreset') : null;
      if(btn){
        try{ btn.blur(); }catch(ignore){}
      }
    });

    root.addEventListener('click', function(e){
      var preset = e.target && e.target.closest ? e.target.closest('.fmPreset') : null;
      if(preset){
        if(App.Util && App.Util.presetToRange){
          state.range = App.Util.presetToRange(preset.getAttribute('data-preset') || '1d');
          if(App.refresh) App.refresh().catch(App.showError);
        }
        return;
      }

      if(e.target.id === 'fmClearFilters'){
        state.search = '';
        state.sender = '';
        state.receiver = '';
        state.status = '';
        if(App.render) App.render();
        return;
      }

      if(e.target.id === 'fmDetailCloseBtn' || e.target.id === 'fmDetailCloseX' || e.target.id === 'fmDetailBackdrop'){
        if(App.Details && App.Details.close) App.Details.close();
        return;
      }

      if(e.target.id === 'fmNewMonitor'){
        if(App.MonitorEditor && App.MonitorEditor.open) App.MonitorEditor.open('__NEW__');
        return;
      }
    });

    root.addEventListener('input', function(e){
      if(e.target.id === 'fmSearch'){
        state.search = e.target.value || '';
        if(App.Flows && App.Flows.renderTable) App.Flows.renderTable();
      }
    });

    root.addEventListener('change', function(e){
      if(e.target.id === 'fmSender'){
        state.sender = e.target.value || '';
        if(App.Flows && App.Flows.renderTable) App.Flows.renderTable();
      } else if(e.target.id === 'fmReceiver'){
        state.receiver = e.target.value || '';
        if(App.Flows && App.Flows.renderTable) App.Flows.renderTable();
      } else if(e.target.id === 'fmStatus'){
        state.status = e.target.value || '';
        if(App.Flows && App.Flows.renderTable) App.Flows.renderTable();
      } else if(e.target.id === 'fmDateFrom' || e.target.id === 'fmDateTo'){
        var from = String((Util.byId('fmDateFrom') || {}).value || '').trim();
        var to = String((Util.byId('fmDateTo') || {}).value || '').trim();
        if(from && to){
          state.range = { from: from, to: to };
          if(App.refresh) App.refresh().catch(App.showError);
        } else {
          if(App.render) App.render();
        }
      }
    });
  };
})(window);
