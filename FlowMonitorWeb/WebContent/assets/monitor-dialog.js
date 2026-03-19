// ---- HIDE OLD DIALOGS + STOP BUBBLE -----------------------------
document.addEventListener('DOMContentLoaded', () => {
  const hideOld = () =>
    document.querySelectorAll('.modal,.fmModal,[data-old-dialog]')
            .forEach(el => el.style.display = 'none');

  const nyBtn =
    document.getElementById('fmNewMonitor') ||
    Array.from(document.querySelectorAll('button,a'))
         .find(b => b.textContent.trim() === 'Ny');

  if (nyBtn && !nyBtn.dataset.newDlgBound) {
    nyBtn.dataset.newDlgBound = '1';
    nyBtn.addEventListener('click', e => {
      e.stopImmediatePropagation();
      e.preventDefault();
      hideOld();
      window.FM && FM.MonDlg && FM.MonDlg.open();
    }, { capture: true });
  }
});
