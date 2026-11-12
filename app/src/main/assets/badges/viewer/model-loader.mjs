import {TextureLoader} from './vendor/three.module.js';
import {GLTFLoader} from './vendor/GLTFLoader.js';

export function createBadgeLoader(htmlImages = false) {
  const loader = new GLTFLoader();
  if (htmlImages) loader.register(parser => {
    // Some Android WebViews expose createImageBitmap but never finish decoding
    // embedded GLB images. HTMLImageElement uses Chromium's ordinary image path.
    const textures = new TextureLoader(parser.options.manager);
    textures.setCrossOrigin(parser.options.crossOrigin);
    textures.setRequestHeader(parser.options.requestHeader);
    parser.textureLoader = textures;
    return {name: 'BADGE_HTML_IMAGE_COMPAT'};
  });
  return loader;
}

function disposeLateModel(gltf) {
  const seen = new Set();
  gltf?.scene?.traverse(object => {
    if (object.geometry && !seen.has(object.geometry)) { seen.add(object.geometry); object.geometry.dispose(); }
    for (const material of Array.isArray(object.material) ? object.material : [object.material]) {
      if (!material || seen.has(material)) continue;
      seen.add(material);
      for (const texture of Object.values(material)) if (texture?.isTexture && !seen.has(texture)) {
        seen.add(texture); texture.dispose(); texture.source?.data?.close?.();
      }
      material.dispose();
    }
  });
}

/** A failed decoder is one failed tile, never an infinite preparation animation. */
export function loadBadgeModel(url, {timeoutMillis = 25_000,
  androidWebView = !!globalThis.BadgeHost, loaderFactory = createBadgeLoader,
  disposeLate = disposeLateModel} = {}) {
  return new Promise((resolve, reject) => {
    let settled = false;
    const timer = setTimeout(() => {
      if (settled) return;
      settled = true; reject(Error('MODEL_DECODE_TIMEOUT'));
    }, timeoutMillis);
    Promise.resolve().then(() => loaderFactory(androidWebView).loadAsync(url)).then(value => {
      if (settled) { disposeLate(value); return; }
      settled = true; clearTimeout(timer); resolve(value);
    }, error => {
      if (settled) return;
      settled = true; clearTimeout(timer); reject(error);
    });
  });
}
