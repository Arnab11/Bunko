<p align="center">
  <img src="docs/icon.png" width="128" alt="Bunko Icon" />
</p>

# Bunko
> An Android manga reader for [Kavita](https://www.kavitareader.com/),
> designed for self-scanned libraries and tablet-first reading.

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Status: Early Development](https://img.shields.io/badge/Status-Early%20Development-orange.svg)]()
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)]()

[日本語 README](README.ja.md)

---

## What is Bunko

**Bunko** (文庫, *library / paperback*) is a third-party Android app for reading manga and light novels from a [Kavita](https://www.kavitareader.com/) server.

It is especially focused on people who digitize their own physical books and want a comfortable native reading experience on tablets and phones.

> Status: **v0.22 (early)**. Usable, but still evolving.

## Highlights

Several Kavita clients already exist. Bunko focuses on **tablet/phone parity** and **reading ergonomics for self-scanned books**.

- **Spread-pair correction**
  When covers, title pages, or wide illustrations shift a spread by one page, edge long press or the reader menu provides **Shift +1 / -1** controls to recover the intended pairing.

- **Smart Invert**
  The reader can switch between **Off / Smart / Always**. Smart Invert flips mostly-white text pages for night reading while leaving illustrations and color pages untouched.
  - The "Smart" detection threshold (percentage of white) is **adjustable in the settings**

- **Search**
  The Home Search tab searches Series / Persons / Genres / Tags / Collections / Reading Lists / Chapters and lets you jump from authors or metadata to filtered series grids.

## Other features

- Multiple Kavita server profiles
- Reading progress sync and mark-as-read on completion
- Offline reading
- Reader page prefetch
- Admin-only Scan Library / metadata refresh actions
- Navigation rail on tablets and bottom navigation on phones
- Automatic single-page / spread switching
- Right-to-left and left-to-right binding
- Pinch zoom, pan, double-tap zoom
- Tap and swipe page turns
- Continuous vertical reading and Webtoon mode with 75% viewport tap-to-scroll and configurable side padding
- Auto Webtoon mode with intelligent detection via strip aspect ratios and metadata tags/genres
- Customizable tap zones (Default, L-shaped, Kindle-ish, Edge, Right & Left) with inversion options
- Image scale modes (Fit Screen, Stretch, Fit Width, Fit Height, Original, Smart Fit) and automatic border crop
- Page-jump slider

## Install

Download the APK from [Releases](https://github.com/Arnab11/Bunko/releases) and sideload it onto an Android 8.0+ device.

## Usage

1. Register your Kavita server URL and Auth Key (`x-api-key`) from the first-launch flow or Settings.
2. Tap **Connect** to authenticate.
3. Open a title from Home, Libraries, or Search.
4. Tap the center of the reader to open the reader menu.

## License

[Apache License 2.0](LICENSE) © 2026 BunkoApp

## Roadmap

- Page-turn animation polish
- More Kavita server features surfaced natively
- App icon refresh

## For Developers

Bunko is written in Kotlin 2.x with Jetpack Compose and Material 3 / Material 3 Expressive components. Networking is handled with Retrofit and kotlinx.serialization, while cover and reader-page images are loaded with Coil.

Kavita integration is intentionally centralized around `app/src/main/java/com/bunko/reader/KavitaApi.kt`. UI layers should consume Bunko models and repositories rather than constructing Kavita calls directly.

Design notes and implementation scratchpads live under `docs/`. They are internal working notes and may be git-ignored, stale, or written for local development rather than public API documentation. For public discussion, bug reports, and feature requests, please use [GitHub issues](https://github.com/Arnab11/Bunko/issues).

## Third-party

- The "Play Curl" page-turn effect is built on a vendored copy of
  [Darkaxt/PlayLikeCurl](https://github.com/Darkaxt/PlayLikeCurl) (a maintained fork of
  [karankalsi/PlayLikeCurl](https://github.com/karankalsi/PlayLikeCurl), MIT License),
  whose `karackencurllib` sources live under `app/src/main/java/karacken/curl/`.
  The legacy `karackencurllib-1.0.aar` from the upstream release is kept under
  `app/libs/` for reference. The previous 3D curl implementation (a vendored fork of
  [oleksandrbalan/pagecurl](https://github.com/oleksandrbalan/pagecurl), Apache-2.0)
  remains under `app/src/main/java/eu/wewox/pagecurl/` but is no longer used.
- Reader tap navigation zones, tap inversion modes, scale types, scan border cropping, and Webtoon continuous scrolling / auto Webtoon mode detection are inspired by and adapted from [Mihon](https://github.com/mihonapp/mihon) (Apache-2.0, © Mihon Open Source Project and contributors).

