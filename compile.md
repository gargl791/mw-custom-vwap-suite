# Compiling CustomVWAPSuite

Run in **Command Prompt** from the project root.

## Layout

```
<project root>/
├── jdk-26.0.2.1+1/
├── out/                 <- compiled classes and jar
├── mwave_sdk.jar
├── CustomVWAPSuite.java
├── README.md
└── compile.md
```

## Commands

```bat
"jdk-26.0.2.1+1\bin\javac.exe" -cp "mwave_sdk.jar" -d out CustomVWAPSuite.java
cd out
"..\jdk-26.0.2.1+1\bin\jar.exe" cf CustomVWAPSuite.jar com
cd ..
```

Result: `out\CustomVWAPSuite.jar`.

Notes:

- The file declares `package com.mystudies;`, so classes are written to `out\com\mystudies\`. The jar is built from the `com` folder.
- No `nls` copy step is needed (labels are plain strings).
- Add `out/` and `*.jar` to `.gitignore` so build output isn't committed.

## Install

1. Copy `out\CustomVWAPSuite.jar` into MotiveWave's Extensions folder.
2. Restart MotiveWave.
3. Add **VWAP Suite (D/W/M)** to a chart as an overlay (it should appear in the study list, likely under **Custom**).
4. In the study settings, check **Session** (default Extended, 18:00 ET) and **History Lookback (days)** (default 90).

## Troubleshooting

| Problem | Fix |
|---|---|
| `cannot find symbol` on a Settings getter, `Figure`, or a descriptor | SDK version differs. Send me the exact error. |
| `package ... does not exist` | Some classes come from MotiveWave's application jar, not the SDK jar. Use `-cp "mwave_sdk.jar;C:\path\to\MotiveWave\lib\*"`. |
| `UnsupportedClassVersionError` in MotiveWave | Add `--release 17` to the javac command. |
| Old jar still loaded | Delete the old jar from the Extensions folder and restart MotiveWave. |
| Study loads but no lines | Needs tick data. Check tick history is loaded back to the start of the month, and that the period line is enabled on its tab. |
| Monthly line starts mid-month | Less tick history is loaded than the start of the month. Load more history or raise **History Lookback**. |
| Daily/weekly reset at the wrong time | Change **Session** (Extended 18:00 ET / RTH 9:30 ET / Chart Setting). |
| Labels missing | **Labels** tab: enable **Show Labels**; a level's label also needs its line enabled on its period tab. |
| Study is slow on load | Lower **History Lookback** (minimum 35 days). |
