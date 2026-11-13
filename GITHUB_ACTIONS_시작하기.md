# GitHub Actions로 안드로이드 APK 만들기

이 파일은 **MediaShield Browser v6.0 (VPN 없음)** 소스와 함께 제공됩니다. 실제 APK가 이미 들어있는 것은 아닙니다.

## 방법 A — Windows PC에서 거의 자동으로 업로드 (권장)

**준비물:** Windows PC, GitHub 계정, [Git for Windows](https://git-scm.com/downloads/win), [GitHub CLI](https://cli.github.com/). Android Studio는 필요 없습니다.

1. `MediaShieldBrowser_GitHubActions_Ready.zip` 압축을 해제합니다.
2. 압축 해제한 프로젝트 폴더의 빈 곳에서 **터미널(또는 PowerShell) 열기**를 선택합니다.
3. 아래 명령을 붙여넣습니다. 스크립트는 소스를 확인하고 사용자가 승인한 후에 **비공개** 저장소를 만듭니다.

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\GitHub_업로드.ps1
   ```

4. 처음이라면 GitHub 브라우저 로그인을 완료합니다. 저장소 이름은 Enter를 누르면 기본값을 사용합니다.
5. `Y`를 입력하여 **비공개 저장소 생성 및 소스 코드 업로드**에 동의합니다.
6. 업로드 후 열리는 GitHub **Actions** 페이지에서 `Build MediaShield Android APK` 작업의 녹색 체크 표시를 기다립니다.
7. 해당 실행 기록을 클릭하고 맨 아래 **Artifacts → MediaShieldBrowser-Android-debug-APK**를 다운로드합니다.
8. 받은 ZIP 파일을 해제하면 `app-debug.apk`가 있습니다. 휴대전화로 복사하고 갤럭시의 설치 허용 안내에 따라 설치합니다.

> 스크립트 실행이 회사 PC 정책 등에 의해 막혀 있다면 방법 B를 사용하세요. 이 스크립트는 로컬 Git 초기화와 개인 GitHub 비공개 저장소의 생성·업로드만 수행하며, 외부 VPN·서버 로그인 정보를 요구하지 않습니다. 스크립트 내용을 먼저 읽고 실행하세요.

## 방법 B — GitHub 웹사이트로 수동 업로드

1. [github.com/new](https://github.com/new)에서 새 저장소를 생성합니다. `Private`를 선택하고 README, .gitignore 등 초기 파일 생성 옵션은 선택하지 않습니다.
2. 생성된 빈 저장소에서 **uploading an existing file** (기존 파일 업로드)를 클릭합니다.
3. ZIP이 아닌 **압축 해제된 프로젝트 폴더 안의 내용**을 업로드합니다. `.github/workflows/build-apk.yml`, `app/`, `build.gradle`, `settings.gradle`, `gradle.properties`, `tests/`가 저장소 최상위에 있어야 합니다. 숨김 폴더인 `.github`가 빠지지 않았는지 꼭 확인하세요.
4. 파일을 커밋하면 `push` 이벤트로 빌드가 시작됩니다. 필요하면 `Actions → Build MediaShield Android APK → Run workflow`를 눌러 수동 실행할 수 있습니다.
5. 녹색 체크 표시가 뜨면 실행 결과의 **Artifacts**에서 APK ZIP을 받습니다.

## APK 설치

- APK 이름: `app-debug.apk`
- 빌드 결과는 디버그 서명된 APK이므로 갤럭시 기기에 직접 설치할 수 있습니다. 구글 Play 스토어 게시용 릴리스 APK는 아닙니다.
- `내 파일` 앱에서 APK를 열 때 안드로이드가 요구하는 **이 출처의 앱 설치 허용**을 승인해야 할 수 있습니다.
- 설치 전 APK를 바이러스/권한 검사할 수 있습니다. 소스가 수정되면 재빌드해야 합니다.

## 실행이 빨간 X로 실패했다면

- `Actions → 실패한 실행 기록 → Build and test Android debug APK`로 이동하여 실패한 **step의 로그**를 확인합니다.
- `Run pure-Java core tests` 실패: Java 코어 테스트 오류.
- `Install Android 35 SDK` 실패: SDK 다운로드 / 네트워크 문제.
- `Build installable debug APK` 실패: Gradle/Android 앱 컴파일 오류. 오류 메시지를 채팅에 붙여 넣으면 수정할 수 있습니다.
- **아티팩트는 빌드 성공 뒤에만 생성됩니다.** 처음 만든 소스의 실제 Android 빌드는 아직 검증되지 않았습니다.

## GitHub Actions 구성 정보

- GitHub Actions: `.github/workflows/build-apk.yml`
- GitHub Actions `push` 자동 실행 + `workflow_dispatch` 수동 실행
- Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Android SDK 35
- 빌드 단계에서 4개의 Java 테스트 클래스 실행 후 `:app:assembleDebug`
- 결과: `MediaShieldBrowser-Android-debug-APK` 아티팩트 (기본 30일 보관)
- VPN 코드는 없습니다. Brave 공식 앱은 아니며 WebView 기반 독립 브라우저입니다.
