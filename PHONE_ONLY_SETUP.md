# 📱 갤럭시만 사용: 첫 개인 서명키 만들기

**안전성 검증 완료 APK가 아닙니다. Play 프로텍트의 경고를 우회하지 마세요.**

1. 휴대전화 Brave에서 [GitHub 저장소 비밀값 설정](https://github.com/sinsun0373-glitch/MediaShield-Browser-Android/settings/secrets/actions) 을 엽니다. GitHub 로그인 필요. 메뉴가 잘 안 보이면 브라우저에서 '데스크톱 사이트'를 켭니다.
2. **New repository secret**을 누르고 첫 번째 비밀값을 등록하세요:
   - 이름: `MEDIASHIELD_STORE_PASSWORD`
   - 값: 본인이 만든 **16자 이상 긴 비밀번호**.
3. **권장(선택):** 다시 **New repository secret**으로 두 번째 비밀값을 등록하세요:
   - 이름: `MEDIASHIELD_BACKUP_PASSPHRASE`
   - 값: 앞의 비밀번호와 다른 **20자 이상 긴 백업 암호**. 없다면 첫 번째 서명 비밀번호를 암호화 백업에 함께 사용합니다. 별도 암호 사용이 더 안전합니다.
4. 사용한 암호를 비밀번호 관리자 등에 안전하게 백업하세요. 기존에 **MEDIASHIELD_KEY_PASSWORD**를 등록했다면 이것도 대신 인식합니다. **절대 채팅에 보내지 마세요.**
5. [Actions → First-time personal key and APK (phone only)](https://github.com/sinsun0373-glitch/MediaShield-Browser-Android/actions/workflows/personal-bootstrap.yml) 을 열고 **Run workflow**를 누릅니다. 확인 입력란에는 `GENERATE`만 넣습니다.
6. 완료되면 Actions의 아티팩트 두 개를 받습니다:
   - `SAVE-THIS-Encrypted-Personal-Key-Backup`: **반드시 휴대전화와 안전한 별도 장소에 백업**. GitHub 아티팩트는 7일 후 만료합니다.
   - `Personal-APK-REVIEW-NOT-VERIFIED`: 개인 서명 APK지만 **Play 프로텍트 안전 판정은 미확인**입니다.

이 워크플로를 두 번 실행하면 **서로 다른 서명키**가 만들어집니다. 한 번만 실행하세요. 사용자가 휴대전화에서 나중에 이 키를 재사용할 수 있는 복호화·비밀값 재등록 절차는 별도 설정이 필요합니다. 위 백업 ZIP 및 두 암호가 장기 업데이트를 위한 필수 준비물입니다.

문제가 생기면 실행 결과 캡처만 보내주세요. 비밀번호나 개인키는 보내지 마세요.
