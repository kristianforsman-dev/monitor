(function(w){
  'use strict';
  var App = w.FMCleanApp;
  if(!App) return;

  App.Layout = {
    ensureRoot: function(){
      if(App.Util.byId('fmCleanApp')) return;

      var root = document.createElement('div');
      root.id = 'fmCleanApp';
      root.innerHTML = ''
        + '<div class="fmWrap">'
        + '  <div class="fmTopbar">'
        + '    <div>'
        + '      <div class="fmTitle">Flow Monitor</div>'
        + '      <div class="fmSub">Ren vy för flöden, detaljer och bevakningar.</div>'
        + '    </div>'
        + '  </div>'
        + '  <div class="fmGrid">'
        + '    <section class="fmCard">'
        + '      <div class="fmCardHead">'
        + '        <div>'
        + '          <div class="fmCardTitle">Flöden</div>'
        + '          <div id="fmRangeMeta" class="fmCardMeta"></div>'
        + '        </div>'
        + '        <div class="fmDoubleHint">Dubbelklick = detaljer</div>'
        + '      </div>'
        + '      <div class="fmFilters">'
        + '        <div class="fmFilterPrimary">'
        + '          <input id="fmSearch" class="fmInput" type="text" placeholder="Sök avsändare, mottagare, meddelandetyp eller status" />'
        + '          <select id="fmSender" class="fmSelect"><option value="">Alla avsändare</option></select>'
        + '          <select id="fmReceiver" class="fmSelect"><option value="">Alla mottagare</option></select>'
        + '          <select id="fmStatus" class="fmSelect"><option value="">Alla statusar</option></select>'
        + '        </div>'
        + '        <div class="fmFilterSecondary">'
        + '          <input id="fmDateFrom" class="fmInput" type="date" />'
        + '          <input id="fmDateTo" class="fmInput" type="date" />'
        + '          <div class="fmPresetBar">'
        + '            <button type="button" class="fmBtn fmPreset" data-preset="1d">1 dag</button>'
        + '            <button type="button" class="fmBtn fmPreset" data-preset="7d">7 dagar</button>'
        + '            <button type="button" class="fmBtn fmPreset" data-preset="30d">30 dagar</button>'
        + '          </div>'
        + '          <button type="button" id="fmClearFilters" class="fmBtn fmBtnGhost">Rensa</button>'
        + '        </div>'
        + '      </div>'
        + '      <table class="fmTable">'
        + '        <thead><tr><th>Avsändare</th><th>Mottagare</th><th>Meddelandetyp</th><th>Status</th><th class="fmNum">Antal</th></tr></thead>'
        + '        <tbody id="fmTableBody"></tbody>'
        + '      </table>'
        + '      <div id="fmTableHint" class="fmHint"></div>'
        + '    </section>'
        + '    <section class="fmCard">'
        + '      <div class="fmCardHead">'
        + '        <div><div class="fmCardTitle">Bevakningar</div><div class="fmCardMeta">Dubbelklick = öppna regel</div></div>'
        + '        <button type="button" id="fmNewMonitor" class="fmBtn fmBtnGhost">Ny</button>'
        + '      </div>'
        + '      <div id="fmMonitorList" class="fmMonitorList"></div>'
        + '    </section>'
        + '  </div>'
        + '</div>'
        + '<div id="fmDetailBackdrop" class="fmDrawerBackdrop" hidden><aside class="fmDrawer"><div class="fmDrawerHead"><div class="fmDrawerTitle">Flödesdetaljer</div><button type="button" id="fmDetailCloseX" class="fmBtn fmBtnGhost">Stäng</button></div><div id="fmDetailBody" class="fmDrawerBody"></div><div class="fmDrawerFoot"><button type="button" id="fmDetailCloseBtn" class="fmBtn">Stäng</button></div></aside></div>';
      document.body.appendChild(root);
      document.body.classList.add('fm-clean-active');
      App.Util.hideLegacyDetails();
    }
  };
})(window);
