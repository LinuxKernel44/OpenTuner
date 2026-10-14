# OpenTuner

A free, open-source chromatic-style tuner for **6-string guitar** and **4-string bass**, written in Java for Android. Everything runs on-device using the phone's microphone — no network access, no ads, no tracking.

## Features

- Real-time pitch detection from the microphone (YIN algorithm, ~±1 cent accuracy on clean tones)
- Analog needle gauge with note name, frequency and cents deviation
- Auto-detect the nearest string, or tap a string to lock onto it
- Guitar presets: Standard, Half-step down, Full-step down, Drop D, Double Drop D, DADGAD, Open G / D / E / A / C
- Bass presets: Standard (E A D G), Half-step down, Full-step down, Drop D, Drop C, Open E / D / G
- Custom tunings: pick any note per string, name it, save, edit or delete
- Reference tone playback for each string
- Adjustable A4 reference pitch (415–466 Hz)
- System / light / dark theme

## Build

Requirements: JDK 17+, Android SDK (compileSdk 36).

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android version: 8.0 (API 26). The only permission is `RECORD_AUDIO`.

## Project layout

| File | Role |
|------|------|
| `PitchDetector` | YIN fundamental-frequency estimation |
| `PitchEngine` | Microphone capture thread |
| `TonePlayer` | Reference tone synthesis |
| `Tunings` / `TuningStore` | Presets and persisted custom tunings/settings |
| `GaugeView` | Custom-drawn needle gauge |
| `MainActivity` / `TuningEditorActivity` | UI |

## License

MIT — see [LICENSE](LICENSE).
