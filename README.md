# 차곡차곡 (ChagokChagok)

**차로 쓰는 성장일기.** 가고 싶은 곳으로 운전하면 차가 본 신호 그대로 일기를 쓰고, 같은 길을 다시 가면 지난번의 나와 함께 쓰는 가족이 남긴 한마디를 그 자리에서 들려주는 Pleos Connect 앱입니다.

- **같은 길, 다른 날** — 혼자 쓰는 사람: 같은 목적지에 다시 가면 지난번의 나와 나란히 비교하고, 지난번에 남긴 한마디를 그 자리에서 들려줍니다.
- **같은 길, 다른 자리** — 함께 쓰는 차: 가족이 평소 달린 길에 한마디를 얹어 건네면, 받은 사람이 그 길을 달릴 때 남긴 자리에서 들려줍니다.
- 해낸 일은 오른쪽 **성장 바**에 한 칸씩 쌓입니다 (익히기 → 넓히기 → 차곡차곡).

## APK

| 항목 | 위치 |
|---|---|
| 제출용 APK | `apk/` 폴더 (`팀명_앱이름_release.apk`) |
| 앱 아이콘 (스토어 규격 512×512) | `tools/store_icon_512.png` |

## 두 가지 판

| 판 | 쓰임 | 시연용 기능 |
|---|---|---|
| `contest` (기본) | 공모전 제출·시연 | 들어 있음. 기본은 꺼짐, 켜면 화면에 "시연용" 표시 |
| `market` | Pleos App Market 탑재 준비 | **없음** (가상 친구, 모의 주행, 예시 길, 시험용 신호를 빼고 빌드) |

Android Studio의 Build Variants에서 `contestDebug` / `marketDebug` 를 고르거나, `./gradlew assembleContestRelease` · `./gradlew assembleMarketRelease` 로 빌드합니다.

## 실행 방법

1. Android Studio에서 **Pleos Connect Emulator 2.1.0**(Android API 34)을 실행합니다. AVD는 Cold boot를 권장합니다.
2. 이 저장소를 열고 ▶로 실행하거나, `apk/`의 APK를 설치합니다.
   ```bash
   adb install -r apk/<APK 파일 이름>
   ```
3. 차량 신호는 **Virtual Console**로 바꿉니다.
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   브라우저에서 `http://127.0.0.1:8088/` 을 엽니다.
4. 처음 실행하면 시작 안내(7단계)가 나옵니다. 이름, 운전 경험, 면허 딴 해, 혼자/함께, 위치·음성·날씨 사용 여부를 고릅니다. 위치 권한을 물으면 허용합니다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator 2.1.0, Android API 34 |
| 화면 규격 | **2560×1440 px** 가로 디스플레이 (`adb shell wm size` 결과). 넓은 창은 사이드바 배치이고, 분할 화면·좁은 창(아래 탭 배치)도 지원합니다. 화면 확인 테스트는 1280×720dp(가로)와 420×700dp(좁은 창)에서 했습니다. |
| 개발 환경 | Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, minSdk 28, targetSdk 34 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, FusedLocation 1.1.3 |
| AAOS API | CarUxRestrictions(주행 중 제한), CarPropertyManager `GEAR_SELECTION`(기어) |
| Gleo AI | App Schema(`assets/schema.json`) + `GleoReceiver` (공모전 참가팀은 별도 승인 없이 사용 가능) |

### 필요한 설정

- **위치 권한**(`pleos.car.permission.FUSED_LOCATION`, 실행 중 허락): 한마디를 남긴 자리에서 들려주는 데 씁니다. 운전자마다 따로 동의를 받습니다.
- **선택: 일기 문장 다듬기(생성형 AI)** — 설정 › 위치와 개인정보 › 일기 문장 다듬기에서 Anthropic API 키를 넣으면 켜집니다. 키는 저장소와 APK에 들어 있지 않고, 넣으면 기기 안 암호화 저장소에만 둡니다. 키가 없으면 차량 기록으로 규칙에 따라 씁니다.
- **선택: 시연용 기능** — 설정 › 연결 › 차량 연결 상태 › 시연 도구(공모전판에만 있음)에서 켭니다(기본은 꺼짐). 화면에 모두 "시연용"으로 표시됩니다.

## 무엇이 실제이고 무엇이 시연용인가

이번 공모전은 공개된 Pleos Playground 개발 문서의 SDK·API 범위에서 개발합니다. 문서에 있는 기능은 실제로 구현했고, 문서에 없는 기능만 시연용으로 표시했습니다.

| 기능 | 구분 | 쓰는 SDK·API |
|---|---|---|
| 주행 기록 (깜빡이·브레이크·와이퍼·전조등·안전띠·좌석·크루즈·주행거리) | 실제 | Vehicle SDK (신호마다 `check…Capabilities` 후 등록) |
| 기어 D에서 기록 시작, P에서 일기 | 실제 | AAOS `CarPropertyManager` `GEAR_SELECTION` |
| 엔진오일·브레이크액·워셔액 부족 알림 | 실제 | Vehicle SDK Safety |
| 이 차의 기어 (제조사 확인) | 실제 | Vehicle SDK CarInfo `getVINInfo` (차종은 운전자가 고름) |
| 회전 안내·목적지·도착·편안한 길·내비 화면 열기 | 실제 | NaviHelper SDK |
| 그 자리의 한마디 (반경 150m) | 실제 | FusedLocation SDK `addProximityAlert` + 진행 방향 확인 |
| 소리로 알려 주기 | 실제 | TextToSpeech SDK (ON_DEVICE), 오디오 포커스 |
| 달리는 동안 말로: 숨 고르기·여기 한마디 남기기·최근 일기 읽기 | 실제 | Gleo AI App Integration |
| 주행 중 화면 잠금, 입력 막기, 긴 글 줄이기 | 실제 | CarUxRestrictions (`activeRestrictions`) |
| 같은 차를 함께 쓰는 사람 (운전석 바꾸기) | 실제 | 앱 안 프로필 |
| 다른 차의 가족·친구와 잇기 (Pleos 계정 연동, 같이 가기) | **시연용** | 공개 API 없음 → 향후 계획 (`docs/향후계획.md`) |
| 모의 주행 | **시연용** | 에뮬레이터에서 내비가 차를 움직이지 않아 경로를 흉내 냄 |
| 표시등(경고등) 화면 확인 | **시험용 신호** | 표시등 API는 비공개 권한이라 쓰지 않음 |

## 시연 순서

1. **같은 길, 다른 날 (혼자)**: 내비에 목적지를 넣고 Virtual Console에서 기어 D → 깜빡이·브레이크 → P. 일기에서 "다음의 나에게 한마디"를 남기고, 같은 목적지로 한 번 더 다녀오면 일기에 지난번과 비교가 나옵니다.
2. **같은 길, 다른 자리 (함께)**: 설정 › 차량 연결 상태 › 시연 도구 › 예시 길과 친구 넣기 → 운전석을 "나"로 → 오늘 화면의 "아빠가 건네준 길" → 이 길로 출발.
   - 그 자리의 한마디: 에뮬레이터 ⋯ › Location › Routes에서 `tools/예시길.gpx`를 불러와 재생하면, 한마디를 남긴 자리 150m 안에서 들려줍니다.
   - 위치가 움직이지 않는 환경이면 출발 전 화면의 **모의 주행 (시연용)**을 씁니다. 이 기록은 성장 바·졸업·분석에서 빠집니다.
3. **출발 준비**: 깜빡이(양쪽)·와이퍼·전조등·기어(R→P)를 Virtual Console로 조작하면 하나씩 확인됩니다. 차종을 고르면 기어 쓰는 법과 안전 조작 10가지가 나옵니다.
4. **달리는 동안 말로**: 글레오에게 "차곡차곡, 숨 고를래", "차곡차곡, 여기 한마디 남겨 줘"라고 말합니다.

## 구조

| 파일 | 하는 일 |
|---|---|
| `MainActivity.kt` | 화면 이동, 사이드바·탭, 주행 모드 전환, 차 신호 연결 |
| `Trip.kt` | 주행 한 번: 시작·종료, 근접 한마디, 회전 전 깜빡이, 말로 남긴 한마디, 일기 만들기 |
| `Car.kt` | Vehicle SDK · NaviHelper SDK · GEAR_SELECTION |
| `Places.kt` · `Voice.kt` · `Restrictions.kt` | FusedLocation SDK(근접 알림), TextToSpeech SDK(오디오 포커스), CarUxRestrictions |
| `GleoReceiver.kt` · `assets/schema.json` | Gleo AI 연동 |
| `Writer.kt` · `AiDiary.kt` | 사실만으로 쓴 일기, (선택) 생성형 AI 다듬기와 필터 |
| `Model.kt` · `SecurePrefs.kt` | 기록 저장 (기기 안 암호화) |
| `Laws.kt` · `LawData.kt` · `Safety.kt` · `Shifters.kt` | 면허 딴 해 기준 법규, 안전 조작, 차종별 기어 (출처 포함) |
| `*Screen.kt` · `Onboarding.kt` · `Stack.kt` · `Tips.kt` | 화면, 시작 안내, 성장 바, 사용 팁 |

화면 확인: `./gradlew testContestDebugUnitTest --tests '*ScreenshotTest*'` 를 실행하면 넓은 창·밤·좁은 창의 화면이 `build/shots/` 에 PNG로 저장됩니다.

## 데이터와 외부 전송

- 일기, 주행 경로, 건넨 길은 이 차(앱) 안에만, 안드로이드 키스토어 키로 암호화해 저장합니다. 앱 서버는 없습니다.
- 위치는 운전자마다 동의를 받고, 동의하지 않은 운전자의 위치는 쓰지도 남기지도 않습니다.
- 길을 건넬 때는 출발한 곳 근처 500m를 빼고, 목적지와 한마디를 남긴 자리만 함께 갑니다.
- **날씨**(켜 둔 경우): 목적지 좌표만 Open-Meteo 공개 예보로 보냅니다.
- **일기 문장 다듬기**(키를 넣은 경우): 일기 초안만 Anthropic API로 갑니다. 초안에 없는 숫자가 들어간 문장은 쓰지 않고, 일기에서 원래 문장으로 되돌리거나 신고할 수 있습니다.

## 한계

- Pleos 계정 정보(이름 등)를 읽는 공개 API가 없어, 첫 설정에서 이름을 직접 입력합니다.
- 차종·트림을 알려 주는 공개 API가 없어, 기어 안내는 운전자가 차종을 한 번 고릅니다.
- 법규·안전 조작 문구는 법제처 생활법령정보, 정책브리핑, 현대자동차 사용설명서, 한국교통안전공단 자료로 확인했고 화면에 근거를 함께 적었습니다.

## 글꼴과 소리

Pretendard, 고운바탕, Noto Sans KR(아이콘) — SIL Open Font License 1.1
