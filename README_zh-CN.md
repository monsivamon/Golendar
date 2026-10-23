<p align="center">
  <img src="screenshots/icon.png" width="80" alt="Golendar 图标">
</p>
<p align="center">
  <strong>Your idea, your future.</strong>
</p>

<p align="center"><a href="README_EN.md">English</a> · <a href="README.md">日本語</a> · <b>简体中文</b> · <a href="README_KO.md">한국어</a></p>

# 📅 Golendar

一款支持月视图、周视图、日视图的 Android 日历应用。  
内置注重隐私的 **Golendar 模式**（应用内独立运行、支持多个本地日历）与 **Google 模式**（同步 Google 日历）两种模式。

> **详细用法请查看应用内教程**  
> 在设置 →“教程”中，提供 17 种以上的图文说明。  
> 包括日历权限、手势操作、地图、AI 解析、日程的添加与编辑、重复日程、照片、搜索、通知、备份、小组件等。

---

## 📲 下载

最新 APK 请从 [GitHub Releases](https://github.com/monsivamon/Golendar/releases/latest) 获取。  
在设备上打开 APK，允许“安装未知应用”后进行安装。

---

## 📸 截图

| 月视图 | 周视图 | 日视图 |
|:-:|:-:|:-:|
| <img src="screenshots/Month.png" width="200"> | <img src="screenshots/Weekly.png" width="200"> | <img src="screenshots/Day.png" width="200"> |

| 日程编辑 | 设置 | 小组件 |
|:-:|:-:|:-:|
| <img src="screenshots/ScheduleEdit.png" width="200"> | <img src="screenshots/Setting.png" width="200"> | <img src="screenshots/Widget_1.png" width="200"> |

---

## ✨ 主要功能

- **两种日历模式**：Golendar 模式（应用内独立运行，仅获取节假日和显示地图时联网）与 Google 模式（同步 Google 日历）。
- **月・周・日视图**：滑动切换期间和标签页。月视图下方的日程列表可开关。支持过去 2 年至未来 2 年的日程搜索。
- **日程管理**：添加、编辑、删除，重复（每天/工作日/每周/每月/每年，可指定结束日期），以文本分享。
- **照片・AI 解析（Golendar 模式）**：照片最多 5 张（长边自动缩小至 1920px）。AI 解析为 BETA，可从日历图片读取日程并批量注册。
- **通知・时区**：整点与提前 10 分钟通知。节假日・文化事件不通知（生日通知）。全天日程在显示时区的 9:00 通知。可从约 600 个时区中固定显示时区，也可让通知时间跟随当地时钟。
- **节假日**：可从 G7、金砖创始成员国、韩国、台湾、香港共 15 个国家・地区中多选。同一天的节假日合并为一行并显示国旗。
- **小组件・快捷方式**：日/周/月 3 种小组件。长按应用图标可使用“添加日程”“今天”“搜索”。
- **备份与还原**：JSON / 含照片的 ZIP（Golendar 模式）。支持按日历分配、还原前预览、跳过重复。
- **自定义**：主题、16 种背景色、星期颜色、日历颜色（两种模式）、日程颜色（Golendar 模式）、每周起始日、4 种语言。

---

## 🚀 开始使用

1. 安装 APK 并启动
2. 按首次启动的引导允许通知
3. 使用 Google 模式时，允许访问日历并选择要显示的日历
4. 首次使用地图时会显示位置信息提示（拒绝后仍可使用地图功能）

---

## ⚠️ 主要注意事项

- **日历权限**：Google 模式需要 `READ_CALENDAR` / `WRITE_CALENDAR`。拒绝时会自动切换到 Golendar 模式。永久拒绝时会引导至设置界面。
- **显示日历**：至少需要显示 1 个日历。在 Google 模式中添加或删除日历后，请执行“重新获取日历列表”。
- **删除本地日历**：删除默认（我的日历）以外的日历会同时删除其中的日程（删除前会显示确认对话框）。
- **备份**：Google 模式下不可“还原（覆盖）”，仅可“追加”。旧格式备份请按照应用内备份界面的说明操作。
- **通知**：建议关闭电池优化并允许精确闹钟。部分厂商设备上可能延迟。
- **固定时区**：开启时，指定时间的日程在新建和编辑时按固定时区保存（全天日程按 UTC 日期边界）。
- **地图**：需要网络连接。拒绝位置信息后仍可使用地图功能。
- **仅限 Golendar 模式**：照片附件、AI 解析、日程颜色。
- **节假日数据**：日本使用 `holidays-jp`，其他对象国使用 `vacanza/holidays` 的公开 JSON。离线时显示最后一次获取的数据，不包含在备份中（还原时重新获取）。

---

## 🛠 技术栈

| 类别 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 导航 | Navigation Compose |
| 小组件 | Jetpack Glance |
| 本地数据库 | Room |
| 设置 | Preferences DataStore |
| 后台 | Coroutines, AlarmManager, WorkManager |
| 地图 | MapLibre Native GL（OpenFreeMap） |
| 逆地理编码 | Nominatim、日本国土地理院 |
| 图片加载 | Coil |
| 架构 | MVVM |

---

## 🙏 Credits

- 节假日数据（日本）：[holidays-jp](https://github.com/holidays-jp/date.json)（MIT）
- 节假日数据（其他对象国）：[vacanza/holidays](https://github.com/vacanza/holidays)（MIT）
- 地图数据：[OpenStreetMap](https://www.openstreetmap.org/) contributors（ODbL）
- 瓦片分发：[OpenFreeMap](https://openfreemap.org/)
- 逆地理编码：[Nominatim](https://nominatim.org/) / [日本国土地理院](https://mreversegeocoder.gsi.go.jp/)
- 库：MapLibre Native for Android（BSD-2-Clause）、AndroidX / Jetpack（Apache 2.0）、Kotlin（Apache 2.0）、Coil（Apache 2.0）
- 资源：Android robot（Google 制作，CC BY 3.0）

---

## 📄 许可证

[GNU General Public License v3.0 (GPLv3)](LICENSE)

### 第三方许可证

| 名称 | 许可证 |
|---|---|
| Kotlin / AndroidX / Jetpack / Room / DataStore / Glance / WorkManager | Apache 2.0 |
| Coil | Apache 2.0 |
| MapLibre Native for Android | BSD-2-Clause |
| OpenStreetMap 数据 | ODbL 1.0 |
| Nominatim | GPL v2（服务使用） |
| holidays-jp / vacanza/holidays | MIT |
