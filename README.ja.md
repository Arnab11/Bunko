<p align="center">
  <img src="docs/icon.png" width="128" alt="Bunko Icon" />
</p>

# Bunko 文庫
> Kavita 向けの Android 漫画リーダー。
> 自炊本・タブレット読書・見開き体験を重視しています。

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Status: Early Development](https://img.shields.io/badge/Status-Early%20Development-orange.svg)]()
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)]()

[English README](README.md)

---

## Bunko とは

**Bunko** (文庫) は、[Kavita](https://www.kavitareader.com/) サーバー上の漫画・ライトノベルを Android 端末で読むためのサードパーティアプリです。

特に、自分で裁断・スキャンして電子化した本 (自炊本) を、タブレットで快適に読む用途を重視しています。

> ステータス: **v0.22 (early)**。実用段階ですが、まだ開発中です。

## 特徴

Kavita クライアントは既に複数ありますが、Bunko は **タブレット/スマホ両対応** と **自炊本向けの読書体験** を重視しています。

- **見開きずれ補正**
  表紙、扉、横長画像などで見開きの左右が 1 ページずれた場合に、画面端の長押しまたは Reader メニューの **Shift +1 / -1** で 1 ページ単位の補正ができます。

- **Smart Invert (おまかせ白黒反転)**
  Reader メニューから **Off / Smart / Always** を切り替えられます。Smart は文字中心の白いページだけを反転し、カラー口絵や挿絵はそのまま表示します。カラー口絵・挿絵のあるライトノベルを読むときに特に便利です。
  - 「Smart」の判定しきい値 (白の割合) は **設定で調整可能**。自炊環境 (圧縮設定) の差を吸収できます。

- **検索**
  Home の検索タブから シリーズ / 人物 / ジャンル / タグ / コレクション / 読書リスト / チャプター を検索できます。著者やジャンルから絞り込まれたシリーズ一覧 (グリッド表示) へ移動できます。

## その他の機能

- 複数 Kavita サーバーへの接続
- ページ進捗同期、読了時の mark-as-read
- オフライン読書 (飛行機等)
- Reader ページ先読み
- 管理者向け Scan Library / metadata refresh (admin role でログイン時)
- タブレット向け Navigation Rail、スマホ向け Bottom Navigation
- 見開き/単ページの自動切り替え
- 右綴じ/左綴じ (デフォルトでは ComicInfo.xml に従う)
- ピンチズーム、パン、ダブルタップズーム
- タップ/スワイプでのページ送り
- カスタマイズ可能なタップゾーン (Default, L-shaped, Kindle-ish, Edge, Right & Left) と反転設定
- 画像スケーリング (Fit Screen, Stretch, Fit Width, Fit Height, Original, Smart Fit) と余白自動クロップ
- スライダーによるページジャンプ

## インストール

[Releases](https://github.com/BunkoApp/Bunko/releases) から APK をダウンロードし、Android 8.0 以上の端末にサイドロードしてください (「提供元不明のアプリ」のインストール許可が必要)。

## 使い方

1. 初回画面または Settings から Kavita サーバー URL と Auth Key (`x-api-key`) を登録します。
2. **Connect** で認証します。
3. Home / Libraries / Search から作品を開きます。
4. Reader では中央タップでメニューを表示できます。

## ライセンス

[Apache License 2.0](LICENSE) © 2026 BunkoApp

## Roadmap

- ページめくりアニメーションの polish
- Kavita Web UI に頼る場面を減らす API 対応
- アプリアイコンの刷新

## 開発者向け

Bunko は Kotlin 2.x、Jetpack Compose、Material 3 / Material 3 Expressive、Retrofit、kotlinx.serialization、Coil で構成されています。

Kavita との接点は `app/src/main/java/com/bunko/reader/KavitaApi.kt` を中心に集約しています。UI 層では Kavita API を直接組み立てず、Bunko 内部のモデルや repository を通して扱う方針です。

設計メモや実装メモは `docs/` 配下にありますが、多くは内部作業用であり、常に公開ドキュメントとして最新とは限りません。バグ報告や機能要望は [GitHub issues](https://github.com/BunkoApp/Bunko/issues) へお願いします。

## サードパーティ

- Curl (ページめくり) 効果は [oleksandrbalan/pagecurl](https://github.com/oleksandrbalan/pagecurl)
  (Apache-2.0, © Oleksandr Balan and pagecurl contributors) の vendored fork を基盤にしています。
  取り込んだソースは `app/src/main/java/eu/wewox/pagecurl/` にあり、同ディレクトリの `README.md` に
  出所・ライセンス・Bunko 側の改変を記録しています。Bunko 自体も Apache-2.0 なので、
  リポジトリ root の `LICENSE` が fork にも適用されます。
- リーダーのタップゾーン (Default, L-shaped, Kindle-ish, Edge 等)、タップ反転、画像スケーリング、余白自動クロップは [Mihon](https://github.com/mihonapp/mihon) (Apache-2.0, © Mihon Open Source Project and contributors) を参考に設計・移植されています。

