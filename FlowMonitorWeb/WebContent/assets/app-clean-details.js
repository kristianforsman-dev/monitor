(function(w){
  'use strict';

  var App = w.FMCleanApp || (w.FMCleanApp = {});
  var Details = App.Details = App.Details || {};
  var Util = App.Util;

  Details.open = function(row){
    if(!row) return;
    Util.hideLegacyDetails();

    var body = Util.byId('fmDetailBody');
    var backdrop = Util.byId('fmDetailBackdrop');
    if(!body || !backdrop) return;

    body.innerHTML = ''
      + '<div class="fmDetailGrid">'
      + '  <div class="fmDetailItem"><span class="fmDetailLabel">Avsändare</span><span class="fmDetailValue fmMono">' + Util.esc(row.sender) + '</span></div>'
      + '  <div class="fmDetailItem"><span class="fmDetailLabel">Mottagare</span><span class="fmDetailValue fmMono">' + Util.esc(row.receiver) + '</span></div>'
      + '  <div class="fmDetailItem"><span class="fmDetailLabel">Meddelandetyp</span><span class="fmDetailValue">' + Util.esc(row.msgType) + '</span></div>'
      + '  <div class="fmDetailItem"><span class="fmDetailLabel">Status</span><span class="fmDetailValue">' + Util.esc(row.state) + '</span></div>'
      + '  <div class="fmDetailItem"><span class="fmDetailLabel">Antal</span><span class="fmDetailValue fmMono">' + Util.esc(row.count) + '</span></div>'
      + '</div>';

    backdrop.hidden = false;
  };

  Details.close = function(){
    var backdrop = Util.byId('fmDetailBackdrop');
    if(backdrop) backdrop.hidden = true;
  };
})(window);
