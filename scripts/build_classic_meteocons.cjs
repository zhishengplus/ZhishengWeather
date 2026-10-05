/* Static Meteocons fill icons, MIT © Bas Milius. See artwork/METEOCONS-LICENSE.txt. */
const fs = require('node:fs');
const path = require('node:path');
const { Resvg } = require('@resvg/resvg-js');
const root = path.resolve(__dirname, '..');
const source = path.join(__dirname, 'node_modules', '@meteocons', 'svg-static', 'fill');
const target = path.join(root, 'app', 'src', 'main', 'res', 'drawable-nodpi');
const archived = path.join(root, 'artwork', 'meteocons-fill');
fs.mkdirSync(archived, { recursive: true });
const icons = {
  sun: 'clear-day', moon: 'clear-night', cloud_sun: 'partly-cloudy-day',
  cloud_moon: 'partly-cloudy-night', cloud: 'cloudy', clouds: 'overcast',
  rain: 'rain', drizzle: 'drizzle', bolt: 'thunderstorms', sleet: 'sleet',
  snow: 'snow', fog: 'fog', haze: 'haze', sand: 'dust', wind: 'wind',
};
for (const [name, file] of Object.entries(icons)) {
  const svg = fs.readFileSync(path.join(source, `${file}.svg`), 'utf8');
  fs.writeFileSync(path.join(archived, `${file}.svg`), svg);
  const png = new Resvg(svg, { fitTo: { mode: 'width', value: 384 } }).render().asPng();
  fs.writeFileSync(path.join(target, `classic_${name}.png`), png);
}
fs.copyFileSync(path.join(__dirname, 'node_modules', '@meteocons', 'svg-static', 'LICENSE'),
  path.join(root, 'artwork', 'METEOCONS-LICENSE.txt'));
console.log(`Generated ${Object.keys(icons).length} classic icons from Meteocons fill 0.1.0.`);
