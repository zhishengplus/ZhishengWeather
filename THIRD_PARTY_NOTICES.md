# Third-party notices

## Meteocons

The weather-condition artwork in `app/src/main/res/drawable-nodpi/weather_*.png`
(excluding the original `weather_girl_*` illustrations) is derived from the static
flat style of **Meteocons** by Bas Milius.

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
and exports `vista_day_*.png` / `vista_night_*.png`. Existing classic and widget
artwork is not replaced. The packaged app never downloads these files at runtime.

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

