(function(w){
  'use strict';

  var App = w.FMCleanApp;
  if(!App) return;

  var Flows = App.Flows = {};
  var state = App.state;
  var Util = App.Util;

  function rowKey(r){
    return [
      String(r.sender || ''),
      String(r.receiver || ''),
      String(r.msgType || r.messageType || ''),
      String(r.state || r.status || '')
    ].join('|');
  }

  function filteredRows(){
    var rows = Array.isArray(state.rows) ? state.rows.slice() : [];
    var q = String(state.search || '').trim().toLowerCase();
    var out = [];

    for(var i=0;i<rows.length;i++){
      var r = rows[i] || {};
      var sender = String(r.sender || '');
      var receiver = String(r.receiver || '');
      var msgType = String(r.msgType || r.messageType || '');
      var status = String(r.state || r.status || '');

      if(state.sender && sender !== state.sender) continue;
      if(state.receiver && receiver !== state.receiver) continue;
      if(state.status && status !== state.status) continue;

      if(q){
        var hay = (sender + ' ' + receiver + ' ' + msgType + ' ' + status).toLowerCase();
        if(hay.indexOf(q) === -1) continue;
      }

      out.push(r);
    }

    out.sort(function(a,b){
      var ak = rowKey(a).toLowerCase();
      var bk = rowKey(b).toLowerCase();
      if(ak < bk) return -1;
      if(ak > bk) return 1;
      return 0;
    });

    return out;
  }

  function uniqueValues(key){
    var rows = Array.isArray(state.rows) ? state.rows : [];
    var seen = Object.create(null);
    var out = [];

    for(var i=0;i<rows.length;i++){
      var r = rows[i] || {};
      var v = '';
      if(key === 'msgType'){
        v = String(r.msgType || r.messageType || '');
      } else if(key === 'state'){
        v = String(r.state || r.status || '');
      } else {
        v = String(r[key] || '');
      }

      if(!v || seen[v]) continue;
      seen[v] = true;
      out.push(v);
    }

    out.sort();
    return out;
  }

  Flows.renderSelect = function(id, values, placeholder, selected){
    var el = document.getElementById(id);
    if(!el) return;

    var html = '<option value="">' + Util.esc(placeholder) + '</option>';
    for(var i=0;i<values.length;i++){
      var v = values[i];
      html += '<option value="' + Util.esc(v) + '"' +
        (v === selected ? ' selected' : '') + '>' +
        Util.esc(v) + '</option>';
    }
    el.innerHTML = html;
  };

  Flows.renderFilterOptions = function(){
    Flows.renderSelect('fmSender', uniqueValues('sender'), 'Alla avsändare', state.sender);
    Flows.renderSelect('fmReceiver', uniqueValues('receiver'), 'Alla mottagare', state.receiver);
    Flows.renderSelect('fmStatus', uniqueValues('state'), 'Alla statusar', state.status);

    var searchEl = document.getElementById('fmSearch');
    if(searchEl && searchEl.value !== String(state.search || '')){
      searchEl.value = String(state.search || '');
    }

    var fromEl = document.getElementById('fmDateFrom');
    var toEl = document.getElementById('fmDateTo');
    if(fromEl && state.range) fromEl.value = state.range.from || '';
    if(toEl && state.range) toEl.value = state.range.to || '';
  };

  Flows.renderTable = function(){
    var body = document.getElementById('fmTableBody');
    var hint = document.getElementById('fmTableHint');
    if(!body) return;

    var rows = filteredRows();

    if(!rows.length){
      body.innerHTML = '<tr><td colspan="5" class="fmEmpty">Ingen data</td></tr>';
      if(hint) hint.textContent = '0 rader';
      return;
    }

    var html = '';
    for(var i=0;i<rows.length;i++){
      var r = rows[i] || {};
      var id = rowKey(r);
      var msgType = String(r.msgType || r.messageType || '');
      var status = String(r.state || r.status || '');
      var count = (r.count == null ? '' : String(r.count));

      html += ''
        + '<tr data-rowid="' + Util.esc(id) + '"' + (state.selectedId === id ? ' class="isSelected"' : '') + '>'
        + '  <td class="fmMono">' + Util.esc(r.sender || '') + '</td>'
        + '  <td class="fmMono">' + Util.esc(r.receiver || '') + '</td>'
        + '  <td>' + Util.esc(msgType) + '</td>'
        + '  <td>' + Util.esc(status) + '</td>'
        + '  <td class="fmNum fmMono">' + Util.esc(count) + '</td>'
        + '</tr>';
    }

    body.innerHTML = html;
    if(hint) hint.textContent = rows.length + ' rader';
  };

  Flows.findRowById = function(id){
    var rows = Array.isArray(state.rows) ? state.rows : [];
    for(var i=0;i<rows.length;i++){
      if(rowKey(rows[i]) === id) return rows[i];
    }
    return null;
  };

  Flows.render = function(){
    Flows.renderFilterOptions();
    Flows.renderTable();
  };

  Flows.bind = function(){
    var body = document.getElementById('fmTableBody');
    if(body && !body.__wiredFlows){
      body.__wiredFlows = true;

      body.addEventListener('click', function(e){
        var tr = e.target && e.target.closest ? e.target.closest('tr[data-rowid]') : null;
        if(!tr) return;
        state.selectedId = tr.getAttribute('data-rowid') || '';
        Flows.renderTable();
      });

      body.addEventListener('dblclick', function(e){
        var tr = e.target && e.target.closest ? e.target.closest('tr[data-rowid]') : null;
        if(!tr) return;
        var id = tr.getAttribute('data-rowid') || '';
        state.selectedId = id;
        Flows.renderTable();

        if(App.Details && typeof App.Details.open === 'function'){
          App.Details.open(Flows.findRowById(id));
        }
      });
    }
  };
})(window);
