# VWAP Suite (D/W/M)

A MotiveWave study that plots Daily, Weekly and Monthly VWAP with ±1 and ±2 standard deviation bands in a single overlay, with compact floating labels pinned to the right edge of the chart.

## Features

- **Daily, Weekly, Monthly VWAP** in one study. Each period has its own tab and can be turned off.
- **Labels without lines**: the line, bands and fills can all be hidden and the floating labels still show. Calculation and labels depend only on the period's Enable and Label switches.
- **±1σ and ±2σ bands** per period. Std multipliers, lines, axis indicators and fills are all editable.
- **Floating labels** with price (`dVWAP 28965.25`, `d+1 29040.50`, ...) coloured to match their lines. Overlapping tags are pushed apart with a short leader line, so many levels do not cover the chart.
- **Session option**: Extended (18:00 ET, default), RTH (9:30 ET), or follow the chart setting.
- **Resets each period**: lines break at the start of every day, week and month.

## Calculation

Follows MotiveWave's built-in `VWAP.java`:

- **VWAP**: tick-based, `sum(price × volume) / sum(volume)`, restarted at each period boundary.
- **Bands**: volume-weighted deviation of bar HLC3 from the VWAP over the period (Sierra Chart style), multiplied by the std setting. Implemented with running sums so each bar costs O(1); the result is algebraically identical to the original per-bar loop.
- **History**: ticks are replayed from the start of the month containing (now − **History Lookback**), so the monthly VWAP covers the whole month. Requires tick data loaded back that far.

## Settings

| Tab | Options |
|---|---|
| General | Session, Use Historical Bars, Match MotiveWave Bands, History Lookback (days, default 90, min 35) |
| Labels | Show Labels, Show Price In Label (all levels, VWAP only, none), Label Font, Label Offset From Right Edge (px) |
| Daily / Weekly / Monthly | Enable period; Label VWAP / Band 1 / Band 2; VWAP line and axis indicator; Band 1 and Band 2: line, std multiplier, top/bottom indicators, fill |

Defaults: bands on at 1σ and 2σ, fills off. Colours: Daily blue, Weekly red, Monthly orange.

## Build and install

See [compile.md](compile.md).

## Scope and limitations

- Quarterly, yearly and rolling (7/30/90/365 day) VWAPs are not included yet.
- Prior-period levels are not projected. Older periods still draw as separate lines, as in MotiveWave's own VWAP; labels show the current period only.
- Not yet tested against a live MotiveWave install: verify period boundaries and the ±2σ fill first.

## Files

| File | Purpose |
|---|---|
| `CustomVWAPSuite.java` | The study (package `com.mystudies`) |
| `compile.md` | Build and install steps |
