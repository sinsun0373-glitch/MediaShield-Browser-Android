"""Device smoke checks: real APK UI, no site availability assumptions."""
import subprocess, time, re, xml.etree.ElementTree as ET
PKG = "com.example.privacymediabrowser"
def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=30)
def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    return list(ET.fromstring(adb("shell", "cat", "/sdcard/window.xml")).iter("node"))
def wait_for(predicate, seconds=25):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:
            for node in nodes():
                if predicate(node): return node
        except (ET.ParseError, subprocess.CalledProcessError, subprocess.TimeoutExpired): pass
        time.sleep(1)
    print(adb("shell","cat","/sdcard/window.xml"), flush=True)
    print(adb("logcat","-d","-s","AndroidRuntime:E"), flush=True)
    raise AssertionError("Expected UI element not found")
def click(node):
    x1,y1,x2,y2=map(int,re.findall(r"\d+",node.attrib["bounds"]))
    adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
def text_is(value): return lambda n:n.get("text")==value
def desc_is(value): return lambda n:n.get("content-desc")==value
adb("install","-r","app/build/outputs/apk/debug/app-debug.apk")
adb("shell","pm","grant",PKG,"android.permission.POST_NOTIFICATIONS")
adb("logcat","-c")
print(adb("shell","am","start","-W","-n",PKG+"/com.example.bravemediasaver.MainActivity","-a","android.intent.action.SEND","--es","android.intent.extra.TEXT","https://example.com"), flush=True)
click(wait_for(desc_is("메뉴")))
for label in ["영상 다운로드","다운로드 목록","감지된 미디어","연결 진단 · DNS 검사","고급 설정"]:
    wait_for(text_is(label))
click(wait_for(text_is("다운로드 목록")))
entry=wait_for(lambda n:n.get("class")=="android.widget.EditText" and "HLS" in n.get("text",""))
click(entry)
sample="https://example.com/test.mp4"
adb("shell","input","text",sample)
adb("shell","input","keyevent","4") # hide keyboard
time.sleep(4) # allow several refresh cycles
wait_for(text_is(sample))
click(wait_for(text_is("브라우저로 돌아가기")))
click(wait_for(desc_is("메뉴")))
click(wait_for(text_is("다운로드 목록")))
wait_for(text_is(sample))
click(wait_for(text_is("브라우저로 돌아가기")))
click(wait_for(desc_is("메뉴")))
click(wait_for(text_is("연결 진단 · DNS 검사")))
wait_for(text_is("영상 연결 진단"))
wait_for(lambda n:"실제 서드파티 쿠키:" in n.get("text",""))
click(wait_for(text_is("닫기")))
adb("shell","settings","put","system","accelerometer_rotation","0")
adb("shell","settings","put","system","user_rotation","1")
time.sleep(3)
wait_for(desc_is("메뉴"))
adb("shell","settings","put","system","user_rotation","0")
time.sleep(3)
click(wait_for(desc_is("메뉴")))
click(wait_for(text_is("다운로드 목록")))
wait_for(text_is(sample))
click(wait_for(text_is("브라우저로 돌아가기")))
click(wait_for(desc_is("메뉴")))
click(wait_for(text_is("내부 호환 엔진으로 열기")))
click(wait_for(text_is("호환 엔진 시작")))
wait_for(text_is("MediaShield · 내부 호환 엔진"),60)
wait_for(text_is("페이지 연결 완료 · Gecko"),60)
assert ".CompatibilityActivity" in adb("shell","dumpsys","activity","activities")
adb("shell","settings","put","system","user_rotation","1")
time.sleep(3)
wait_for(text_is("MediaShield · 내부 호환 엔진"))
adb("shell","settings","put","system","user_rotation","0")
click(wait_for(text_is("설정·진단")))
wait_for(text_is("연결 결과 복사"))
click(wait_for(text_is("닫기")))
click(wait_for(text_is("기본 화면")))
wait_for(desc_is("메뉴"))
print("PASS: internal Gecko engine HTTPS load, rotation, diagnostics and return to WebView")
logs=adb("logcat","-d","-s","AndroidRuntime:E")
assert "FATAL EXCEPTION" not in logs, logs
assert adb("shell","pidof",PKG).strip()
print("PASS: launch, menu routes, download input survives refresh/reopen/rotation, diagnostics, no crash")
