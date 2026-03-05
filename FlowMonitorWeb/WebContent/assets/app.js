
window.__fmBuild="v74a_20260304104813";
(function(){
  'use strict';
  // Load sibling modules from the same folder as this file (assets/)
  var me = document.currentScript && document.currentScript.src ? document.currentScript.src : '';
  var base='assets/';
  var files=['app-core.js','app-filters.js','app-table.js','app-detail.js','app-monitor.js','app-bootstrap.js'];
  var i=0;
  function loadNext(){
    if(i>=files.length) return;
    var s=document.createElement('script');
    s.src=base+files[i]+'?v74a_20260304104813';
    s.async=false;
    s.onload=function(){ i++; loadNext(); };
    s.onerror=function(){ i++; loadNext(); };
    document.head.appendChild(s);
  }
  loadNext();
})();
