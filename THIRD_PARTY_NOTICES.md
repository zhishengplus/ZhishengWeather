# Third-party notices

## Makin-Things weather icons (amCharts-based)

All weather-condition icons, in the app and in every home-screen widget, are
`app/src/main/res/drawable-nodpi/mk_*.webp`. They are rasterized from the static
SVG set of **Makin-Things weather-icons**, which is based on the **amCharts**
weather icons.

- Source: <https://github.com/Makin-Things/weather-icons> (`static/` folder)
- Source commit used: `3793c6a1bf5c52402bb09e5d062b72f9fb5c0410`
- License: MIT, Copyright (c) 2019 Custom cards for Home Assistant
- Based on: amCharts weather icons, <https://www.amcharts.com/free-animated-svg-weather-icons/>,
  licensed under [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/) (CC BY 4.0)
- Changes: the static SVGs were rasterized to 512×512 lossless WebP, cropped to their
  visible bounds and re-centered at a common visual size. Colours and shapes are unchanged.

The full MIT text and the amCharts attribution are packaged in
`app/src/main/assets/licenses/makin-things-weather-icons.txt`.

Condition mapping (`model/ConditionIcons.kt`):
clear-day, clear-night, cloudy-2-day (partly cloudy), cloudy-2-night, cloudy (cloudy/overcast),
rainy-1 (drizzle), rainy-2 (rain), thunderstorms, hail, rain-and-sleet-mix (freezing rain/drizzle),
snowy-2 (snow), rain-and-snow-mix (sleet), fog, haze, dust (sand), wind.

## Meteocons

The themed launcher monochrome layer `app/src/main/res/drawable-nodpi/weather_cloud_sun.png`
is derived from the static flat style of **Meteocons** by Bas Milius. The former
Meteocons weather-condition rasters (`weather_*`, `classic_*`, `vista_day_*`,
`vista_night_*`) were replaced by the Makin-Things icons above and are no longer packaged.

- Source: <https://github.com/basmilius/weather-icons>
- Source commit used for review: `70dfb1d6e30dc9e791cfb0e4c5b5e5e60e972aa0`
- Packaged static assets: `@meteocons/svg-static` version `0.1.0`
- License: MIT

Copyright (c) 2015-2026 Bas Milius

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

### Vista weather artwork update (2026-09-07)

The new theme additionally uses the **Fill** geometry from `@meteocons/svg-static`
version `0.1.0`, npm gitHead `1d821149b94a08f23c85e5042e8a61a3fcd82cf7`.
Original SVGs and their download URLs/SHA-256 hashes are retained in
`artwork/meteocons-fill-0.1.0/`. Source: <https://github.com/basmilius/meteocons>.
The MIT license and copyright notice above also apply to these derivatives.
`scripts/build_vista_weather_icons.cjs` applies distinct light/dark color ramps
and exports `vista_day_*.png` / `vista_night_*.png`. Those exports are no longer
packaged (see Makin-Things above); the source SVGs and script are kept for reference.

## GeoNames offline place data (2026-10-11)

The packaged administrative place index is derived from the pinned CN/HK/MO/TW
exports of [GeoNames](https://www.geonames.org/), under
[Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/).
The app selects ADM1/ADM2/ADM3 records, normalizes names and search aliases,
and builds parent labels from the supplied administrative codes. These are
representative points, not boundaries, street data or a complete official list.
GeoNames is the data source; this adaptation is not endorsed by GeoNames.

Source URLs, snapshot hashes, scope and profile are in
`app/src/main/assets/geo/local_places-manifest.json`; the packaged attribution
is in `app/src/main/assets/licenses/geonames.txt`. To reproduce the index,
download those exact snapshots into an input directory, verify the pinned hashes,
then run `python scripts/build_local_places.py --inputs INPUTS --output OUTPUT`.
The data license applies separately from the application's source-code license.

## Phosphor interface artwork

Interface icons are derived from the regular-weight assets of **Phosphor Icons**.

- Source: <https://github.com/phosphor-icons/core>
- Source commit used: `2b75f3ad12b420c9504ef05df8d2564a28f8500e`
- License: MIT

Copyright (c) 2020 Phosphor Icons

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Commons: Suncalc

Local solar position calculations use org.shredzone.commons:commons-suncalc 3.11 by Richard Körber, under the Apache License 2.0. Source: https://github.com/shred/commons-suncalc/tree/v3.11. The full license and attribution are packaged in app/src/main/assets/licenses/commons-suncalc.txt.

