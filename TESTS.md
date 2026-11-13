# JVM 독립 테스트 실행 (앱 APK 빌드는 별도)

Java 17 이상에서 프로젝트 루트 기준:

```sh
mkdir -p build/jvm-test
javac --add-modules jdk.httpserver -d build/jvm-test \
  app/src/main/java/com/example/bravemediasaver/HlsPlaylist.java \
  app/src/main/java/com/example/bravemediasaver/HostMatcher.java \
  app/src/main/java/com/example/bravemediasaver/RangePlanner.java \
  app/src/main/java/com/example/bravemediasaver/ParallelRangeDownloader.java \
  tests/*.java
for test in HostMatcherTest HlsPlaylistTest RangePlannerTest ParallelRangeDownloaderTest; do
  java --add-modules jdk.httpserver -cp build/jvm-test com.example.bravemediasaver.$test
done
```

Android 라이브러리 종속 코드는 JVM 테스트가 아니라 Android Studio/Gradle + 실제 기기에서 검증해야 합니다.
