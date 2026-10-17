<p align="center">
  <img src="screenshots/icon.png" width="80" alt="Golendar icon">
</p>
<p align="center">
  <strong>Your idea, your future.</strong>
</p>

<p align="center"><b>English</b> · <a href="README.md">日本語</a> · <a href="README_zh-CN.md">简体中文</a> · <a href="README_KO.md">한국어</a></p>

# 📅 Golendar

An Android calendar app with month, week, and day views.
It features two modes: privacy-focused **Golendar Mode** (fully self-contained within the app, supporting multiple local calendars) and **Google Mode** (syncs with Google Calendar).

---

## 📲 Download

Get the latest APK from [GitHub Releases](https://github.com/monsivamon/Golendar/releases/latest).
Open the APK on your device and install it after allowing "Install unknown apps".

---

## 📸 Screenshots

| Month | Week | Day |
|:-:|:-:|:-:|
| <img src="screenshots/Month.png" width="200"> | <img src="screenshots/Weekly.png" width="200"> | <img src="screenshots/Day.png" width="200"> |

| Event editing | Settings | Widget |
|:-:|:-:|:-:|
| <img src="screenshots/ScheduleEdit.png" width="200"> | <img src="screenshots/Setting.png" width="200"> | <img src="screenshots/Widget_1.png" width="200"> |

---

## ✨ Key Features

### Dual Engine
- **Golendar Mode**: No external syncing. Everything works within the app alone (network is used only for fetching holidays and displaying the map). You can create and manage multiple local calendars.
- **Google Mode**: Syncs with Google Calendar.

### Multiple Calendars in Golendar Mode
- Create multiple local calendars within the app and organize your events across them.
- "My Calendar" is created automatically on first launch. My Calendar cannot be deleted, but it can be hidden.
- Each calendar can have its own name, color, visibility ON/OFF, and display order.
- Deleting a calendar other than the default also deletes all of its events (a dialog shows the event count before deletion so you can confirm).
- On the event editing screen, you can choose which calendar to add the event to (when two or more calendars are visible).
- "Show All" turns every calendar's display ON at once (the number of visible calendars cannot be zero).
- When two or more calendars are visible, event cards display the calendar name and color.

### Multiple Calendars in Google Calendar
- Lists calendars per account so you can pick which ones to display.
- Individual visibility ON/OFF and color customization are available in the Settings screen.
- When two or more calendars are visible, event cards display the calendar name and color.
- "Refresh calendar list" keeps up with calendars being added or removed.

### Calendar Views
- Seamlessly switch between month, week, and day views.
- The event list below the month grid can be toggled ON/OFF (with the bottom list OFF, tapping a date opens a popup).
- The month grid shows up to 3 event bars per day, with overflow shown as "+N" (up to 6 with the bottom list OFF). While the bottom list is shown, an indicator appears when there are items not displayed.
- Event search (from 2 years in the past to 2 years in the future; holidays, cultural observation days, and birthdays are excluded).
- Swipe up/down to move to the previous or next period, and swipe left/right to switch between the Day, Week, and Month tabs.
- Event cards carry a type emoji (🎂 Birthday / 🎌 Culture / 🗾 Holiday).

### Pick a Location on the Map
- Choose a spot from the MapLibre Native + OpenFreeMap map.
- Includes a follow-current-location button. Saved in the format "POI name, address".

### Recurring Events
- Daily / Weekdays / Weekly / Monthly / Yearly. An end date can be specified.

### Share Events
- Share as text to other apps from the detail dialog.

### Photo Attachments (Golendar Mode only)
- Attach up to 5 photos to an event. Automatically downscaled to a 1920px longest edge.
- Supports an enlarged viewer, and exporting to your device's "Pictures/Golendar" folder.

### Bulk Event Registration via AI Analysis (BETA · Golendar Mode only)
- Have an external AI read a calendar image, and register events in bulk from the returned JSON.
- Supports copying the prompt and a share sheet. Select events in the preview before registering them.

### App Shortcuts
- Long-press the icon for "Add Event", "Today", and "Search".

### Home Screen Widgets
- Three types: Day, Week, and Month. They follow the app settings (theme, background color, visible calendars, and the show-holidays toggle).
- The month widget shows event dots for cells of the previous and next months as well.
- In Golendar Mode it reflects events from visible local calendars; in Google Mode, from visible Google calendars.

### Reminder Notifications
- Notifications at the exact time and 10 minutes before. Uses exact alarms from `AlarmManager` to counter Doze mode.
- Holidays and cultural observation days are not notified. **Birthdays are notified** (the Emperor's Birthday is treated as a holiday and thus excluded).
- Linked to the visible calendar settings (covers visible calendars in both Golendar and Google Modes).

### First-Launch Setup
0. Welcome screen (shown on first launch and on version upgrades)
1. Notification permissions (Notifications → Exact alarms → Disable battery optimization)
2. Permission to access Google Calendar
3. Choose calendars to display (when in Google Mode)
4. Gesture guide

Location permission is requested the first time you open the map.

### Backup & Restore
- **Exports the events of your visible calendars all at once** (both Golendar and Google Modes).
- With "Back up photos as well" ON, ZIP (Golendar Mode only). With it OFF, JSON.
- The backup file includes "original calendar info", allowing events to be assigned back to their calendars on restore.
- Pressing "Save" while multiple calendars are visible shows a dialog listing the calendars to be included.
- The filename has a date attached (e.g. `golendar_backup_YYMMDD.json`).
- **Pre-restore preview**: After picking a file, you can check the number of calendars, events, and photos it contains.
- **Choose which original calendars to restore**: Multiple selection via checkboxes (hidden automatically for backups in the old format).
- **Specify the restore destination**:
  - Google Mode: Select a single calendar as the restore destination.
  - Golendar Mode: Choose either "Auto-create calendars with the same names and assign events to them" or "Gather events into a specific local calendar".
- **Overwrite confirmation for same-name calendars**: In Golendar Mode, if a calendar with the same name already exists, a dialog asks whether to overwrite it.
- **Duplicate skipping when appending**: Events whose title, start, end, and all-day flag all match are not registered again.
- **Compatibility with old-format (v1.1.6 or earlier) backups**: Appending works in both Google/Golendar modes (you choose the calendar to append to). Restore (overwrite) is available only in Golendar Mode, restoring to My Calendar. Adding from an old-format ZIP in Google Mode is not possible.
- In Google Mode, "Restore (overwrite)" is unavailable and only "Append" can be used.
- Holiday data is not included and is re-fetched automatically on restore.

### Holidays & Cultural Events
- Fetched automatically from the `holidays-jp` API (on first launch and every 30 days).
- Holidays, cultural events, and birthdays are detected automatically and shown with colors and emoji.
- "Show holidays" can be toggled ON/OFF in the Settings screen.
- Holidays are always shown regardless of each calendar's visibility setting.

### Tutorials
- From "Tutorial" in the Settings screen, you can re-display all 13+ guides at any time.
- Contents: Calendar permissions / Gesture controls / How to use the map / AI analysis / Calendar visibility and color settings / The bottom list in month view / Adding events / Editing and deleting events / Recurring events / Using photos / How to search / Notification settings / Backup & restore / Widgets and shortcuts
- The first time you expand the "Calendar Mode" section, it automatically shows the differences between Golendar/Google and how to operate the calendar list.
- Each tutorial is a multi-page, illustrated walkthrough that carefully covers common pitfalls (such as the end date of repeats, the split behavior of "delete this day only", the difference between appending and restoring, and the effects of hidden calendars).

---

## 🎨 Customization

- Theme: Light / Dark / Follow system
- Languages: Japanese / English / 简体中文 / 한국어 (switch from the "Display language" section in Settings; the app stays on the Settings screen after switching. When not selected, the default is auto-detected from the device language)
- App background color: 16 pastel colors (synced with the calendar, settings, and widgets)
- Weekday colors: Can be set for every weekday
- Per-calendar colors: Can be set individually in **both Golendar Mode and Google Mode**. Assigned automatically according to the theme (light = 16 dark colors / dark = 16 light colors).
- Event color coding: Birthday = orange, Culture = green, Normal = theme color (calendar color takes priority)
- Start of the week: Sunday / Monday
- Holiday display: ON/OFF

---

## 🛠 Tech Stack

| Category | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Navigation | Navigation Compose |
| Widget | Jetpack Glance |
| Local DB | Room |
| Settings | Preferences DataStore |
| Background | Coroutines, AlarmManager, WorkManager |
| Map | MapLibre Native GL (OpenFreeMap) |
| Reverse geocoding | Nominatim, Geospatial Information Authority of Japan |
| Image loading | Coil |
| Architecture | MVVM |

---

## ⚠️ Notes

- **Calendar permissions**: In Google Mode, `READ_CALENDAR` / `WRITE_CALENDAR` are required. If denied, the app automatically falls back to Golendar Mode. If there is no Google account, a prompt appears when switching. If permission is permanently denied, a dialog guides you to the Settings screen.
- **Visible calendars**: **At least one calendar must be visible** (it cannot be zero). In Golendar Mode, My Calendar cannot be deleted, so at least one always remains. After adding or deleting calendars in Google Mode, run "Refresh calendar list".
- **Deleting local calendars**: Deleting a local calendar other than the default (My Calendar) also deletes all of its events. A dialog shows the count before deletion so you can confirm.
- **Switching to Google Mode**: In v1.1.7, if permission is already granted, switching happens instantly without restarting the app.
- **Notifications**: Disabling battery optimization and allowing exact alarms is recommended. Notifications may be delayed on some manufacturers' devices.
- **Map**: Requires an internet connection. The map feature works even if location permission is denied.
- **Search**: Holidays, cultural events, and birthdays are excluded.
- **Recurrence**: "End" refers to the duration of a single occurrence. The overall end date is specified separately.
- **Holiday data**: When offline, the last fetched data is shown.
- **Backup**: In Google Mode, "Restore (overwrite)" is unavailable and only "Append" can be used. The restore destination is chosen from writable calendars.
- **Old-format backups**: Backups from v1.1.6 or earlier can be appended in either Google/Golendar mode (you choose the calendar to append to). Restore (overwrite) is only available in Golendar Mode (restoring to My Calendar). To append from an old-format ZIP in Google Mode, switch to Golendar Mode beforehand.
- **Photo attachments**: Golendar Mode only. Excluded from cloud auto-backup.
- **AI analysis**: Golendar Mode only. Analysis results are reflected only in the local DB.

---

## 🙏 Credits

### Data & APIs
- Holiday data: [holidays-jp](https://github.com/holidays-jp/date.json) (MIT)
- Map data: [OpenStreetMap](https://www.openstreetmap.org/) contributors (ODbL)
- Tile delivery: [OpenFreeMap](https://openfreemap.org/)
- Reverse geocoding: [Nominatim](https://nominatim.org/) / [Geospatial Information Authority of Japan](https://mreversegeocoder.gsi.go.jp/)

### Libraries
- MapLibre Native for Android (BSD-2-Clause)
- AndroidX / Jetpack (Apache 2.0)
- Kotlin (Apache 2.0)
- Coil (Apache 2.0)

### Assets
- Android robot: created by Google (CC BY 3.0)

---

## 📄 License

[GNU General Public License v3.0 (GPLv3)](LICENSE)

### Third-Party Licenses

| Name | License |
|---|---|
| Kotlin, AndroidX, Jetpack Compose, Room, DataStore, Glance, WorkManager | Apache 2.0 |
| Coil | Apache 2.0 |
| MapLibre Native for Android | BSD-2-Clause |
| OpenStreetMap data | ODbL 1.0 |
| Nominatim | GPL v2 (service usage) |
| holidays-jp | MIT |
