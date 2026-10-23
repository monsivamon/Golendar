<p align="center">
  <img src="screenshots/icon.png" width="80" alt="Golendar icon">
</p>
<p align="center">
  <strong>Your idea, your future.</strong>
</p>

<p align="center"><b>English</b> · <a href="README.md">日本語</a> · <a href="README_zh-CN.md">简体中文</a> · <a href="README_KO.md">한국어</a></p>

# 📅 Golendar

An Android calendar app with month, week, and day views.  
It offers two modes: privacy-focused **Golendar Mode** (self-contained in the app, with multiple local calendars) and **Google Mode** (syncs with Google Calendar).

> **For detailed usage, see the in-app tutorial**  
> Go to Settings → "Tutorial" for 17+ illustrated guides.  
> Covers calendar permissions, gestures, the map, AI parsing, adding/editing events, recurring events, photos, search, notifications, backup, widgets, and more.

---

## 📲 Download

Get the latest APK from [GitHub Releases](https://github.com/monsivamon/Golendar/releases/latest).  
Open the APK on your device and allow "Install unknown apps" to install.

---

## 📸 Screenshots

| Month | Week | Day |
|:-:|:-:|:-:|
| <img src="screenshots/Month.png" width="200"> | <img src="screenshots/Weekly.png" width="200"> | <img src="screenshots/Day.png" width="200"> |

| Event edit | Settings | Widget |
|:-:|:-:|:-:|
| <img src="screenshots/ScheduleEdit.png" width="200"> | <img src="screenshots/Setting.png" width="200"> | <img src="screenshots/Widget_1.png" width="200"> |

---

## ✨ Key Features

- **Two calendar modes**: Golendar Mode (self-contained; network used only for holidays and the map) and Google Mode (Google Calendar sync).
- **Month, week, and day views**: Swipe to move between periods or switch tabs. The event list below the month grid can be toggled on/off. Search covers 2 years back to 2 years ahead.
- **Event management**: Add, edit, and delete; recurring events (daily/weekdays/weekly/monthly/yearly with an end date); share as text.
- **Photos and AI parsing (Golendar Mode)**: Attach up to 5 photos (auto-resized to a 1920px long edge). AI parsing is BETA and registers events in bulk from a calendar image.
- **Notifications and time zone**: Notify at start and 10 minutes before. Holidays and cultural events are excluded (birthdays are included). All-day events notify at 9:00 in the display time zone. Pin the display time zone from about 600 zones, and optionally match notification times to the local clock.
- **Holidays**: Choose from 15 countries/regions: the G7, original BRICS members, Korea, Taiwan, and Hong Kong. Same-day holidays are combined into one row with flags.
- **Widgets and shortcuts**: Day/week/month widgets. Long-press the app icon for "Add Event", "Today", and "Search".
- **Backup and restore**: JSON or ZIP with photos (Golendar Mode). Supports per-calendar assignment, pre-restore preview, and duplicate skipping.
- **Customization**: Theme, 16 background colors, day-of-week colors, calendar colors (both modes), per-event colors (Golendar Mode), week start, and 4 languages.

---

## 🚀 Getting Started

1. Install the APK and launch the app
2. Follow the first-launch prompts and allow notifications
3. To use Google Mode, allow calendar access and select the calendars to display
4. The first time you use the map, a location permission prompt appears (the map still works if you decline)

---

## ⚠️ Important Notes

- **Calendar permission**: Google Mode requires `READ_CALENDAR` / `WRITE_CALENDAR`. If denied, the app switches to Golendar Mode automatically. If permanently denied, it guides you to the settings screen.
- **Visible calendars**: At least one calendar must remain visible. After adding or removing calendars in Google Mode, run "Refresh calendar list".
- **Deleting local calendars**: Deleting any calendar other than the default (My Calendar) also deletes its events. A confirmation dialog shows the count first.
- **Backup**: In Google Mode, "Restore (overwrite)" is unavailable; only "Append" is possible. For old-format backups, follow the guidance in the in-app backup screen.
- **Notifications**: Disabling battery optimization and allowing exact alarms is recommended. Notifications may be delayed on some manufacturers' devices.
- **Pinned time zone**: When enabled, new and edited timed events are saved in the pinned zone (all-day events use UTC date boundaries).
- **Map**: Requires a network connection. The map is still usable if you decline location access.
- **Golendar Mode only**: Photo attachments, AI parsing, and per-event colors.
- **Holiday data**: Japan uses `holidays-jp`; other supported countries use the public JSON from `vacanza/holidays`. When offline, the last fetched data is shown. Holiday data is not included in backups and is re-fetched on restore.

---

## 🛠 Tech Stack

| Category | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Navigation | Navigation Compose |
| Widgets | Jetpack Glance |
| Local DB | Room |
| Settings | Preferences DataStore |
| Background | Coroutines, AlarmManager, WorkManager |
| Map | MapLibre Native GL (OpenFreeMap) |
| Reverse geocoding | Nominatim, Geospatial Information Authority of Japan |
| Image loading | Coil |
| Architecture | MVVM |

---

## 🙏 Credits

- Holiday data (Japan): [holidays-jp](https://github.com/holidays-jp/date.json) (MIT)
- Holiday data (other supported countries): [vacanza/holidays](https://github.com/vacanza/holidays) (MIT)
- Map data: [OpenStreetMap](https://www.openstreetmap.org/) contributors (ODbL)
- Tile delivery: [OpenFreeMap](https://openfreemap.org/)
- Reverse geocoding: [Nominatim](https://nominatim.org/) / [Geospatial Information Authority of Japan](https://mreversegeocoder.gsi.go.jp/)
- Libraries: MapLibre Native for Android (BSD-2-Clause), AndroidX / Jetpack (Apache 2.0), Kotlin (Apache 2.0), Coil (Apache 2.0)
- Assets: Android robot (created by Google, CC BY 3.0)

---

## 📄 License

[GNU General Public License v3.0 (GPLv3)](LICENSE)

### Third-Party Licenses

| Name | License |
|---|---|
| Kotlin / AndroidX / Jetpack / Room / DataStore / Glance / WorkManager | Apache 2.0 |
| Coil | Apache 2.0 |
| MapLibre Native for Android | BSD-2-Clause |
| OpenStreetMap data | ODbL 1.0 |
| Nominatim | GPL v2 (service usage) |
| holidays-jp / vacanza/holidays | MIT |
