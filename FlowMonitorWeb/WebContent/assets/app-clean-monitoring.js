(function(w){
  var App = w.FMCleanApp;
  if(!App) return;

  var Monitoring = App.Monitoring = {};
  var state = App.state;
  var Util = App.Util;

  Monitoring.render = function(){
    var host = Util.byId('fmMonitorList');
    if(!host) return;

    if(!state.monitoring.length){
      host.innerHTML = '<div class="fmEmpty">Inga bevakningar att visa.</div>';
      return;
    }

    var html = '';
    for(var i=0;i<state.monitoring.length;i++){
      var r = state.monitoring[i] || {};
      var status = String(r.status || 'INFO').toUpperCase();
      var meta = [];
      if(r.mode) meta.push(Monitoring.prettyMode(r.mode));
      if(r.message) meta.push(r.message);
      if(r.details) meta.push(r.details);

      html += ''
        + '<div class="fmMonitorItem" data-flowid="' + Util.esc(r.flowId || '') + '">'
        + '  <div class="fmMonitorMain">'
        + '    <div class="fmMonitorName">' + Util.esc(r.name || r.flowId || '') + '</div>'
        + '    <div class="fmMonitorMeta">' + Util.esc(meta.join(' • ')) + '</div>'
        + '  </div>'
        + '  <div class="fmMonitorActions">'
        + '    <span class="fmBadge ' + Monitoring.badgeClass(status) + '">' + Util.esc(status) + '</span>'
        + '  </div>'
        + '</div>';
    }

    host.innerHTML = html;
  };

  Monitoring.badgeClass = function(status){
    status = String(status || '').toUpperCase();
    if(status === 'ERROR') return 'error';
    if(status === 'WARNING') return 'warning';
    return 'info';
  };

  Monitoring.prettyMode = function(mode){
    mode = String(mode || '');
    if(mode === 'interval' || mode === 'intervals') return 'Intervall';
    if(mode === 'exactTimes') return 'Exakta tider';
    if(mode === 'weekdays') return 'Veckodagar';
    if(mode === 'monthDays') return 'Månadsdagar';
    if(mode === 'dates') return 'Datum';
    return mode;
  };

  Monitoring.bind = function(){
    var host = Util.byId('fmMonitorList');
    if(host && !host.__wiredMonitoring){
      host.__wiredMonitoring = true;
      host.addEventListener('dblclick', function(e){
        var card = e.target && e.target.closest ? e.target.closest('.fmMonitorItem[data-flowid]') : null;
        if(!card) return;
        if(App.MonitorEditor && App.MonitorEditor.open){
          App.MonitorEditor.open(card.getAttribute('data-flowid') || '');
        }
      });
    }
  };

  Monitoring.loadConfig = async function(){
    var r = await fetch('api/monitoring-config', { cache:'no-store' });
    if(!r.ok) throw new Error('Kunde inte läsa monitoring-config');
    return await r.json();
  };

  Monitoring.saveConfig = async function(cfg){
    var r = await fetch('api/monitoring-config', {
      method:'POST',
      headers:{ 'Content-Type':'application/json; charset=UTF-8' },
      body: JSON.stringify(cfg, null, 2)
    });
    var data = null;
    try{ data = await r.json(); }catch(ignore){}
    if(!r.ok || !data || data.ok === false){
      throw new Error((data && data.error) ? data.error : 'Kunde inte spara monitoring-config');
    }
    return data;
  };
})(window);
