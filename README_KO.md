<p align="center">
  <img src="screenshots/icon.png" width="80" alt="Golendar 아이콘">
</p>
<p align="center">
  <strong>Your idea, your future.</strong>
</p>
<p align="center"><a href="README_EN.md">English</a> · <a href="README.md">日本語</a> · <a href="README_zh-CN.md">简体中文</a> · <b>한국어</b></p>

# 📅 Golendar

월간・주간・일간 표시를 갖춘 Android 캘린더 앱입니다.  
프라이버시를 중시하는 **Golendar 모드**(앱 내 완결・여러 로컬 캘린더 지원)와 **Google 모드**(Google 캘린더 동기화) 두 가지를 탑재하고 있습니다.

> **자세한 사용법은 앱 내 튜토리얼을 참고하세요**  
> 설정 → '튜토리얼'에서 그림으로 설명하는 안내를 17종류 이상 제공합니다.  
> 캘린더 권한, 제스처, 지도, AI 분석, 일정 추가・편집, 반복, 사진, 검색, 알림, 백업, 위젯 등.

---

## 📲 다운로드

최신 APK는 [GitHub Releases](https://github.com/monsivamon/Golendar/releases/latest)에서 받을 수 있습니다.  
APK를 기기에서 열고 '알 수 없는 앱 설치'를 허용하여 설치하세요.

---

## 📸 스크린샷

| 월간 | 주간 | 일간 |
|:-:|:-:|:-:|
| <img src="screenshots/Month.png" width="200"> | <img src="screenshots/Weekly.png" width="200"> | <img src="screenshots/Day.png" width="200"> |

| 일정 편집 | 설정 | 위젯 |
|:-:|:-:|:-:|
| <img src="screenshots/ScheduleEdit.png" width="200"> | <img src="screenshots/Setting.png" width="200"> | <img src="screenshots/Widget_1.png" width="200"> |

---

## ✨ 주요 기능

- **두 가지 캘린더 모드**: Golendar 모드(앱 내 완결. 공휴일 가져오기・지도 표시에만 네트워크 사용)와 Google 모드(Google 캘린더 동기화).
- **월・주・일 표시**: 스와이프로 기간 이동・탭 전환. 월 표시 하단의 일정 목록은 ON/OFF 가능. 과거 2년~앞으로 2년의 일정 검색 지원.
- **일정 관리**: 추가・편집・삭제, 반복(매일/평일/매주/매월/매년, 종료일 지정), 텍스트 공유.
- **사진・AI 분석(Golendar 모드)**: 사진은 최대 5장(긴 변 1920px로 자동 축소). AI 분석은 BETA이며 캘린더 이미지에서 일정을 읽어 일괄 등록할 수 있습니다.
- **알림・시간대**: 정각・10분 전 알림. 공휴일・문화 행사는 알림 대상 제외(생일은 대상). 종일 일정은 표시 시간대의 9:00에 알림. 표시 시간대는 약 600개 시간대에서 고정할 수 있고, 알림 시각도 현지 시계에 맞출 수 있습니다.
- **공휴일**: G7・BRICS 창립 회원국・한국・대만・홍콩의 15개국・지역에서 여러 개 선택. 같은 날의 공휴일은 한 줄로 합쳐 국기로 표시합니다.
- **위젯・바로가기**: 일/주/월 3종 위젯. 앱 아이콘을 길게 눌러 '일정 추가'・'오늘'・'검색'.
- **백업 및 복원**: JSON / 사진 포함 ZIP(Golendar 모드). 캘린더별 분배, 복원 전 미리보기, 중복 건너뛰기 지원.
- **사용자 지정**: 테마, 배경 16색, 요일 색, 캘린더 색(두 모드), 일정별 색(Golendar 모드), 주 시작 요일, 4개 언어.

---

## 🚀 시작하기

1. APK를 설치하고 실행
2. 최초 실행 안내에 따라 알림 허용
3. Google 모드를 사용하는 경우 캘린더 접근을 허용하고 표시할 캘린더 선택
4. 지도는 최초 이용 시 위치 정보 안내 표시(거부해도 지도 기능 이용 가능)

---

## ⚠️ 주요 주의사항

- **캘린더 권한**: Google 모드에서는 `READ_CALENDAR` / `WRITE_CALENDAR`가 필요합니다. 거부 시 Golendar 모드로 자동 전환. 영구 거부 시 설정 화면으로 안내합니다.
- **표시 캘린더**: 최소 1개 이상 표시해야 합니다. Google 모드에서 캘린더를 추가・삭제한 후 '캘린더 목록 새로고침'을 실행하세요.
- **로컬 캘린더 삭제**: 기본(내 캘린더) 이외를 삭제하면 소속 일정도 함께 삭제됩니다(삭제 전 확인 대화상자 표시).
- **백업**: Google 모드에서는 '복원(덮어쓰기)'이 불가하며 '추가'만 가능합니다. 구 형식 백업은 앱 내 백업 화면의 안내를 따르세요.
- **알림**: 배터리 최적화 해제와 정확한 알람 허용을 권장합니다. 일부 제조사 기기에서는 지연될 수 있습니다.
- **시간대 고정**: ON일 때 시간 지정 일정의 생성・편집은 고정 시간대 기준으로 저장됩니다(종일 일정은 UTC 날짜 경계).
- **지도**: 네트워크 연결이 필요합니다. 위치 정보를 거부해도 지도 기능은 사용할 수 있습니다.
- **Golendar 모드 전용**: 사진 첨부, AI 분석, 일정별 색.
- **공휴일 데이터**: 일본은 `holidays-jp`, 그 외 대상국은 `vacanza/holidays` 공개 JSON을 사용합니다. 오프라인 시 마지막으로 가져온 데이터를 표시하며, 백업에는 포함되지 않습니다(복원 시 다시 가져옴).

---

## 🛠 사용 기술

| 카테고리 | 기술 |
|---|---|
| 언어 | Kotlin |
| UI | Jetpack Compose |
| 내비게이션 | Navigation Compose |
| 위젯 | Jetpack Glance |
| 로컬 DB | Room |
| 설정 | Preferences DataStore |
| 백그라운드 | Coroutines, AlarmManager, WorkManager |
| 지도 | MapLibre Native GL (OpenFreeMap) |
| 역지오코딩 | Nominatim, 일본 국토지리원 |
| 이미지 로드 | Coil |
| 아키텍처 | MVVM |

---

## 🙏 Credits

- 공휴일 데이터(일본): [holidays-jp](https://github.com/holidays-jp/date.json)(MIT)
- 공휴일 데이터(그 외 대상국): [vacanza/holidays](https://github.com/vacanza/holidays)(MIT)
- 지도 데이터: [OpenStreetMap](https://www.openstreetmap.org/) contributors(ODbL)
- 타일 배포: [OpenFreeMap](https://openfreemap.org/)
- 역지오코딩: [Nominatim](https://nominatim.org/) / [일본 국토지리원](https://mreversegeocoder.gsi.go.jp/)
- 라이브러리: MapLibre Native for Android(BSD-2-Clause), AndroidX / Jetpack(Apache 2.0), Kotlin(Apache 2.0), Coil(Apache 2.0)
- 에셋: Android robot(Google 제작, CC BY 3.0)

---

## 📄 라이선스

[GNU General Public License v3.0 (GPLv3)](LICENSE)

### 서드파티 라이선스

| 이름 | 라이선스 |
|---|---|
| Kotlin / AndroidX / Jetpack / Room / DataStore / Glance / WorkManager | Apache 2.0 |
| Coil | Apache 2.0 |
| MapLibre Native for Android | BSD-2-Clause |
| OpenStreetMap 데이터 | ODbL 1.0 |
| Nominatim | GPL v2(서비스 이용) |
| holidays-jp / vacanza/holidays | MIT |
