// Reproducible, development-only raster export. No runtime network or dependency.
// Source geometry: @meteocons/svg-static 0.1.0, MIT (see THIRD_PARTY_NOTICES.md).
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const sharp = require(process.env.VISTA_SHARP_MODULE || 'sharp');
const root = path.resolve(__dirname, '..');
const sourceDir = path.join(root, 'artwork/meteocons-fill-0.1.0');
const targetDir = path.join(root, 'app/src/main/res/drawable-nodpi');
const icons = {
  sun: 'clear-day', moon: 'clear-night', cloud_sun: 'partly-cloudy-day', cloud_moon: 'partly-cloudy-night',
  cloud: 'cloudy', clouds: 'overcast', rain: 'rain', drizzle: 'drizzle', bolt: 'thunderstorms',
  sleet: 'sleet', snow: 'snow', fog: 'fog', haze: 'haze', sand: 'dust', wind: 'wind',
};
// Surface lighting, precipitation and sun remain distinct. Never darken the entire icon.
const palettes = {
  day: { '#F3F7FE': '#CCDFE8', '#E6EFFC': '#607F94', '#B0BCCD': '#829CAC', '#94A3B8': '#476579',
    '#E2E8F0': '#5F7C8D', '#0A5AD4': '#197496', '#86C3DB': '#6DA6BD', '#72B9D5': '#417F99',
    '#F8AF18': '#BB760D', '#FBBF24': '#F6CA69', '#FCD966': '#B18132' },
  night: { '#F3F7FE': '#E3EFF3', '#E6EFFC': '#8BA9BD', '#B0BCCD': '#91AAB9', '#94A3B8': '#586F82',
    '#E2E8F0': '#B5CDD7', '#0A5AD4': '#70BDD5', '#86C3DB': '#B0D7E2', '#72B9D5': '#74A8BD',
    '#F8AF18': '#D79B32', '#FBBF24': '#F6D58A', '#FCD966': '#DDB76D' },
};
async function main() {
  await fs.mkdir(sourceDir, { recursive: true });
  const manifest = { package: '@meteocons/svg-static', version: '0.1.0', license: 'MIT', assets: [] };
  for (const [name, slug] of Object.entries(icons)) {
    const url = `https://unpkg.com/@meteocons/svg-static@0.1.0/fill/${slug}.svg`;
    const rawPath = path.join(sourceDir, `${slug}.svg`);
    let source;
    try { source = await fs.readFile(rawPath, 'utf8'); }
    catch (e) {
      if (e.code !== 'ENOENT') throw e;
      const response = await fetch(url);
      if (!response.ok) throw new Error(`${response.status}: ${url}`);
      source = await response.text();
      if (!source.startsWith('<svg') || /<script|<foreignObject|href\s*=/i.test(source)) throw new Error(`Unsafe SVG: ${slug}`);
      await fs.writeFile(rawPath, source, { flag: 'wx' });
    }
    manifest.assets.push({ name, slug, url, sha256: crypto.createHash('sha256').update(source).digest('hex') });
    for (const [mode, colors] of Object.entries(palettes)) {
      const svg = source.replace(/#[0-9a-f]{6}/gi, color => colors[color.toUpperCase()] || color);
      await sharp(Buffer.from(svg)).resize(256, 256).png().toFile(path.join(targetDir, `vista_${mode}_${name}.png`));
    }
    console.log(`Exported ${name}: light + dark`);
  }
  await fs.writeFile(path.join(sourceDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
