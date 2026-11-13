# MediaShield Browser v6.0 — Android (VPN 제거 · 다운로드 강화)

**GitHub Actions 자동 APK 빌드:** [`GITHUB_ACTIONS_시작하기.md`](GITHUB_ACTIONS_시작하기.md) 참고. 사용자 본인 GitHub 저장소에 업로드하면 자동으로 빌드합니다. APK는 아직 미포함입니다.


**이 파일은 앱의 개발 소스 프로젝트입니다. APK는 포함하지 않습니다.** Android Studio 또는 제공한 GitHub Actions 워크플로에서 `app-debug.apk`를 빌드해야 합니다. 실제 Android 기기 실행은 아직 검증하지 못했습니다.

## 무엇을 만든 것인가

Brave **공식 앱이나 Brave Chromium 포크가 아닙니다.** 독립적인 Android **WebView 기반** 브라우저이며, Brave에서 웹페이지/직접 미디어 URL을 Android 공유 메뉴로 보내 열 수 있습니다. Brave 확장 프로그램, 동기화, 모든 사이트의 자동 영상 저장 기능까지 구현한 것은 아닙니다.

### 유지한 기능

- 주소 입력·검색, 뒤로/앞으로, 새로고침, 즐겨찾기
- 동영상/이미지 URL 자동 감지: WebView 리소스 요청 + 페이지의 `<video>`, `<source>`, 이미지, 직접 미디어 링크 주기적 확인
- Brave/다른 앱의 Android **공유**로 URL 수신 (Brave의 기존 쿠키/로그인 세션 자체는 공유되지 않음)
- 광고/추적기 **도메인 차단 기본 켜짐** (`shouldInterceptRequest`), 일부 광고 영역 숨기기, 자동 팝업 제한
- 사이트별 차단 예외, 차단 건수 표시, StevenBlack/hosts 목록의 **수동 갱신**
- 다운로드 대기열, 진행률·전송 속도, 일시정지/재개/취소, 알림
- 저장 경로: **Downloads/MediaShieldBrowser** (MediaStore)

### v6에서 강화한 다운로드

1. **4분할 고속 다운로드:** 직접 영상/파일 주소, 총 크기 **16MiB 이상**, HTTP `Range` 206 응답과 ETag 또는 Last-Modified 검사 조건이 맞으면 4개 구간을 병렬 수신합니다. 서버가 지원하지 않으면 일반 단일 연결 다운로드로 전환합니다. **서버/네트워크 상태에 따라 빨라지지 않을 수도 있습니다.**
2. **분할 파일 이어받기:** 앱 안에서 일시정지 후 재개하거나 서비스 중단 후 다시 시작하면 완료된 구간은 유지하고 남은 바이트를 받습니다. 재개 전 파일 식별 정보가 달라지면 잘못 합치지 않도록 기존 구간을 초기화합니다. 단일 연결에서도 지원 서버에 HTTP Range로 재개를 요청합니다.
3. **HLS 화질 선택:** `.m3u8` 마스터 재생목록에 여러 변형 스트림이 있으면 대역폭 및 제공된 해상도를 보여주고 선택할 수 있습니다. 마스터가 아니면 바로 다운로드 대기열에 추가합니다.
4. **일반 HLS VOD와 라이브 녹화:** DRM/암호화가 없는 **MPEG-TS 세그먼트** 기반 HLS만 조각을 합쳐 `.ts`로 저장합니다. 라이브 재생목록은 계속 갱신하여 저장하며 `녹화 종료·저장`으로 종료합니다. 일시정지·재개와 마지막 세그먼트 번호 기록을 지원합니다. 네트워크 중단/만료된 라이브 세그먼트는 복원되지 않을 수 있습니다.
5. **URL 직접 입력 및 감지 목록:** 영상 링크 붙여넣기, 다운로드 목록에서 전송 속도/상태 확인, 고속 다운로드 ON/OFF 설정.

### VPN 완전 제거

- VPN 메뉴, 서버 입력, `VpnManager`/IKEv2 코드, VPN 권한을 모두 제거했습니다.
- 광고 차단은 이 **앱 내부 브라우저**에 적용되고 시스템 전체나 기존 Brave 앱을 차단하지 않습니다.

## 의도적으로 지원하지 않는 항목

- DRM 보호 동영상 또는 암호화된 스트리밍의 보호 기술 우회
- `blob:`/MSE 내부 버퍼 영상, DASH(.mpd), 암호화 HLS, fMP4 기반 HLS/LL-HLS, 원본 라이브 영상의 누락된 과거 구간 복구
- 웹사이트에서 다운로드를 제한하거나 로그인 쿠키/접근 권한이 다른 콘텐츠
- `.ts`를 MP4로 변환하는 리먹싱/인코딩, HLS 별도 오디오 트랙 병합, 모든 사이트의 영상·광고를 완벽하게 감지/차단
- 네트워크 요청을 직접 분석하는 Brave 원본 브라우저 내부 확장 기능. 본 앱에서 열린 WebView 페이지의 네트워크 요청만 감지됩니다.

**중요:** 본인에게 저장 권한이 있는 미디어에 사용하세요. 일부 웹사이트 약관 또는 저작권 제한이 적용될 수 있습니다. 인증 우회나 DRM 해제 코드는 포함하지 않습니다.

## APK 생성 방법

### A. PC: Android Studio

1. ZIP 압축을 풀고 **`PrivacyMediaBrowser_v6_NoVPN` 폴더**를 Android Studio에서 엽니다.
2. JDK 17, Android SDK 35, Gradle 8.9(또는 호환 버전), Android Gradle Plugin 8.7.3을 사용하고 `Sync Project with Gradle Files`를 실행합니다.
3. `Build` → `Build Bundle(s) / APK(s)` → **`Build APK(s)`**.
4. 성공 시 생성 경로: `app/build/outputs/apk/debug/app-debug.apk`.
5. 갤럭시 폰에 APK를 옮긴 뒤 Android의 허용 확인을 거쳐 설치합니다. 또는 USB 디버깅 활성화 후 `adb install -r app-debug.apk`.

### B. PC에 Android Studio 없이: GitHub Actions

1. 개인 GitHub 저장소를 새로 만듭니다.
2. ZIP 내부의 **내용물 전체** (`app`, `.github`, `build.gradle`, `settings.gradle`, `gradle.properties`)를 **저장소 최상위**에 올립니다.
3. 저장소의 `Actions` → **Build Android debug APK** → **Run workflow**.
4. 빌드에 성공하면 `PrivacyMediaBrowser-v6-NoVPN-debug-apk` 아티팩트를 받아 압축을 풀고 `app-debug.apk`를 설치합니다.

GitHub Actions가 외부 Gradle/Android SDK 다운로드와 빌드를 수행합니다. 빌드 오류가 나면 작업 로그를 확인하세요.

## 기기에서 확인할 항목

1. 앱의 URL 칸에 웹페이지를 열고 `미디어`에서 파일 주소가 나타나는지 확인.
2. 직접 MP4 파일을 다운로드 후 `내 파일 → 다운로드 → MediaShieldBrowser`에 저장되는지 확인.
3. 크기 16MiB 이상이며 `HTTP Range`와 validator를 제공하는 서버에서 **고속 분할 다운로드** 사용. 중간 일시정지/재개 후 결과 파일 비교.
4. 마스터 HLS URL에서 `화질 선택 / HLS 받기` 클릭, 원하는 화질 선택. 라이브라면 `받기 목록 → 녹화 종료·저장`.
5. `광고차단`에서 기본 활성화 상태, 사이트별 예외, 외부 차단 목록 갱신을 확인.
6. Android 공유 메뉴에서 Brave → 링크 공유 → MediaShield Browser로 URL 전달.

## 테스트 및 제약

- 순수 Java 부분은 **JDK**로 컴파일하여 테스트: `HostMatcherTest`, `HlsPlaylistTest`, `RangePlannerTest`, `ParallelRangeDownloaderTest`.
- `ParallelRangeDownloaderTest`는 임시 로컬 HTTP 서버에서 **4분할 바이트 결과 검증**, **일시정지 후 재개**, **Range 미지원시 fallback**을 테스트합니다.
- **이 실행 환경에는 Android SDK/Gradle이 없어 실제 APK 생성 및 Galaxy 휴대전화 실기기 테스트는 수행하지 못했습니다.** 단말에서 설치·실행하려면 위 방식으로 빌드하고 테스트가 필요합니다.
- Android 15 이상에서 `dataSync` 포그라운드 서비스는 백그라운드 실행 시간이 제한됩니다(일반적으로 24시간 중 총 6시간). 매우 긴 라이브 녹화는 시스템에 의해 중지될 수 있습니다.
- 웹 브라우징의 HTTP 주소도 허용하지만 암호화되지 않은 HTTP 사이트에서는 개인정보 전송을 피하세요. HTTPS 연결을 권장합니다.
- 알림 권한은 Android 13 이상에서 요청하며, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` 권한만 다운로드에 사용합니다. **VPN 권한 없음.**
- 외부 광고 호스트 목록 출처: https://github.com/StevenBlack/hosts (사용자가 갱신을 누를 때만 다운로드). 목록의 적용 라이선스는 원본을 참조하세요.
