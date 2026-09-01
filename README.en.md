![Zhisheng Weather · ZHISHENG WEATHER TERMINAL](assets/banner.png)

<p align="center">
  <img src="assets/app-icon.png" width="96" alt="Zhisheng Weather app icon"/><br/>
  <b>Open it and get the weather.</b><br/>
  A dense, phosphor-terminal weather app for Android. No ads, accounts, or analytics SDK.
</p>

<p align="center">
  <a href="https://github.com/zhishengplus/ZhishengWeather/releases">
    <img alt="Download the public APK" src="https://img.shields.io/badge/DOWNLOAD_PUBLIC_APK_·_v0.1.3-FF6F1E?style=for-the-badge&labelColor=10151C"/>
  </a>
</p>

<p align="center">
  <a href="https://github.com/zhishengplus/ZhishengWeather"><img alt="GitHub stars" src="https://img.shields.io/github/stars/zhishengplus/ZhishengWeather?style=flat-square&labelColor=10151C&color=FF6F1E"/></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-31C9DB?style=flat-square"/>
  <img alt="Current version 0.1.3" src="https://img.shields.io/badge/current-0.1.3-31C9DB?style=flat-square"/>
  <img alt="No ads, accounts, or tracking" src="https://img.shields.io/badge/ads_·_accounts_·_tracking-none-31C9DB?style=flat-square"/>
  <img alt="MIT License" src="https://img.shields.io/badge/license-MIT-31C9DB?style=flat-square"/>
</p>

<p align="center">
  <a href="README.md">简体中文</a> · <b>English</b>
</p>

---

## Screens

<table>
  <tr>
    <td align="center"><a href="assets/screenshot-home.jpg"><img src="assets/screenshot-home.jpg" width="160" alt="Zhisheng Weather home screen"/></a></td>
    <td align="center"><a href="assets/screenshot-details.jpg"><img src="assets/screenshot-details.jpg" width="160" alt="Telemetry and air quality"/></a></td>
    <td align="center"><a href="assets/screenshot-cities.jpg"><img src="assets/screenshot-cities.jpg" width="160" alt="Saved cities"/></a></td>
    <td align="center"><a href="assets/screenshot-add-city.jpg"><img src="assets/screenshot-add-city.jpg" width="160" alt="City search"/></a></td>
    <td align="center"><a href="assets/screenshot-settings.jpg"><img src="assets/screenshot-settings.jpg" width="160" alt="Settings"/></a></td>
  </tr>
  <tr>
    <td align="center"><sub>Home</sub></td>
    <td align="center"><sub>Telemetry &amp; air</sub></td>
    <td align="center"><sub>Cities</sub></td>
    <td align="center"><sub>Search</sub></td>
    <td align="center"><sub>Settings</sub></td>
  </tr>
</table>

<p align="center"><sub>Click a screenshot to open the full image.</sub></p>

## About the project

When I check the weather, I usually want four answers quickly: the current temperature, when rain is due, whether the air is decent, and whether the next few days will turn colder. Zhisheng Weather puts those answers in one vertical feed. There is no splash ad and no account screen.

The interface uses a phosphor-terminal look: black background, thin dividers, cyan for regular data, and orange for signals that need attention. Current conditions, alerts, hourly weather, and short-term precipitation come first. Air quality, life indices, and moon data follow below.

## What it shows

- Current conditions, feels-like temperature, wind, and pressure for your location or saved cities
- A 24-hour forecast, 15-day high/low outlook, and the next two hours of precipitation
- Weather alerts, six air-pollutant readings, and common life indices
- Sunrise, sunset, moon phase, moonrise, moonset, and yesterday's weather
- Saved cities with a hold-and-slide city deck, reorderable home modules, and five terminal-watch widget sizes
- Launcher shortcuts for refresh, city search, and settings

Every weather condition has its own background effect, drawn beneath the readings. Intensity is adjustable, and every effect can be turned off. Temperature, wind-speed, and pressure units are configured separately. Page sections can also be hidden.

Fields that a provider does not supply stay empty; the app does not fill them with estimates.

## Public release

GitHub Releases distribute only the formal public APK, which is the only package regular users need. It works with the no-setup public data path. Optional QWeather or Caiyun credentials can be entered in the in-app lab and remain on the device.

The repository's `release`, `performance`, and `previewPublic` variants exist for maintainer development, device upgrades, and side-by-side acceptance testing. They are not community release packages. In-app direct updates are enabled only in the formal public build so later releases can replace it with the same package name and signing identity.

## Data providers

The app connects to QWeather, Caiyun, Xiaomi's public weather API, and Open-Meteo. Settings can select a provider manually or use Auto, and show which provider actually returned the selected city's data.

| Provider | Setup | Main coverage |
|:--|:--|:--|
| QWeather | Lab wizard, or compile-time `local.properties` | Current conditions, alerts, hourly/daily, minute precipitation, AQI, life indices |
| Caiyun | Lab Token | Current/hourly/daily, minute precipitation, AQI, alerts |
| Xiaomi public weather API | None | Weather in China, city search, yesterday's weather, and typhoon support data; no cross-provider fill when selected manually |
| Open-Meteo | None | Global current/hourly/daily, AQI, 15-minute precipitation, and gap filling |

Auto keeps Xiaomi's public API as the authority for current conditions and precipitation. Open-Meteo only fills telemetry, hourly, or daily fields Xiaomi did not return, and replaces Xiaomi only when the entire Xiaomi request fails. QWeather and Caiyun stay out of Auto and are only used after they are unlocked in developer mode. Selecting any provider manually keeps all displayed provider data pure.

Moon phase is calculated on-device for the selected city's date. If the provider does not return moonrise or moonset, the app calculates them from the date and coordinates without making another request.

QWeather requests use Ed25519-signed JWTs, or an API KEY. `assemblePublicRelease` and `assemblePreviewPublic` always clear compile-time credentials. Tokens entered in the lab stay on the device, out of backups and out of the public APK.

## Icons

<p align="center">
  <img src="assets/app-icon.png" width="144" alt="Zhisheng Weather app icon"/>
</p>

Version 0.1.3 uses the weather character portrait by default. Her hair clip keeps the classic sun, cloud, and rain mark, while the dark base, cyan signal color, and orange accents stay aligned with the terminal interface. The original launcher icon remains available under Settings → Interface → App icon.

<p align="center"><img src="assets/icons_grid.png" width="560" alt="Zhisheng Weather icon set"/></p>

The app also includes 15 custom weather glyphs for clear, cloudy, overcast, fog, rain, thunderstorms, snow, wind, and sleet. They were drawn for this project rather than assembled from a general icon library.

## Install

1. Download the latest public APK from [Releases](https://github.com/zhishengplus/ZhishengWeather/releases).
2. Install it on Android 8.0 or later.
3. The first launch shows Beijing. Use search to save your own cities.

The APK is distributed through GitHub. Android may ask you to allow the current app to install unknown-source files. That prompt refers to the download channel; Zhisheng Weather is not requesting another system permission.

## Version 0.1.5-beta3

- Restores the compact five-day home forecast and caps the hourly carousel at 24 readable slots, with fixed positions for conditions, rain chance, and high/low temperatures
- Adds a dedicated 15-day view with a dimmed yesterday column and a more natural character-led outlook summary
- Adds the previous seven days, same-date comparisons, and five- or ten-year history views; each record names its year and unusable records are omitted instead of shown as placeholders
- Adds map-style radar controls, frame scrubbing, and smooth playback, with separate messages for clear nearby echoes and unavailable coverage
- Adds typhoon tracks with observed positions, intensity, wind radii, forecasts from multiple agencies, cache time, and clear stale-data notices
- Separates character briefings from alerts and tailors guidance to temperature, wind, precipitation, air quality, humidity, UV, visibility, and time of day; a text-only Tips mode is also available
- Rechecks temperature, wind, precipitation, pressure, visibility, and air-quality handling across QWeather, Caiyun, Xiaomi, and Open-Meteo without substituting unrelated values for missing fields
- Reorganizes Settings and adds Japanese UI, home briefing choices, and transparent, glass, or opaque widget backgrounds
- Makes the new Weather Hub the default landscape style while retaining the classic view; full Settings and a return-to-portrait control are available in landscape
- Adds city favorites that stay above regular cities while preserving the order within each group
- Checks for updates quietly at launch and shows a badge in Settings when one is available, without pop-ups or automatic downloads
- Fixes landscape cold-start routing on affected Samsung and realme devices, plus night icons, alert colors, short-term precipitation, and large-screen layouts
- Expands the community contributor list to 558 names

## Version 0.1.3

- Adds an optional landscape weather clock; disabling it keeps the app in portrait
- Adds precise positioning with street-level labels when available and safe city fallback
- Makes Xiaomi authoritative in Auto while other public data only fills fields Xiaomi did not return; manual providers remain pure
- Fixes QWeather credential state, hourly/daily parsing, precipitation intensity, air quality, and life-index display
- Requests longer hourly ranges and additional life indices when the QWeather account permits them
- Fixes conflicting Caiyun current summaries, short daily ranges, and inconsistent blocks
- Lets developer mode choose individual telemetry and life-index cards, with adaptive balanced layouts
- Adds day-of-month labels while keeping weekday, icon, probability, and temperature columns aligned
- Adds a subtle month divider only when the forecast crosses into a new month
- Reworks short-term precipitation around start/stop timing, peak intensity, and fixed 30-minute marks
- Shows the provider, native time resolution, and update time instead of inventing minute-level precision
- Balances the dry short-term precipitation card with a complete two-hour clear-window readout
- Keeps current conditions, the hourly “now” column, and short-term precipitation on the same clock
- Uses the city’s local date and time for highs/lows, update stamps, and widgets
- Labels current precipitation as mm/h, and does not treat a peak rate as a daily rainfall total
- Fixes southern/western coordinates, single-city scrolling, and small-widget text sizing
- Adds an Intense ambience level and improves effect visibility at night
- Gives home-screen widgets a layered translucent glass shell that lets wallpaper show through
- Refines widget borders, type, and alignment; larger widgets add hourly trends and useful daily context, while refresh actions show clear feedback
- Adds the weather character launcher icon with an in-app switch back to the classic weather mark
- Extends the app surface behind the gesture navigation area to remove the detached black strip

## Version 0.1.1

Version 0.1.1 is a bug-fix update based on 0.1.0.

- Fixes configured QWeather occasionally appearing as unavailable
- Fixes QWeather showing only “Now” in the hourly forecast and Caiyun showing too few daily forecasts
- Aligns telemetry cards and restores available visibility, dew point, cloud cover, and gust readings
- Uses each city's UTC offset for hourly, weekday, date, and widget time labels
- Corrects southern/western coordinate labels and improves home scrolling with a single saved city
- Improves the date and update-time text on small widgets

## Version 0.1.0

Version 0.1.0 is a major update to home interaction, weather effects, data providers, and widgets.

### Cities and home screen

- With two or more saved cities, long-press the breathing light at the bottom to open the city deck. After the haptic pulse, keep holding, slide left or right, and release on the centered city
- Push the open deck upward until the second haptic pulse to pin it, then release your thumb, browse, and tap a city
- Reorder home weather modules in Settings, or restore the default order with one action
- A three-page update guide appears once on first launch. Open it again by tapping Settings → About → Version

### Weather effects and interface

- Clear day, clear night, partly cloudy, overcast, rain, sleet, snow, thunderstorms, fog, haze, sand, and wind each have a distinct terminal background with smooth condition transitions
- Rain uses a data-rain layer. Ambience stays behind the readings, has adjustable intensity, and can be disabled
- Developer mode includes a simulated-weather preview for every ambience without changing the real home screen
- Settings are reorganized; dimmer green and blue accent options are available, and light-mode icons use condition-appropriate colors

### Data and presentation

- Added Caiyun Weather, plus guided setup and connection tests for QWeather JWT / API KEY and Caiyun Token
- Home high/low values follow the time of day. Missing provider data stays empty rather than being estimated
- Unified minute precipitation, rain trend, precipitation probability, and wind direction presentation; fixed invalid percentages, distorted precipitation cards, and conflicting summaries
- The minute-precipitation card stays hidden when dry, and feels-like temperature is omitted when it is effectively the same as air temperature

### Widgets and community

- Widgets now come in five sizes: 4×1, 2×2, 4×2, 2×4, and 4×4, with terminal-device frames and breathing status lights
- Fixed fluorescent-green sun, cloud, and precipitation icons in light mode
- Added a community contributors list under About

Notifications are still not included.

## Version 0.0.8

This update mainly fixes inconsistent weather display.

1. Fixed the home screen still saying "no rain" while it is raining
2. Fixed the hourly forecast labeling the next hour as "Now"
3. Improved short-term precipitation status so it no longer stays on "raining" after rain has stopped
4. Improved daily weather display: when day and night conditions differ, the more significant weather is shown, and the expanded row can show phrases such as "sunny turning to thunderstorms"
5. Fixed overcast being shown as light rain

Notifications are not included in this version.

## Version 0.0.6

Open the app and see the next two hours at a glance. A one-line briefing sits under the big temperature (when rain starts, alerts, tomorrow's swing). The precipitation card says "rain starts in X minutes". The public Xiaomi feed now includes the 120-minute nowcast series. Tap a daily row for sunrise, sunset, and moon data. MIUI forced-dark is disabled, and light theme no longer flashes black on cold start. Notifications are not in this release.

## Version 0.0.5

Version 0.0.5 is mostly about themes. Alongside dark mode there is now a "cold jade" light theme — cool gray paper with jade as the data color — and the widgets change skin with it. It also fixes two bugs that had been hiding in plain sight.

- Three theme modes — dark / light / follow system — applied instantly, widgets restyle to match
- Fixed follow-system showing the opposite of the system theme
- Fixed alerts from the QWeather provider ignoring the four-tier national color scale (they all showed red)
- Added an open-source repository link in Settings — stars welcome
- A full polish pass on light-mode text, icons, and the daily temperature bars

Release checks cover 46 unit tests, Android Lint, and Debug, public Release, and full Release builds.

## Version 0.0.5.1

A widget polish pass: date, update time, feels-like, and hourly/daily text got a size bump; the widget-picker previews are redrawn (sample city is now Shanghai, matching the real layouts); widgets now follow only the system dark mode — switching the in-app theme no longer restyles the desktop widgets.

## Contributing

Report reproducible problems through [GitHub Issues](https://github.com/zhishengplus/ZhishengWeather/issues). Include the app version, phone model, Android version, active provider, and necessary screenshots; redact Tokens, API keys, and other private credentials.

Community contributors: `PPQ1028`, `Uinuan1`, `KZzzzo`, `睡觉了寂`, `微生之最`, `r1file`, `vsqesy3721`, `茉莉羽`, `陈大橙`, `飞667`, `一杯冰美式、、`, `M1ralce`, `紅星照耀中國`, `我爱跑步`, `河鱼天雁`, `你的心里没点高数吗`, `周月星斗`, `无敌战神暴王龙`, `control3`, `明珠有泪`, `Gstar_`, `伍拾两HZ`, and `寡欲老公猪`.

## Build from source

You need JDK 17 and Android SDK 34. The Gradle Wrapper is included.

```bash
git clone https://github.com/zhishengplus/ZhishengWeather.git
cd ZhishengWeather
```

Without credentials, the project builds the public data path. To use QWeather as the primary feed, put your SDK path and credentials in the root `local.properties`; the file is already ignored by Git.

```properties
sdk.dir=<Android SDK path>
qw.host=<API host>
qw.project_id=<Project ID>
qw.kid=<Key ID>
qw.private_key=<single-line Ed25519 private key>
```

```bash
./gradlew assembleDebug                     # Windows: .\gradlew.bat assembleDebug
./gradlew assembleRelease                   # maintainer-local build; configure your own signing key
./gradlew assemblePublicRelease             # public build; clears credentials and uses the in-repo public key
./gradlew assemblePreviewPublic             # maintainer side-by-side test build; do not upload to Releases
./scripts/package_release.ps1               # package the GitHub public APK and SHA-256
./scripts/package_release.ps1 -IncludeDevelopmentBuilds  # also package local development variants
```

The bundled `keystore/public.jks` only keeps public builds upgrade-compatible with each other. It is not a private signing identity.

Main stack: Kotlin 2.0.21, Jetpack Compose, Material 3, ViewModel / StateFlow, Retrofit, OkHttp, kotlinx-serialization, DataStore, and BouncyCastle. `minSdk 26`, `targetSdk 34`. See [CONTRIBUTING.md](CONTRIBUTING.md) for code layout and commit conventions.

## Permissions and data

The app uses network, update-install, and optional location permissions:

| Permission | Purpose |
|:--|:--|
| Internet | Fetch weather and city-search data |
| Network state | Check whether the device is online |
| Install packages | Open Android's installer only after a manual update download in the formal public build; never installs silently |
| Approximate location | Optional; requested only after location is enabled and a new fix is requested |
| Precise location | Optional; requested only when street-level positioning is explicitly enabled |

There is no ad SDK, analytics, account system, or project-operated backend. Saved cities and settings stay on the device. Weather requests send the selected city's coordinates to the active provider. Location coordinates are also used to resolve a city name.

After location is enabled and permission is granted, the app rechecks the city at intervals when returning to the foreground. It does not collect location in the background. Relevant code is under [`app/src/main/kotlin/com/zhisheng/weather/data`](app/src/main/kotlin/com/zhisheng/weather/data).

## Known limitations

- The public build ships without QWeather credentials; they can be added in the lab. Official alerts and life indices depend on account permissions
- Open-Meteo short-term precipitation uses 15-minute intervals rather than minute-by-minute radar nowcasting
- Typhoon and yesterday's weather depend on an auxiliary feed; those sections stay empty when it returns nothing
- Alerts are deduplicated by exact title, so differently worded copies of one alert may both appear
- This is an early release. For safety decisions, follow your local meteorological authority

## Changelog

<details open>
<summary><b>0.1.3 // STABLE RELEASE</b></summary>

- Adds the landscape weather clock, street-level positioning, selectable telemetry and life indices, and stronger night ambience
- Makes Xiaomi authoritative in Auto and fixes QWeather, Caiyun, hourly/daily, and short-term precipitation consistency issues
- Rebuilds the translucent glass widgets with consistent borders, typography, alignment, refresh feedback, and light/dark weather colors
- Adds the weather character launcher icon while retaining the classic option, and extends the interface behind gesture navigation

</details>

<details open>
<summary><b>0.1.1 // BUG FIXES</b></summary>

- Fixes QWeather switching, QWeather/Caiyun forecast display, missing telemetry fields, and uneven telemetry rows
- Corrects overseas-city time zones, hemisphere labels, and widget times
- Prevents the city-deck gesture from intercepting scrolling when only one city is saved
- 137 unit tests pass; Android Lint reports zero errors; personal and public builds compile

</details>

<details>
<summary><b>0.1.0 // CITY DECK</b></summary>

- Bottom city deck with hold-and-slide switching or an upward push to pin and tap
- Reorderable home modules with a restore-default action
- Per-condition terminal ambience, developer preview, and dimmer accent options
- Guided QWeather / Caiyun setup with connection tests and consistent weather presentation
- Five terminal-device widgets with breathing status lights and light-mode colors
- First-launch update guide and community contributors

</details>

<details>
<summary><b>0.0.8 // DATA</b></summary>

- Fixed the home screen still saying "no rain" while it is raining
- Fixed the hourly forecast labeling the next hour as "Now"
- Improved short-term precipitation status after rain has stopped
- Improved daily weather display when day and night conditions differ
- Fixed overcast being shown as light rain

</details>

<details>
<summary><b>0.0.6 // NOWCAST</b></summary>

- One-line briefing under the big temperature: rain timing, alerts, tomorrow's swing
- Precipitation card says when rain starts; public Xiaomi feed now includes the 120-minute series
- Daily rows expand for sunrise, sunset, and moon data
- Disabled forced-dark; light theme no longer flashes black on cold start
- Precip chart and hourly row height stay consistent across providers; a locked source no longer falls back to another provider's cache

</details>

<details>
<summary><b>0.0.4 // WIDGET OVERHAUL</b></summary>

- Rebuilt all three widgets with rounded panels, larger type and glyphs, and a clearer information hierarchy
- Included the 0.0.3 test stability patches; 41 unit tests pass and Lint reports zero errors
- Offline cache fallback, background widget refresh, provider circuit breaking, color-coded alerts

</details>

<details>
<summary><b>0.0.3 // STABILITY PASS</b></summary>

- Fixed moon data, widgets, launcher shortcuts, city relocation, and provider status
- Adjusted the Vivid ambience level without changing Subtle
- Standardized the terminal name and replaced the launcher icon
- 15 unit tests pass, Lint reports zero errors, and public and full builds compile

</details>

<details>
<summary><b>0.0.2 // FEED SELECT</b></summary>

- Added provider selection, three widget sizes, weather ambience, and optional location
- Made Open-Meteo available as a standalone provider
- Fixed night icons, the hourly curve, back navigation, and rotation state

</details>

**0.0.1 Preview** was the first public build, with the phosphor-terminal interface, 15 weather glyphs, three-provider data path, and public build task.

See [Releases](https://github.com/zhishengplus/ZhishengWeather/releases) for complete version notes.

## License

- Code is released under the [MIT License](LICENSE). Issues and pull requests are welcome.
- The interface, launcher icon, weather glyphs, and terminal copy are project artwork. Keep attribution when reusing them.
- Weather data belongs to [QWeather](https://www.qweather.com/), [Open-Meteo](https://open-meteo.com/), and the relevant providers behind Xiaomi's public weather API, and is provided for reference only.
- Keep personal QWeather credentials out of public repositories.

---

<p align="center"><sub>ZHISHENG WEATHER TERMINAL // PATTERN BLUE · Kotlin / Android</sub></p>
