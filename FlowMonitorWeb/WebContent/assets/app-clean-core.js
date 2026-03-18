(function(w){
  'use strict';

  var App = w.FMCleanApp = w.FMCleanApp || {};

  App.state = App.state || {
    range: null,
    rows: [],
    monitoring: [],
    selectedId: '',
    search: '',
    sender: '',
    receiver: '',
    status: '',
    monitoringConfig: null,
    editingFlowId: '',
    editorDraft: {
      monthDays: [],
      weekdays: [],
      dates: []
    }
  };

  App.Util = App.Util || {
    byId: function(id){ return document.getElementById(id); },

    esc: function(v){
      return String(v == null ? '' : v)
        .replace(/&/g,'&amp;')
        .replace(/</g,'&lt;')
        .replace(/>/g,'&gt;')
        .replace(/"/g,'&quot;');
    },

    todayISO: function(){
      try{
        if(w.FM && w.FM.state && w.FM.state.mockToday){
          return String(w.FM.state.mockToday);
        }
      }catch(e){}
      var d = new Date();
      var y = d.getFullYear();
      var m = String(d.getMonth()+1).padStart(2, '0');
      var day = String(d.getDate()).padStart(2, '0');
      return y + '-' + m + '-' + day;
    },

    addDays: function(iso, delta){
      var d = new Date(iso + 'T00:00:00');
      d.setDate(d.getDate() + delta);
      var y = d.getFullYear();
      var m = String(d.getMonth()+1).padStart(2, '0');
      var day = String(d.getDate()).padStart(2, '0');
      return y + '-' + m + '-' + day;
    },

    presetToRange: function(preset){
      var to = App.Util.todayISO();
      if(preset === '30d') return { from: App.Util.addDays(to, -29), to: to };
      if(preset === '7d') return { from: App.Util.addDays(to, -6), to: to };
      return { from: to, to: to };
    },

    rowKey: function(r){
      return [r.sender||'', r.receiver||'', r.msgType||'', r.state||''].join('|');
    },

    uniqueValues: function(rows, key){
      var seen = Object.create(null);
      var out = [];
      for(var i=0;i<rows.length;i++){
        var v = String((rows[i] || {})[key] || '');
        if(!v || seen[v]) continue;
        seen[v] = true;
        out.push(v);
      }
      out.sort();
      return out;
    },

    hideLegacyDetails: function(){
      var ids = [
        'detailPane','detailPanel','flowDetail','detailDrawerHost','detailHost',
        'detail','details','detailView','flowDetails','detailContainer',
        'monEditorBackdrop'
      ];
      for(var i=0;i<ids.length;i++){
        var el = App.Util.byId(ids[i]);
        if(el){
          el.style.display = 'none';
          el.hidden = true;
        }
      }
      var sels = ['.legacyDetail','.detailInline','.detailPane','.detailPanel','.flowDetailInline'];
      for(var j=0;j<sels.length;j++){
        var nodes = document.querySelectorAll(sels[j]);
        for(var k=0;k<nodes.length;k++){
          nodes[k].style.display = 'none';
          nodes[k].hidden = true;
        }
      }
    }
  };

  App.Api = App.Api || {
    fetchJson: async function(url, init){
      var r = await fetch(url, init || { cache:'no-store' });
      if(!r.ok) throw new Error(url + ' -> ' + r.status);
      return await r.json();
    },

    getRangeRows: async function(range){
      var q = '?from=' + encodeURIComponent(range.from) + '&to=' + encodeURIComponent(range.to);
      var data = await App.Api.fetchJson('api/range' + q, { cache:'no-store' });
      return Array.isArray(data && data.rows) ? data.rows : [];
    },

    getMonitoringRows: async function(range){
      var q = '?from=' + encodeURIComponent(range.from) + '&to=' + encodeURIComponent(range.to);
      var data = await App.Api.fetchJson('api/monitoring' + q, { cache:'no-store' });
      return Array.isArray(data && data.rows) ? data.rows : [];
    },

    loadMonitoringConfig: async function(){
      var cfg = await App.Api.fetchJson('api/monitoring-config', { cache:'no-store' });
      cfg = cfg || {};
      cfg.defaults = cfg.defaults || {};
      cfg.flows = Array.isArray(cfg.flows) ? cfg.flows : [];
      return cfg;
    },

    saveMonitoringConfig: async function(cfg){
      return await App.Api.fetchJson('api/monitoring-config', {
        method:'POST',
        headers:{ 'Content-Type':'application/json; charset=UTF-8' },
        body: JSON.stringify(cfg, null, 2)
      });
    }
  };
})(window);
