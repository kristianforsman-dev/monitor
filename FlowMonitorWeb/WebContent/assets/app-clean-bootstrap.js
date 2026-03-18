(function(w){
  'use strict';
  var App = w.FMCleanApp;
  if(!App) return;

  App.showError = function(err){
    console.error(err);
    alert('Kunde inte ladda sidan: ' + (err && err.message ? err.message : err));
  };

  App.refresh = async function(){
    if(!App.state.range || !App.state.range.from || !App.state.range.to){
      App.state.range = App.Util.presetToRange('1d');
    }

    var results = await Promise.all([
      App.Api.getRangeRows(App.state.range),
      App.Api.getMonitoringRows(App.state.range)
    ]);

    App.state.rows = results[0];
    App.state.monitoring = results[1];

    if(App.render) App.render();
  };

  App.boot = async function(){
    if(App.Layout && App.Layout.ensureRoot) App.Layout.ensureRoot();
    if(App.bindGlobal) App.bindGlobal();
    try{
      await App.refresh();
    }catch(err){
      App.showError(err);
    }
  };

  if(document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', App.boot);
  }else{
    App.boot();
  }
})(window);
