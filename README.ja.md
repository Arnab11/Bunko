<p align="center">
  <img src="docs/icon.png" width="128" alt="Bunko Icon" />
</p>

# Bunko 文庫
> Kavita サーバーおよびローカルストレージ向けの軽量でモダンな Android リーダー。
> 自炊本、漫画、ライトノベル、電子書籍をタブレット・スマートフォンで快適に読むために設計されています。

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Status: Active Development](https://img.shields.io/badge/Status-Active%20Development-brightgreen.svg)]()
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)]()

[English README](README.md)

---

## 📖 Bunko とは

**Bunko** (文庫) は、Jetpack Compose と Material 3 Expressive で構築された高速でネイティブな Android リーダーアプリです。

**Kavita リモートストリーム**、**ダウンロードしたオフラインチャプター**、**端末内ローカルストレージ**のすべてを単一の共有エンジンでシームレスに閲覧できます。

---

## ✨ 主な機能

### 🚀 ユニバーサルマルチフォーマットエンジン
主要な電子書籍・コミック形式をすべてネイティブサポート:

| カテゴリ | 対応フォーマット | 特徴 |
| :--- | :--- | :--- |
| **コミック・漫画** | `.cbz`, `.cbr`, `.cb7`, `.cbt`, `.zip`, `.rar`, `.7z`, `.tar`, 画像フォルダ | 自然順ソート、ComicInfo.xml メタデータ解析、アーカイブ形式の自動検出フォールバック。 |
| **リフロー型電子書籍** | `.epub` (EPUB 2 & 3), `.mobi`, `.azw`, `.azw3`, `.fb2`, `.txt`, `.md` | Pure-Java PalmDOC LZ77 伸張 (Librera 由来)、OPF/NCX スパイン解決、ルビ・傍点・リストの忠実な再現。 |
| **ドキュメント** | `.pdf` | ハードウェアアクセラレーションによる高解像度スレッドセーフ PDF レンダリング。 |

---

### 🎨 読書体験とエルゴノミクス
- **PlayCurl 3D OpenGL リアルページめくり**: ベジエシャドウとスムーズなドラッグ操作による本物の紙のような 3D めくり体験。
- **Webtoon 連続縦スクロール**: 75% 画面高タップスクロールおよび左右余白設定に対応。
- **Auto Webtoon 自動判定**: ComicInfo タグ、タイトルキーワード、画像アスペクト比による縦スクロール自動切り替え。
- **Material Expressive Wavy プログレスバー**: 正弦波状のシークバーとフローティングバブルインジケータ (右開き/左開き対応)。
- **見開きずれ補正**: 表紙や単一扉絵による見開きの左右ずれを **Shift +1 / -1** で即座に補正。
- **Smart Invert (おまかせ白黒反転)**: カラー口絵や挿絵を維持したまま、本文の白背景のみを反転 (しきい値調整可能)。
- **多彩なタップゾーン**: Default, L-shaped, Kindle-ish, Edge, Right & Left から選択可能。
- **画像スケーリングと余白クロップ**: Fit Screen, Stretch, Fit Width, Fit Height, Original, Smart Fit および自動白線トリミング。
- **タイポグラフィ設定**: フォントサイズ、明朝 (Serif) / ゴシック (Sans) / 等幅 (Monospace) 切り替え、テキスト配置。

---

## 🤝 オープンソースとクレジット

Bunko は数多くの優れたオープンソースプロジェクトと開発者の成果に基づいて開発されています:

| プロジェクト | 作者 / チーム | ライセンス | Bunko での役割 |
| :--- | :--- | :--- | :--- |
| **[Librera Reader](https://github.com/foobnix/LibreraReader)** | [foobnix](https://github.com/foobnix) | GPL-3.0 | Pure-Java PalmDOC LZ77 伸張、EXTH メタデータ解析、MOBI/AZW ストリーミングレコードパーサー (`LibreraMobiParser`, `ByteArrayBuffer`)。 |
| **[PlayLikeCurl](https://github.com/Darkaxt/PlayLikeCurl)** | [Darkaxt](https://github.com/Darkaxt) / [karankalsi](https://github.com/karankalsi) | MIT | 3D OpenGL ページカール物理エンジンおよびリアルタイムめくり描画。 |
| **[Mihon](https://github.com/mihonapp/mihon)** | [Mihon Open Source Project](https://github.com/mihonapp) | Apache-2.0 | タップゾーン設定、タップ反転、画像スケーリング、余白自動クロップ、Webtoon 縦スクロール設計。 |
| **[Kavita](https://www.kavitareader.com/)** | [Kavita Team](https://github.com/Kareadita/Kavita) | GPL-3.0 | セルフホスト型デジタルライブラリサーバーおよび API 仕様。 |
| **[Jsoup](https://jsoup.org/)** | [Jonathan Hedley](https://github.com/jhy) | MIT | HTML DOM 解析、EPUB / MOBI / FB2 チャプター分割、ルビ抽出。 |
| **[Junrar](https://github.com/junrar/junrar)** | [Junrar Contributors](https://github.com/junrar/junrar) | Apache-2.0 | CBR コミック向け Pure-Java RAR エントリ伸張。 |
| **[Apache Commons Compress](https://commons.apache.org/proper/commons-compress/)** | [Apache Software Foundation](https://www.apache.org/) | Apache-2.0 | 7-Zip (`.7z`/`.cb7`) および TAR (`.tar`/`.cbt`) 抽出パイプライン。 |
| **[Coil](https://coil-kt.github.io/coil/)** | [Coil Contributors](https://github.com/coil-kt/coil) | Apache-2.0 | 高速画像ローディング、デコード、メモリキャッシュ。 |

---

## 📄 ライセンス

Distributed under the **Apache License 2.0**. See [`LICENSE`](LICENSE) for more information.

```
Copyright 2026 BunkoApp

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
