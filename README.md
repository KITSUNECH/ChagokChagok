# 차곡車곡

차로 쓰는 성장일기. 초보 운전자의 주행을 차가 읽어 두었다가, 내린 뒤 그림일기 한 편으로 돌려주는 Pleos Connect 앱입니다.

## 실행 방법

1. Android Studio에서 Pleos Connect Emulator(API 34)를 실행합니다. AVD는 Cold boot로 설정합니다.
2. 이 프로젝트를 빌드해 실행하거나, `apk/` 폴더의 APK를 설치합니다.
   ```bash
   adb install -r apk/chagokchagok_debug.apk
   ```
3. Virtual Console을 엽니다.
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   브라우저에서 `http://127.0.0.1:8088/` 을 엽니다.

## 테스트 환경과 필요한 설정

- Pleos Connect Emulator 2.1.0, Android API 34
- Vehicle SDK 2.3.2, NaviHelper SDK 3.0.5
- 권한: `CAR_LIGHTS`, `CAR_DRIVE`, `CAR_WINDOWS`, `CAR_SEATS`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`

## 써 보는 순서

1. 홈에서 운전석에 앉은 사람을 **아빠**로 고릅니다.
2. Virtual Console에서 기어를 D에 놓고, 깜빡이와 브레이크를 몇 번 조작한 뒤 P에 놓습니다. 일기가 써집니다.
3. **이 길 건네주기**를 눌러 길 이름을 고르고 한마디를 얹어 남깁니다.
4. 홈에서 운전석에 앉은 사람을 **나**로 바꿉니다. 아빠가 남긴 길이 나타납니다.
5. **출발 준비**에서 깜빡이, 와이퍼, 전조등, 안전벨트, 기어를 한 번씩 조작해 불이 들어오는지 봅니다.
6. **이 길로 출발**을 누르고 기어를 D에 놓습니다. 주행 중 그 자리에 이르면 한마디가 나옵니다.
7. P에 놓으면 "같은 길, 다른 자리" 일기가 써집니다.

홈의 앱 이름을 길게 누르면 점검 창이 열립니다. 신호 연결 상태를 보고, 예시 길을 넣거나 기록을 지울 수 있습니다.

## 구조

| 파일 | 하는 일 |
|---|---|
| `Car.kt` | Vehicle SDK와 NaviHelper SDK에 연결해 신호를 받는다 |
| `Writer.kt` | 주행을 기록하고(Recorder) 일기 문장으로 바꾼다(Writer) |
| `Model.kt` | 주행, 건네준 길, 일기의 모양과 저장 |
| `Views.kt` | 주행 선, 원고지, 계기판 표시등을 직접 그린다 |
| `MainActivity.kt` | 화면 여섯 가지와 흐름 |
| `GleoReceiver.kt`, `assets/schema.json` | 글레오가 부르면 기록을 돌려준다 |

## 지금 되는 것과 아닌 것

- 일기 문장은 규칙으로 만듭니다. 생성형 AI는 쓰지 않습니다.
- 가족 사이의 건네준 길은 한 대의 차 안에서 운전자를 바꾸는 방식으로 구현했습니다. 다른 차와의 연결은 계정 연동이 필요해 구현하지 않았습니다.
- "도착 알리기"는 화면 표시만 합니다.
- 글레오 연동은 문서의 형식대로 구현했으나 실제 호출은 에뮬레이터에서 확인이 필요합니다.

## 쓰인 글꼴

- Do Hyeon, Gowun Batang: SIL Open Font License 1.1 (Google Fonts)
- 확인 효과음(`res/raw/confirm.wav`)은 이 프로젝트에서 직접 만든 소리입니다.
