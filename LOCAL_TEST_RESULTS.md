# 로컬 JVM 테스트 결과

이 파일은 로컬에서 JDK 21로 `TESTS.md`의 코어 테스트를 재실행한 결과입니다. Android APK 빌드 성공을 의미하지 않습니다.

```text
OK: 6 hostname matching checks
HlsPlaylistTest: 11 checks passed
RangePlannerTest: 4 tests passed
ParallelRangeDownloaderTest: pause, resume, 4-way content, no-range fallback passed
```

GitHub Actions는 별도로 JDK 17 환경에서 같은 테스트를 수행하고 `:app:assembleDebug`로 APK 컴파일을 시도합니다.
