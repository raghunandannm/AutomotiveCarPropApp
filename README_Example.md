# Adaptive Infotainment

An **Android Automotive OS** application that keeps the driver aware of their speed
relative to the limit in force, and adapts its layout to the driver's seating
position.

Kotlin · Views + Car UI Library · Android 16 (API 36) · AAOS emulator

![Dashboard](docs/screenshots/01-dashboard.png)

---

## Contents

- [What it does](#what-it-does)
- [Features](#features)
- [Architecture](#architecture)
- [Getting started](#getting-started)
- [Running the demo](#running-the-demo)
- [Design decisions](#design-decisions)
- [Known limitations](#known-limitations)

---

## What it does

The dashboard has three zones — the **speed gauge**, the **limit sign** (showing the
limit currently applied, with its source named beneath), and the **custom limit
card**. Status chips run along the bottom, with Settings at the far corner.

Colour is consistent throughout: **green** within the limit, **amber** approaching
it, **red** exceeding it.

The application implements four user stories.

| # | User story | Implemented by |
|---|---|---|
| 1 | Layout adjusts to steering position | `SteeringPositionRepository`, `constraints_dashboard_{lhd,rhd}.xml` |
| 2 | Custom speed limit | `SettingsRepository`, custom limit card on the dashboard |
| 3 | Speed monitoring | `VehicleSpeedRepository`, `SpeedLimitRepository`, `SpeedMonitor` |
| 4 | Overspeed alert | `OverspeedAlertController`, `SpeedLimitSignView` |

---

## Features

### 1. Infotainment layout adjustment

> *As a driver, I want the infotainment layout to automatically adjust based on my
> steering position, so that the interface remains convenient and safe to interact
> with while driving.*

The application reads `INFO_DRIVER_SEAT` from the vehicle and arranges the display
around the driver. What the driver needs while moving sits nearest to them; controls
that can wait sit furthest away. The full screen width is used in both
configurations — the three zones form a spread chain whose **order reverses** rather
than bunching to one edge.

| Left-hand drive | Right-hand drive |
|---|---|
| ![LHD](docs/screenshots/02-lhd.png) | ![RHD](docs/screenshots/03-rhd.png) |

Layout changes animate via `TransitionManager` rather than jumping. The bottom chip
names the active layout.

### 2. Custom speed limit

> *As a driver, I want the ability to set a custom speed limit for my drive, so that
> I can personalise the speed-limit threshold based on my preferences or specific
> driving conditions.*

The custom limit card sits on the dashboard, not in Settings, because this is a
per-journey decision made in context. Three controls:

- **Switch** — turns the custom limit on and off. Switching it on adopts the current
  speed, rounded to the nearest 5 km/h, so the value is never stale from an earlier
  drive.
- **Minus / plus** — adjust in 5 km/h steps. Deliberately coarse: precise input while
  moving is a driver-distraction hazard.
- **Use current speed** — re-adopts the present speed in one tap, the cruise-control
  gesture.

| Active | Inactive |
|---|---|
| ![Custom active](docs/screenshots/04-custom-active.png) | ![Custom inactive](docs/screenshots/05-custom-inactive.png) |

Three signals change together in the inactive state — card surface, accent border,
and control contrast — with the buttons genuinely disabled rather than only dimmed.
The value stays visible so the driver can see what would be restored.

### 3. Speed monitoring

> *As a driver, I want the system to compare my vehicle's speed with the correct
> speed limit — using the custom speed limit if one is set, otherwise using the
> location's default speed limit — so that I can remain aware of whether I am driving
> within legal or configured limits.*

`PERF_VEHICLE_SPEED` is sampled continuously and compared against whichever limit
applies:

| Situation | Limit applied |
|---|---|
| Custom limit on | The custom value |
| Custom limit off | The location's default |
| No location fix yet | A conservative default (60 km/h) |

The location default comes from a lookup table of geofenced zones, evaluated
first-match-wins so smaller zones take precedence over the larger ones containing
them.

| Urban arterial — 70 | Highway corridor — 105 |
|---|---|
| ![Arterial](docs/screenshots/06-zone-arterial.png) | ![Highway](docs/screenshots/07-zone-highway.png) |

### 4. Overspeed alert

> *As a driver, I want to receive a warning when my vehicle speed exceeds the allowed
> speed limit, so that I can immediately slow down and avoid potential risks or
> violations.*

The warning is delivered through sight, sound and touch simultaneously. **No sentence
appears on screen** — prose cannot be read safely at speed. Instead the gauge arc and
digits turn red, a halo pulses around the limit sign, the status chip reads *Slow
down*, and a short tone plays with a haptic pulse.

| Approaching | Overspeed |
|---|---|
| ![Approaching](docs/screenshots/08-approaching.png) | ![Overspeed](docs/screenshots/09-overspeed.png) |

Two behaviours keep the alert usable rather than irritating:

- **Hysteresis** — the alert clears 2 km/h below the threshold that triggered it, so
  the display does not flicker when speed hovers on the boundary.
- **Rate limiting** — the tone repeats at most once every 5 seconds during a sustained
  overspeed.

Alert tolerance (0–15 km/h) and the audible alert are configurable in Settings.

![Settings](docs/screenshots/10-settings.png)

---

## Architecture

```
VHAL ──► CarPropertyManager ──► VehicleSpeedRepository ─────┐
                             └► SteeringPositionRepository ─┤
LocationManager ─► LocationRepository ─► SpeedLimitRepository┤
SharedPreferences ─► SettingsRepository ────────────────────┤
                                                            ▼
                                                      SpeedMonitor
                                                            │
                                              DashboardUiState (immutable)
                                                            │
                                         DashboardViewModel ─► DashboardFragment
                                                            └► OverspeedAlertController
```

Every source is a cold `Flow`. `SpeedMonitor` combines five of them into a single
immutable `DashboardUiState`; the UI renders that state and nothing else.
`stateIn(WhileSubscribed)` in the ViewModel means VHAL callbacks are unregistered
while the UI is not visible.

The domain layer has no Android dependencies — `SpeedMonitor` and the model types are
plain Kotlin.

```
app/src/main/java/com/example/adaptiveinfotainment/
├── car/          CarConnectionManager, VehicleSpeedRepository, SteeringPositionRepository
├── location/     LocationRepository, SpeedLimitRepository, SpeedLimitLookupTable
├── settings/     SettingsRepository
├── domain/       Model, SpeedMonitor
├── alert/        OverspeedAlertController
├── ui/           MainActivity, dashboard/, settings/, widget/
└── di/           AppContainer
```

Dependency injection is a hand-rolled `AppContainer` — the graph is small enough that
Hilt would add complexity without payoff.

**Custom views.** `SpeedGaugeView` draws a 270° arc with an internal `ValueAnimator`,
so the ~5 Hz VHAL updates render as smooth motion rather than stepping.
`SpeedLimitSignView` draws a Vienna Convention road sign, chosen because a driver
recognises the shape without reading it.

---

## Getting started

Everything below is done from Android Studio — no command line required.

### 1. Prerequisites

- **Android Studio** (Otter / 2025.x or newer)
- **JDK 17** (bundled with Android Studio)

### 2. Install the SDK components

*Tools → SDK Manager → SDK Platforms*, tick **Show Package Details**, and install:

- **Android SDK Platform 36**
- An **Automotive** system image — see the warning below

Then under *SDK Tools*: **Android Emulator**, **Android SDK Platform-Tools**, and
**Android SDK Build-Tools 36**.

> ⚠️ **Choose the right system image.** It must be named **"Automotive"** or
> **"Automotive with Google APIs"**. **"Automotive with Play Store" will not work** —
> that image targets Play-distributed apps and deliberately withholds the Car API, so
> installation fails with `INSTALL_FAILED_MISSING_SHARED_LIBRARY`.
>
> If no Automotive image is offered for API 36, use API 34 or 35 instead. The project
> needs no changes — `minSdk` is 33.

### 3. Create the emulator

*Tools → Device Manager → Add device*:

1. Set **Category = Automotive** first — this filters the list to AAOS images only.
2. Pick a hardware profile, e.g. **Automotive (1408p landscape)**. The dashboard is
   designed for this width.
3. On the system image screen, confirm the name contains **Automotive**. The hardware
   profile name alone does not guarantee an AAOS image.
4. Allocate at least 2 GB RAM, then Finish.

Launch it once and let it boot fully before running the app.

### 4. Open the project

*File → Open*, select the cloned `AdaptiveInfotainment` folder — the one containing
`settings.gradle.kts` — and click **Trust Project**. Gradle sync starts automatically
and will offer to install anything missing; accept.

### 5. Run

Select the **app** run configuration and your Automotive AVD from the target dropdown,
then press **Run** (▶).

On first launch the app requests two permissions — **Location** and **Car speed**.
Grant both. `CAR_INFO` is a normal permission and is granted at install time.

### 6. Enable location on the emulator

Some AAOS images boot with location services off, which leaves the app showing its
fallback limit of 60 km/h regardless of position.

In the emulator, open the car's **Settings → Location** and switch it on. Then
**restart the app** — it subscribes to location providers at startup, so enabling
location mid-session will not take effect until the next launch.

---

## Running the demo

Everything is driven from the emulator's **Extended controls** panel — the `⋯` button
on the emulator toolbar. Changes take effect in the app immediately.

### Injecting speed

**Extended controls → Car data → Car sensor data → Car speed.** Set the unit dropdown
to **kmph** and drag the slider; the gauge follows in real time.

<details>
<summary>Command-line alternative</summary>

`PERF_VEHICLE_SPEED` (`0x11600207`) is a float in **metres per second**:

```bash
adb shell cmd car_service inject-vhal-event 0x11600207 20   # 72 km/h
```
</details>

### Setting location

**Extended controls → Location → Single points.** Type the values into the
**Latitude** and **Longitude** fields — not the search box, which is a geocoder and
will not accept a coordinate pair — then click **SET LOCATION**. Use **Save point** to
keep each one for single-click recall.

| Latitude | Longitude | Result |
|---|---|---|
| `37.4237` | `-122.0855` | 30 — School zone |
| `37.4220` | `-122.0841` | 50 — Residential |
| `37.4400` | `-122.0600` | 70 — Urban arterial |
| `37.4550` | `-122.1400` | 105 — Highway corridor |
| `48.4200` | `10.9000` | 60 — fallback, no zone |

Zones are defined in
[`SpeedLimitLookupTable.kt`](app/src/main/java/com/example/adaptiveinfotainment/location/SpeedLimitLookupTable.kt).

### A sequence covering all four stories

1. **Location → Single points**, set `37.4220 / -122.0841` → the sign shows **50**,
   captioned *Residential*.
2. **Car data → Car speed**, set **43 kmph** → green, *Within limit*.
3. Raise the speed to **58 kmph** → gauge turns red, halo pulses, tone and haptic
   fire — **Story 4**.
4. On the dashboard, switch the **custom limit** on and step it up to **80** → the
   alert clears and the caption reads *Custom limit* — **Stories 2 and 3**.
5. Move the location to `37.4550 / -122.1400` with the custom limit off → the sign
   changes to **105**, captioned *Highway corridor* — **Story 3**.
6. **Settings → Force right-hand drive** → the layout mirrors — **Story 1**.

---

## Design decisions

**`INFO_DRIVER_SEAT` is a `STATIC` property.** The vehicle fixes it at boot and it
cannot be changed at runtime — `inject-vhal-event` has no effect, and no Extended
Controls panel exposes it, because a static property changing at runtime would violate
the specification. The production path reads the real property on every launch; a
developer override in Settings substitutes a value so both layouts can be demonstrated
on one image. Changing it for real means editing the reference VHAL config in an AOSP
tree and rebuilding.

**`<uses-library android:required="false" />`.** On current AAOS images `android.car`
lives on the **bootclasspath** (`/system/framework/android.car.jar`) rather than being
published as a declared shared library — there is no `android.car.xml` in
`/system/etc/permissions`. A hard `required="true"` therefore fails to install even
though the classes are present. `required="false"` installs correctly on both layouts.

**Custom limit on the dashboard, tolerance in Settings.** The custom limit is a
per-drive decision; alert tolerance is set once and forgotten. Placement follows how
often each is touched, not how similar they look.

**Hysteresis anchored to the trigger point.** The alert clears relative to
`limit + tolerance`, not to `limit`. Anchoring to the limit produced an asymmetric
band that surprised users — trigger at 110, clear at 103 — where anchoring to the
threshold gives trigger at 110, clear at 108.

**Coarse stepper input.** 5 km/h steps with ~76dp targets, rather than a slider.
Sliders require sustained precision, which is the wrong input model for a vehicle.

---

## Known limitations

- **No unit tests.** `SpeedMonitor.classify()` is pure and synchronous and would be
  straightforward to cover; it is the most obvious gap.
- **`LocationRepository` does not recover** if location services are enabled *after*
  the app starts — it subscribes only to providers enabled at collection time. Restart
  the app. The fix is a `PROVIDERS_CHANGED_ACTION` receiver.
- **`CarUxRestrictionsManager` is not integrated.** The custom limit steppers remain
  editable while moving; a production app would disable them above a speed threshold.
- **Speed limits come from a static table**, not a map provider. Google offers no
  public speed-limit API; OpenStreetMap's `maxspeed` tag via Overpass would be the
  realistic replacement, but coverage is patchy and map-matching a GPS point to the
  correct way is non-trivial.
- **`SpeedMonitor` holds a mutable latch** (`overspeedLatched`) that is safe only
  because the flow has a single collector. `runningFold` would make this structural
  rather than conventional.
- **Distribution.** Apps using `CarPropertyManager` cannot be distributed via Google
  Play; they are preinstalled into the system image by the OEM. This targets that tier.

---

## Tech stack

| | |
|---|---|
| Language | Kotlin |
| UI | Android Views + [Car UI Library](https://source.android.com/docs/automotive/hmi/car_ui) `2.6.0` |
| Async | Coroutines + Flow |
| Vehicle data | `android.car` (`CarPropertyManager`) |
| Persistence | `SharedPreferences` |
| Min / target SDK | 33 / 36 |
