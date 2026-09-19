<p align="center">
  <img src="screenshots/icon.png" width="80" alt="Golendar アイコン">
</p>

<p align="center">
  <strong>Your idea, your future.</strong>
</p>

# 📅 Golendar

**Golendar** は、シンプルで使いやすい月・週・日表示を備えた Android カレンダーアプリです。  
プライバシー重視の「ローカル専用モード」と、利便性の高い「Googleカレンダー同期モード」の2つを搭載しています。

---

## 📲 ダウンロード

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/monsivamon/Golendar)](https://github.com/monsivamon/Golendar/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/monsivamon/Golendar/total)](https://github.com/monsivamon/Golendar/releases)

最新のAPKは [GitHub Releases](https://github.com/monsivamon/Golendar/releases/latest) からダウンロードできます。  
APKを端末で開き、「不明なアプリのインストール」を許可してインストールしてください。

---

## 📸 スクリーンショット

### カレンダービュー

| 月間カレンダー | 週間カレンダー | 日間カレンダー |
|:-:|:-:|:-:|
| <img src="screenshots/Month.png" width="200"> | <img src="screenshots/Weekly.png" width="200"> | <img src="screenshots/Day.png" width="200"> |

### 予定編集・設定

| 予定の追加／編集 | 設定画面（カスタマイズ） |
|:-:|:-:|
| <img src="screenshots/ScheduleEdit.png" width="200"> | <img src="screenshots/Setting.png" width="200"> |

### ホーム画面ウィジェット

| 月間ウィジェット | 日次・週次ウィジェット |
|:-:|:-:|
| <img src="screenshots/Widget_1.png" width="200"> | <img src="screenshots/Widget_2.png" width="200"> |

---

## ✨ 主な機能

### デュアルエンジン搭載

- **Golendarモード**：外部通信なし。アプリ内のみで完結するプライベートな予定管理。
- **Googleモード**：Googleカレンダーと同期し、他デバイスやサービスと共有。

### 柔軟なカレンダービュー

- 月間・週間・日間の3ビューをシームレスに切り替え。
- 月グリッド下の予定リストはヘッダー左のトグルで ON/OFF 可能（設定は保持されます）。
- 下部リスト OFF 時は日付タップでその日の予定をポップアップ表示。
- ヘッダーのカレンダーアイコンで今日の月表示へジャンプ。
- **予定の検索**：過去2年〜未来2年を対象。結果タップでその日へジャンプ。祝日・文化イベント・誕生日は除外。
- 上下スワイプで前後の期間へ、左右スワイプで日・週・月タブを切替（月・週・日で操作感を統一）。

### 地図で場所を選択

- 「場所」欄の地図アイコンから MapLibre Native の地図を開いて選択。
- Googleマップ風のベクタータイル（OpenFreeMap）を採用し、建物名・POI名も表示。
- 地図右下の現在地ボタンで現在地を追従表示（位置情報権限が必要）。
- 選択した場所は「POI名, 住所」形式で保存され、Google Calendar / Google マップと相互運用しやすい形式に。

### 繰り返し予定

- 毎日 / 平日 / 毎週 / 毎月 / 毎年 の繰り返しに対応。
- 繰り返しの終了日を指定可能（未指定なら無期限）。
- カレンダーの日付セルから追加した場合は日付がロックされる。

### 予定の簡単共有

- 予定詳細ダイアログから、タイトル・日時・場所・メモをテキストとして他アプリへ共有。

### アプリショートカット対応

- ホーム画面のアイコン長押しから「予定追加」「今日」「検索」を起動可能。

### カスタマイズ可能なホーム画面ウィジェット

- 日間・週間・月間の3種類。
- アプリ設定と連動し、16色のパステルカラーで背景をカスタマイズ可能。

### 正確なリマインダー通知

- 「定刻」「10分前」の通知に対応。
- Doze 対策として `AlarmManager` の正確なアラーム機能を使用し、再起動時も自動再スケジュール。

### 初回起動時のセットアップ

初回起動時は、以下の順でセットアップを行います（各ステップはスキップ可、後から設定画面で変更可能）。

1. 通知の許可（通知 → 正確なアラーム → バッテリー最適化の無効化）
2. Google カレンダーへのアクセス許可
3. ジェスチャー操作の案内（3ページ）

位置情報は、初めて地図の場所選択を開いたときに説明ダイアログが表示されます。

### データバックアップ＆復元

- 予定と設定（テーマ・曜日の色・週の始まりなど）を JSON ファイルとしてエクスポート可能。
- Googleカレンダーの予定を Golendar モードや別アカウントへ「追記」して移行・統合できます。
- 祝日データはバックアップに含まれません（30日ごとの自動再取得で復元されます）。

### 日本の祝日・文化イベントの自動識別

- 外部APIから祝日を自動取得し、カレンダーに表示。
- 祝日・文化イベント・誕生日を自動判別し、それぞれ異なる色で表示。

---

## 🎨 カスタマイズとデザイン

- **表示テーマ**：ライト / ダーク / システム追従
- **アプリ背景色**：16色のパステルカラーから選択。カレンダー・設定・ウィジェットすべてに連動し、背景輝度に応じて文字色も自動調整
- **曜日の色付け**：全曜日に自由にカラー設定可能
- **イベント色分け**：誕生日はオレンジ、文化イベントは緑、通常予定はテーマカラー
- **週の始まり**：日曜 / 月曜 を選択可能

---

## 🛠 使用技術

| カテゴリ | 技術 |
|----------|------|
| 言語 | Kotlin |
| UI フレームワーク | Jetpack Compose |
| ナビゲーション | Jetpack Navigation Compose |
| ウィジェット | Jetpack Glance |
| ローカルデータベース | Room |
| 設定の保存 | Preferences DataStore |
| バックグラウンド処理 | Coroutines, AlarmManager, BroadcastReceiver, WorkManager |
| 地図 | MapLibre Native GL（ベクタータイル: OpenFreeMap） |
| 逆ジオコーディング | Nominatim（OSM）, 国土地理院 |
| 通信 | HttpURLConnection, org.json |
| アーキテクチャ | MVVM (Model-View-ViewModel) |

---

## ⚠️ 既知の仕様・注意点

- **カレンダー権限**  
  Googleモード利用時は `READ_CALENDAR` / `WRITE_CALENDAR` の許可が必要です。拒否すると自動でGolendarモードに切り替わります。

- **通知の確実な受信**  
  通知許可に加え、「バッテリー最適化の無効化」と「正確なアラームの許可」を推奨します。一部メーカー端末（Xiaomi, OPPO 等）では通知が遅延する場合があります。

- **地図・位置情報**  
  場所選択は MapLibre + OpenFreeMap を使用（要ネット接続）。住所は Nominatim（1req/sec 制限あり）と国土地理院を併用しています。位置情報を拒否しても地図機能は使えます。

- **検索範囲**  
  検索対象は過去2年〜未来2年。祝日・文化イベント・誕生日は検索から除外されます。

- **繰り返し予定**  
  「終了」は1回分の所要時間です。繰り返し全体の終了日は「繰り返しの終了日を指定」で別途設定します。カレンダーの日付セルから追加した場合は日付がロックされます。

- **祝日データ**  
  `holidays-jp.github.io` から取得（初回起動時と30日ごとに自動更新）。オフライン時は最後に取得したデータを表示します。

- **バックアップ**  
  祝日データは含まれません。Googleモードでは「復元（上書き）」がブロックされ、「追記」のみ可能です。

---

## 🙏 Credits

### データ・API

- **祝日データ**：[holidays-jp](https://github.com/holidays-jp/date.json)（MIT License）
- **地図データ**：[OpenStreetMap](https://www.openstreetmap.org/) contributors（[ODbL](https://opendatacommons.org/licenses/odbl/)）
- **地図タイル配信**：[OpenFreeMap](https://openfreemap.org/)
- **逆ジオコーディング**：[Nominatim](https://nominatim.org/) / [国土地理院 逆ジオコーダ](https://mreversegeocoder.gsi.go.jp/)

### ライブラリ

- **MapLibre Native for Android**：[GitHub](https://github.com/maplibre/maplibre-native)（BSD-2-Clause）
- **AndroidX / Jetpack**（Compose, Navigation, Room, DataStore, Glance, WorkManager）：Apache License 2.0
- **Kotlin**：[kotlinlang.org](https://kotlinlang.org/)（Apache License 2.0）

### アセット

- **Android robot**：Google 作成。 [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) に基づき使用。

---

## 📄 ライセンス

このプロジェクトは [GNU General Public License v3.0 (GPLv3)](LICENSE) のもとで公開されています。

### サードパーティライセンス

| 名称 | ライセンス |
|------|-----------|
| Kotlin, AndroidX, Jetpack Compose, Room, DataStore, Glance, WorkManager | Apache License 2.0 |
| MapLibre Native for Android | BSD-2-Clause |
| OpenStreetMap データ | ODbL 1.0 |
| Nominatim | GPL v2（サービス利用、ソフトウェア同梱なし） |
| holidays-jp | MIT License |