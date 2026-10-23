<p align="center">
  <img src="screenshots/icon.png" width="80" alt="Golendar アイコン">
</p>
<p align="center">
  <strong>Your idea, your future.</strong>
</p>
<p align="center"><a href="README_EN.md">English</a> · <b>日本語</b> · <a href="README_zh-CN.md">简体中文</a> · <a href="README_KO.md">한국어</a></p>

# 📅 Golendar

月・週・日表示を備えた Android カレンダーアプリです。  
プライバシー重視の **Golendarモード**（アプリ内完結・複数ローカルカレンダー）と、**Googleモード**（Googleカレンダー同期）の2つを搭載しています。

> **詳しい使い方はアプリ内チュートリアルへ**  
> 設定 →「チュートリアル」に、図解付きの案内を17種以上用意しています。  
> カレンダー権限、ジェスチャー、地図、AI解析、予定の追加・編集、繰り返し、写真、検索、通知、バックアップ、ウィジェットなど。

---

## 📲 ダウンロード

最新のAPKは [GitHub Releases](https://github.com/monsivamon/Golendar/releases/latest) から。  
APKを端末で開き、「不明なアプリのインストール」を許可してインストールしてください。

---

## 📸 スクリーンショット

| 月間 | 週間 | 日間 |
|:-:|:-:|:-:|
| <img src="screenshots/Month.png" width="200"> | <img src="screenshots/Weekly.png" width="200"> | <img src="screenshots/Day.png" width="200"> |

| 予定編集 | 設定 | ウィジェット |
|:-:|:-:|:-:|
| <img src="screenshots/ScheduleEdit.png" width="200"> | <img src="screenshots/Setting.png" width="200"> | <img src="screenshots/Widget_1.png" width="200"> |

---

## ✨ 主な機能

- **2つのカレンダーモード**：Golendarモード（アプリ内完結。祝日取得・地図表示のみネット使用）と Googleモード（Googleカレンダー同期）。
- **月・週・日表示**：スワイプで期間移動・タブ切替。月表示下部の予定リストはON/OFF可能。過去2年〜未来2年の予定検索にも対応。
- **予定管理**：追加・編集・削除、繰り返し（毎日/平日/毎週/毎月/毎年、終了日指定）、テキスト共有。
- **写真・AI解析（Golendarモード）**：写真は最大5枚（長辺1920pxに自動縮小）。AI解析はBETAで、カレンダー画像から予定を読み取って一括登録できます。
- **通知・タイムゾーン**：定刻・10分前の通知。祝日・文化イベントは通知対象外（誕生日は対象）。終日予定は表示タイムゾーンの9:00に通知します。表示タイムゾーンは約600ゾーンから固定でき、通知時刻も現地の時計に合わせられます。
- **祝日**：G7・BRICS原加盟国・韓国・台湾・香港の15か国・地域から複数選択。同日の祝日は1行にまとめて国旗で表示します。
- **ウィジェット・ショートカット**：日/週/月の3種ウィジェット。アイコン長押しで「予定追加」「今日」「検索」。
- **バックアップ＆復元**：JSON / 写真込みZIP（Golendarモード）。カレンダーごとの振り分け、復元前プレビュー、重複スキップに対応。
- **カスタマイズ**：テーマ、背景16色、曜日色、カレンダー色（両モード）、予定ごとの色（Golendarモード）、週の始まり、4言語。

---

## 🚀 はじめかた

1. APKをインストールして起動
2. 初回起動時の案内に従って、通知を許可
3. Googleモードを使う場合は、カレンダーへのアクセスを許可して表示カレンダーを選択
4. 地図は初回利用時に位置情報の案内を表示（拒否しても地図機能は利用可）

---

## ⚠️ 主な注意点

- **カレンダー権限**：Googleモードでは `READ_CALENDAR` / `WRITE_CALENDAR` が必要です。拒否時は Golendarモードへ自動切替。恒久的に拒否した場合は設定画面へ誘導します。
- **表示カレンダー**：最低1つ以上の表示が必要です。Googleモードでカレンダーを追加・削除した後は「カレンダー一覧を再取得」を実行してください。
- **ローカルカレンダーの削除**：既定（マイカレンダー）以外を削除すると、所属する予定も削除されます（削除前に確認ダイアログを表示）。
- **バックアップ**：Googleモードでは「復元（上書き）」は不可で、「追記」のみ可能です。旧形式バックアップの扱いは、アプリ内のバックアップ画面の案内に従ってください。
- **通知**：バッテリー最適化の解除と正確なアラームの許可を推奨。一部メーカー端末では遅延する場合があります。
- **タイムゾーン固定**：ONのとき、時刻指定予定の作成・編集は固定ゾーン基準で保存されます（終日予定はUTC日付境界）。
- **地図**：ネット接続が必要です。位置情報を拒否しても地図機能は使えます。
- **Golendarモード専用**：写真添付、AI解析、予定ごとの色。
- **祝日データ**：日本は `holidays-jp`、その他の対象国は `vacanza/holidays` の公開JSONを使用。オフライン時は最後に取得したデータを表示し、バックアップには含まれません（復元時に再取得）。

---

## 🛠 使用技術

| カテゴリ | 技術 |
|---|---|
| 言語 | Kotlin |
| UI | Jetpack Compose |
| ナビゲーション | Navigation Compose |
| ウィジェット | Jetpack Glance |
| ローカルDB | Room |
| 設定 | Preferences DataStore |
| バックグラウンド | Coroutines, AlarmManager, WorkManager |
| 地図 | MapLibre Native GL（OpenFreeMap） |
| 逆ジオコーディング | Nominatim, 国土地理院 |
| 画像読込 | Coil |
| アーキテクチャ | MVVM |

---

## 🙏 Credits

- 祝日データ（日本）：[holidays-jp](https://github.com/holidays-jp/date.json)（MIT）
- 祝日データ（その他の対象国）：[vacanza/holidays](https://github.com/vacanza/holidays)（MIT）
- 地図データ：[OpenStreetMap](https://www.openstreetmap.org/) contributors（ODbL）
- タイル配信：[OpenFreeMap](https://openfreemap.org/)
- 逆ジオコーディング：[Nominatim](https://nominatim.org/) / [国土地理院](https://mreversegeocoder.gsi.go.jp/)
- ライブラリ：MapLibre Native for Android（BSD-2-Clause）、AndroidX / Jetpack（Apache 2.0）、Kotlin（Apache 2.0）、Coil（Apache 2.0）
- アセット：Android robot（Google 作成、CC BY 3.0）

---

## 📄 ライセンス

[GNU General Public License v3.0 (GPLv3)](LICENSE)

### サードパーティライセンス

| 名称 | ライセンス |
|---|---|
| Kotlin / AndroidX / Jetpack / Room / DataStore / Glance / WorkManager | Apache 2.0 |
| Coil | Apache 2.0 |
| MapLibre Native for Android | BSD-2-Clause |
| OpenStreetMap データ | ODbL 1.0 |
| Nominatim | GPL v2（サービス利用） |
| holidays-jp / vacanza/holidays | MIT |
