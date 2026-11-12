// This tiny local script runs before the module graph, so failed imports cannot
// leave the native screen showing a number above an invisible error message.
(() => {
  let closed = false;
  const fail = () => {
    if (closed) return;
    const status = document.getElementById('status');
    if (status) { status.hidden = false; status.textContent = '三维视图暂时无法加载'; }
    try { if (window.BadgeHost) window.BadgeHost.status('render_failed'); } catch (_) {}
  };
  window.addEventListener('error', fail, true);
  window.addEventListener('unhandledrejection', fail);
  window.addEventListener('pagehide', () => { closed = true; }, { once: true });
})();
