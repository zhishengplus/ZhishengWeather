// Local assets can still stall in a WebView. A scene either owns all its
// resources or releases them; a late HDR must not survive a failed/retried page.
export function loadBadgeSceneResources({loadManifest, loadEnvironment, loadFont = () => {},
  signal, timeoutMillis = 15_000}) {
  return new Promise((resolve, reject) => {
    const controller = new AbortController(); let finished = false, environment = null;
    const release = value => { try { value?.dispose(); } catch {} };
    const detach = () => { clearTimeout(timer); signal?.removeEventListener('abort', abort); };
    const fail = error => {
      if (finished) return;
      finished = true; detach(); controller.abort(); release(environment); environment = null; reject(error);
    };
    const abort = () => fail(Error('SCENE_CANCELLED'));
    const timer = setTimeout(() => fail(Error('SCENE_PREPARATION_TIMEOUT')), timeoutMillis);
    signal?.addEventListener('abort', abort, {once: true});
    if (signal?.aborted) { abort(); return; }
    const start = task => Promise.resolve().then(() => {
      if (finished) throw Error('SCENE_CANCELLED');
      return task(controller.signal);
    });
    Promise.all([start(loadManifest), start(loadEnvironment).then(value => {
      if (finished) { release(value); return null; }
      environment = value; return value;
    }), start(loadFont)]).then(([manifest, hdr]) => {
      if (finished) return;
      finished = true; detach(); environment = null; resolve([manifest, hdr]);
    }, fail);
  });
}
