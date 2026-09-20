<p align="center">
  <img src="docs/icon.png" width="128" alt="Bunko Icon" />
</p>

# Bunko
> A lightweight, modern Android reader for [Kavita](https://www.kavitareader.com/) and local storage,
> designed for self-scanned manga, comics, light novels, and e-books with tablet-first ergonomics.

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Status: Active Development](https://img.shields.io/badge/Status-Active%20Development-brightgreen.svg)]()
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)]()

[日本語 README](README.ja.md)

---

## 📖 What is Bunko

**Bunko** (文庫, *paperback library*) is a fast, native Android reader built with Jetpack Compose and Material 3 Expressive design.

It offers a unified reading experience across **remote Kavita server streams**, **downloaded offline chapters**, and **local device storage** with full support for every major comic, manga, e-book, and fixed-layout format.

---

## ✨ Features & Highlights

### 🚀 Universal Multi-Format Engine
A single, shared, non-redundant reader engine powering both local files and remote Kavita streams:

| Category | Formats Supported | Engine Capabilities |
| :--- | :--- | :--- |
| **Comics & Manga** | `.cbz`, `.cbr`, `.cb7`, `.cbt`, `.zip`, `.rar`, `.7z`, `.tar`, image folders | Natural numeric entry sorting, ComicInfo.xml metadata inspection, automatic archive fallback. |
| **Reflowable E-Books** | `.epub` (EPUB 2 & 3), `.mobi`, `.azw`, `.azw3`, `.fb2`, `.txt`, `.md` | PalmDOC LZ77 decompressor (Librera), OPF/NCX spine resolver, Ruby/Furigana text formatting. |
| **Documents** | `.pdf` | High-DPI hardware-accelerated thread-safe PDF renderer. |

---

### 🎨 Reading Ergonomics & UI
- **PlayCurl 3D OpenGL Page Turn**: Pure Kotlin Jetpack Compose 3D curl physics with Bezier shadows and instant zero-delay consecutive turns.
- **Continuous Webtoon Scroll**: Seamless vertical scrolling with tap-to-scroll viewport jumping (75%) and adjustable side margins.
- **Auto Webtoon Detection**: Automatic manhwa/webtoon identification via title heuristics, ComicInfo tags, and aspect ratio analysis.
- **Material Expressive Wavy Progress Bar**: Wavy sinusoidal page seekbar with interactive value bubbles and RTL/Manga direction support.
- **Spread-Pair Correction**: Shift spread pairing (+1 / -1) to restore intended two-page layouts affected by title/cover offsets.
- **Smart Invert & E-Paper Modes**: Inverts mostly-white pages for comfortable night reading while preserving color illustrations (configurable white-pixel threshold).
- **Customizable Tap Navigation**: Flexible tap zones (Default, L-shaped, Kindle-ish, Edge, Right & Left) with inversion options.
- **Image Scaling & Border Crop**: Fit Screen, Stretch, Fit Width, Fit Height, Original, Smart Fit, and automatic border whitespace removal.
- **E-Book Typography**: Dynamic font size, custom fonts (Serif, Sans-serif, Monospace), text alignment, and chapter TOC tree.

---

### 📚 Library Management & Connectivity
- **Multi-Server Kavita Profiles**: Fast switching between servers with secure token authentication.
- **Reading Progress Sync**: Bi-directional progress synchronization and auto mark-as-read on completion.
- **Offline Library Folders**: Storage Access Framework (SAF) folder picker with multi-folder indexing and format categorization (eBooks, MOBI, Comics, PDF).
- **Global & Local Search**: Instant search across Series, Persons, Genres, Tags, Collections, Reading Lists, and Chapters.
- **Tablet & Foldable Optimization**: Adaptive Navigation Rail, dual-pane library layout, and automatic spread/single-page layout transitions.

---

## 📦 Installation

Download the latest APK from the [Releases](https://github.com/Arnab11/Bunko/releases) page and install it on any Android 8.0+ device (arm64-v8a, armeabi-v7a, x86, x86_64, or Universal).

```bash
adb install app-arm64-v8a-release.apk
```

---

## 🛠️ Build from Source

### Requirements
- JDK 17 or JDK 21
- Android SDK (API 35+)
- Gradle 8.x / 9.x

### Build Commands

```bash
# Debug Build
./gradlew assembleDebug

# Release Build (Minified & Optimized)
./gradlew assembleRelease

# Run Unit Tests
./gradlew test
```

---

## 🤝 Acknowledgments & Credits

Bunko is built with open source and stands on the shoulders of incredible projects and contributors across the Android and reader ecosystem:

| Project | Author / Team | License | Role in Bunko |
| :--- | :--- | :--- | :--- |
| **[Librera Reader](https://github.com/foobnix/LibreraReader)** | [foobnix](https://github.com/foobnix) | GPL-3.0 | PalmDOC LZ77 decompression, EXTH metadata, and streaming MOBI/AZW record parser (`LibreraMobiParser`, `ByteArrayBuffer`). |
| **[PlayLikeCurl](https://github.com/Darkaxt/PlayLikeCurl)** | [Darkaxt](https://github.com/Darkaxt) / [karankalsi](https://github.com/karankalsi) | MIT | 3D OpenGL page-curl physics engine migrated to Kotlin Compose with instant turn rendering. |
| **[Mihon](https://github.com/mihonapp/mihon)** | [Mihon Open Source Project](https://github.com/mihonapp) | Apache-2.0 | Tap navigation zones, tap inversion modes, scale types, scan border cropping, and webtoon continuous scrolling ergonomics. |
| **[Kavita](https://www.kavitareader.com/)** | [Kavita Team](https://github.com/Kareadita/Kavita) | GPL-3.0 | Self-hosted digital library server and REST API specification. |
| **[Jsoup](https://jsoup.org/)** | [Jonathan Hedley](https://github.com/jhy) | MIT | HTML DOM manipulation, EPUB / MOBI / FB2 chapter splitting, and Ruby/Furigana text parsing. |
| **[Junrar](https://github.com/junrar/junrar)** | [Junrar Contributors](https://github.com/junrar/junrar) | Apache-2.0 | Pure-Java RAR archive entry extractor for CBR comics. |
| **[Apache Commons Compress](https://commons.apache.org/proper/commons-compress/)** | [Apache Software Foundation](https://www.apache.org/) | Apache-2.0 | 7-Zip (`.7z`/`.cb7`) and TAR (`.tar`/`.cbt`) archive extraction pipelines. |
| **[Coil](https://coil-kt.github.io/coil/)** | [Coil Contributors](https://github.com/coil-kt/coil) | Apache-2.0 | High-performance image loading, decoding, and memory caching. |

---

## 📄 License

Distributed under the **Apache License 2.0**. See [`LICENSE`](LICENSE) for more information.

```
Copyright 2026 BunkoApp

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
