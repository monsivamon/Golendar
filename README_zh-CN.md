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

---

## 📲 下载

最新 APK 可从 [GitHub Releases](https://github.com/monsivamon/Golendar/releases/latest) 获取。
在设备上打开 APK，允许“安装未知应用”后即可安装。

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

### 双引擎
- **Golendar 模式**：无需外部同步，完全在应用内运行（仅获取节假日数据与显示地图时联网）。可创建并管理多个本地日历。
- **Google 模式**：与 Google 日历同步。

### Golendar 模式的多日历支持
- 可在应用内创建多个本地日历，将日程分类管理。
- 首次启动时自动创建“我的日历”。我的日历无法删除，但可以隐藏。
- 可为每个日历设置名称、颜色、显示 ON/OFF 及排序。
- 删除默认日历以外的日历时，其中的日程也会一并被删除（删除前会显示确认日程数量的对话框）。
- 可在日程编辑界面选择要添加到的日历（当可见日历有 2 个及以上时）。
- 「显示全部」可一次性开启所有日历的显示（不可全部隐藏）。
- 当可见日历有 2 个及以上时，日程卡片会显示日历名称和颜色。

### Google 日历的多日历支持
- 按账号列出日历，可选择要显示的日历。
- 可在设置界面中逐个设置显示 ON/OFF、自定义颜色。
- 当可见日历有 2 个及以上时，日程卡片会显示日历名称和颜色。
- 通过「重新获取日历列表」来跟随日历的添加与删除。

### 日历视图
- 月视图、周视图、日视图之间无缝切换。
- 月网格下方的日程列表可切换 ON/OFF（关闭下方列表时，点击日期会弹出弹窗）。
- 月网格中的日程条最多显示 3 条，超出的部分以「+N」表示（关闭下方列表时最多显示 6 条）。显示下方列表时会显示还有未显示条目的指示器。
- 日程搜索（过去 2 年至未来 2 年，节假日・文化值得关注・生日除外）。
- 上下滑动切换前后时间段，左右滑动切换日・周・月标签。
- 日程卡片显示类型表情符号（🎂 生日 / 🎌 文化 / 🗾 节假日）。

### 通过地图选择地点
- 使用 MapLibre Native + OpenFreeMap 的地图进行选择。
- 带有跟随当前位置的按钮。以“POI 名称， 地址”格式保存。

### 重复日程
- 每天 / 工作日 / 每周 / 每月 / 每年。可指定结束日期。

### 日程分享
- 从详情弹窗以文本形式分享到其他应用。

### 照片附件（仅限 Golendar 模式）
- 每个日程最多可添加 5 张照片。自动缩放至长边 1920px。
- 支持放大查看器，并可导出到设备的「Pictures/Golendar」。

### 通过 AI 解析批量导入日程（BETA・仅限 Golendar 模式）
- 将日历截图交给外部 AI 读取，并根据返回的 JSON 批量导入日程。
- 支持复制 Prompt／分享面板。可在预览中勾选后导入。

### 应用快捷方式
- 长按图标可使用「添加日程」「今天」「搜索」。

### 主屏幕小组件
- 共有日视图、周视图、月视图 3 种。与应用设置（主题・背景色・可见日历・节假日显示）联动。
- 月视图小组件会连同前后月份的单元格一起显示日程圆点。
- Golendar 模式反映可见本地日历的日程，Google 模式反映可见 Google 日历的日程。

### 提醒通知
- 支持准点・提前 10 分钟通知。通过 `AlarmManager` 的精确闹钟应对 Doze 模式。
- 节假日・文化事件不通知。**生日会通知**（天皇诞生日按节假日处理，不通知）。
- 与可见日历设置联动（两种模式的可见日历均为通知对象）。

### 首次启动的引导设置
0. 欢迎界面（首次启动及版本更新时显示）
1. 通知权限（通知 → 精确闹钟 → 禁用电池优化）
2. Google 日历访问权限
3. 选择要显示的日历（Google 模式时）
4. 手势操作引导

位置信息会在首次打开地图时引导授权。

### 备份与还原
- **将可见日历的日程一并进行导出**（Golendar/Google 两种模式均可）。
- 开启「同时备份照片」时为 ZIP（仅 Golendar 模式）。关闭时为 JSON。
- 备份文件中包含“原日历信息”，还原时可按日历进行分类。
- 在显示多个日历的状态下按「保存」时，会显示确认目标列表的对话框。
- 文件名中附加上日期（例如：`golendar_backup_YYMMDD.json`）。
- **还原前预览**：选择文件后可确认其中包含的日历数・日程数・照片数。
- **选择要还原的原日历**：可通过复选框多选（旧格式备份中会自动隐藏）。
- **指定还原目标**：
  - Google 模式：选择 1 个还原目标日历。
  - Golendar 模式：可选择「自动创建同名日历并分类」或「合并到指定的本地日历」。
- **同名日历覆盖确认**：Golendar 模式下若已存在同名日历，会显示是否覆盖的确认对话框。
- **追加时的重复跳过**：标题・开始・结束・全天标志一致的日程不会重复添加。
- **旧格式（v1.1.6 及之前）的备份兼容**：追加在 Google/Golendar 哪种模式下都可以（选择追加目标日历）。还原（覆盖）仅限 Golendar 模式，还原到我的日历。旧格式 ZIP 不能在 Google 模式下追加。
- Google 模式下不可使用「还原（覆盖）」，仅可「追加」。
- 备份中不包含节假日数据，还原时会自动重新获取。

### 节假日・文化事件
- 从 `holidays-jp` API 自动获取（首次获取后每 30 天更新一次）。
- 自动判别节假日・文化・生日，并以颜色和表情符号显示。
- 可在设置界面切换「显示节假日」ON/OFF。
- 无论日历的显示 ON/OFF 如何，节假日始终显示。

### 教程
- 在设置界面的「教程」中，随时可以重新查看 13 种以上的引导内容。
- 内容：日历访问权限 / 手势操作 / 地图的使用方法 / AI 解析 / 日历的显示与颜色设置 / 月视图的下方列表 / 添加日程 / 编辑与删除日程 / 重复日程 / 照片的使用 / 搜索的使用方法 / 通知设置 / 备份与还原 / 小组件与快捷方式
- 首次展开「日历模式」分区时，会自动显示 Golendar/Google 的区别以及日历列表的操作方法。
- 每个教程由多页图文说明构成，并会耐心讲解容易踩坑的地方（重复日程的结束日期、“仅删除此日”的拆分行为、追加与还原的区别、隐藏日历的影响等）。

---

## 🎨 自定义

- 主题：浅色 / 深色 / 跟随系统
- 语言：日语 / English / 简体中文 / 한국어（在设置界面的「显示语言」分区中切换；切换后仍停留在设置界面。未选择时将根据设备语言自动判定）
- 应用背景色：16 种粉彩色（与日历・设置・小组件联动）
- 星期颜色：可为每个星期设置
- 各日历的颜色：**Golendar 模式・Google 模式均**可分别设置。并根据主题自动分配颜色（浅色=16 种深色调 / 深色=16 种明亮色调）。
- 事件颜色区分：生日=橙色、文化=绿色、普通=主题色（日历颜色优先）
- 每周起始日：周日 / 周一
- 节假日显示：ON/OFF

---

## 🛠 使用技术

| 类别 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose |
| 导航 | Navigation Compose |
| 小组件 | Jetpack Glance |
| 本地数据库 | Room |
| 设置 | Preferences DataStore |
| 后台处理 | Coroutines, AlarmManager, WorkManager |
| 地图 | MapLibre Native GL（OpenFreeMap） |
| 逆地理编码 | Nominatim, 国土地理院 |
| 图片加载 | Coil |
| 架构 | MVVM |

---

## ⚠️ 注意事项

- **日历权限**：Google 模式需要 `READ_CALENDAR` / `WRITE_CALENDAR` 权限。被拒绝时会自动切换到 Golendar 模式。没有 Google 账号时切换会显示提示。若被永久拒绝，会显示引导前往设置界面的对话框。
- **可见日历**：**必须至少显示 1 个日历**（不能为 0）。Golendar 模式下由于我的日历无法删除，因此始终会保留 1 个以上。在 Google 模式下添加・删除日历后，请执行「重新获取日历列表」。
- **删除本地日历**：删除默认（我的日历）以外的本地日历时，其中的日程也会一并被删除。删除前会显示确认日程数量的对话框。
- **切换 Google 模式**：在 v1.1.7 中，只要权限已授予，无需重启应用即可立即切换。
- **通知**：建议禁用电池优化并允许精确闹钟。部分厂商设备上可能有延迟。
- **地图**：需要网络连接。即使拒绝位置信息权限，地图功能仍可使用。
- **搜索**：节假日・文化・生日不在搜索范围内。
- **重复日程**：「结束」为单次所需时长。整体结束日期需另行指定。
- **节假日数据**：离线时显示最后一次获取的数据。
- **备份**：Google 模式下不可使用「还原（覆盖）」，仅可「追加」。还原目标日历从可写入的日历中选择。
- **旧格式备份**：v1.1.6 及之前的备份，追加在 Google/Golendar 哪种模式下都可以（选择追加目标日历）。还原（覆盖）仅限 Golendar 模式（还原到我的日历）。若要在 Google 模式下追加旧格式 ZIP，请先切换到 Golendar 模式。
- **照片附件**：仅限 Golendar 模式。不包含在云端自动备份中。
- **AI 解析**：仅限 Golendar 模式。解析结果仅反映到本地数据库。

---

## 🙏 致谢

### 数据・API
- 节假日数据：[holidays-jp](https://github.com/holidays-jp/date.json)（MIT）
- 地图数据：[OpenStreetMap](https://www.openstreetmap.org/) contributors（ODbL）
- 地图瓦片分发：[OpenFreeMap](https://openfreemap.org/)
- 逆地理编码：[Nominatim](https://nominatim.org/) / [国土地理院](https://mreversegeocoder.gsi.go.jp/)

### 库
- MapLibre Native for Android（BSD-2-Clause）
- AndroidX / Jetpack（Apache 2.0）
- Kotlin（Apache 2.0）
- Coil（Apache 2.0）

### 素材
- Android robot：由 Google 制作（CC BY 3.0）

---

## 📄 许可证

[GNU General Public License v3.0 (GPLv3)](LICENSE)

### 第三方许可证

| 名称 | 许可证 |
|---|---|
| Kotlin, AndroidX, Jetpack Compose, Room, DataStore, Glance, WorkManager | Apache 2.0 |
| Coil | Apache 2.0 |
| MapLibre Native for Android | BSD-2-Clause |
| OpenStreetMap 数据 | ODbL 1.0 |
| Nominatim | GPL v2（服务使用） |
| holidays-jp | MIT |
