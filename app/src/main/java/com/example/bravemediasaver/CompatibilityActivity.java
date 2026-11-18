package com.example.bravemediasaver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.List;
import org.mozilla.geckoview.*;

/** A separate in-app engine, never a Custom Tab or external browser handoff. */
public final class CompatibilityActivity extends Activity {
    public static final String EXTRA_URL = "compatibility_url";
    private static GeckoRuntime runtime;
    private GeckoSession session;
    private MediaSession mediaSession;
    private GeckoView view;
    private LinearLayout root;
    private EditText address;
    private TextView state;
    private ProgressBar progress;
    private String current = "", error = "없음";
    private boolean canBack, fullscreen, loadOk, lock = true;
    private int blocked;
    private boolean crashed;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(Color.WHITE); t.setPadding(dp(8), dp(5), dp(8), dp(5)); return t;
    }
    private void button(LinearLayout row, String title, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setTextSize(12);
        b.setAllCaps(false); b.setContentDescription(title);
        row.addView(b, new LinearLayout.LayoutParams(0, dp(50), 1));
        b.setOnClickListener(v -> action.run());
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        current = saved == null ? getIntent().getStringExtra(EXTRA_URL) : saved.getString("url");
        if (CompatibilityPolicy.host(current).isEmpty()) { finish(); return; }
        if (saved != null) lock = saved.getBoolean("lock", true);
        root = new LinearLayout(this); root.setOrientation(1); root.setBackgroundColor(0xff131822);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom)); return insets;
        });
        root.addView(label("MediaShield · 내부 호환 엔진", 17));
        address = new EditText(this); address.setSingleLine(true); address.setTextColor(Color.WHITE);
        address.setText(current); address.setTextSize(14); address.setSelectAllOnFocus(true);
        address.setContentDescription("호환 엔진 주소");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v,a,e) -> {
            String url = address.getText().toString().trim();
            if (!url.contains(":")) url = "https://" + url;
            if (CompatibilityPolicy.host(url).isEmpty()) {
                Toast.makeText(this,"HTTPS 사이트 주소를 입력해 주세요",Toast.LENGTH_SHORT).show(); return true;
            }
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
            address.clearFocus(); session.loadUri(url); return true;
        });
        root.addView(address, new LinearLayout.LayoutParams(-1, dp(50)));
        state = label("연결 준비 중…",12); root.addView(state);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        view = new GeckoView(this); root.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar = new LinearLayout(this);
        button(bar,"뒤로",this::goBack);
        button(bar,"새로고침",()->{
            if(crashed){session.open(runtime);crashed=false;session.loadUri(current);}
            else session.reload();
        });
        button(bar,"설정·진단",this::showTools);
        button(bar,"기본 화면",this::finish);
        root.addView(bar);
        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
                    .remoteDebuggingEnabled(false).consoleOutput(false).build();
            runtime = GeckoRuntime.create(getApplicationContext(), settings);
        }
        session = new GeckoSession(new GeckoSessionSettings.Builder().useTrackingProtection(true).build());
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public void onCanGoBack(GeckoSession s, boolean value) { canBack = value; }
            @Override public void onLocationChange(GeckoSession s,String url,
                    List<GeckoSession.PermissionDelegate.ContentPermission> permissions,Boolean gesture) {
                if (!CompatibilityPolicy.host(url).isEmpty()) current = url;
                if (!address.hasFocus()) address.setText(current);
            }
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s,LoadRequest req) {
                boolean newWindow = req.target == TARGET_WINDOW_NEW;
                if (CompatibilityPolicy.block(current,req.uri,req.hasUserGesture,req.isDirectNavigation,
                        req.isRedirect,newWindow,lock)) {
                    blocked++; state.setText("외부 앱 또는 자동 이동 차단 · " + blocked + "건");
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                if (newWindow) {
                    view.post(()->{if(!isDestroyed()) session.loadUri(req.uri);});
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }
            @Override public GeckoResult<String> onLoadError(GeckoSession s,String url,WebRequestError failure) {
                loadOk = false; error = "분류 " + failure.category + " / 코드 " + failure.code;
                progress.setVisibility(View.GONE);
                state.setText("호환 엔진 연결 실패 · 설정·진단에서 결과 복사");
                // No certificate override, no HTTP downgrade, no untrusted error page.
                return GeckoResult.fromValue("data:text/html;charset=utf-8," + android.net.Uri.encode(
                        "<!doctype html><meta name='viewport' content='width=device-width'><h2>연결하지 못했습니다</h2><p>상단 새로고침 또는 설정·진단을 이용해 주세요.</p>"));
            }
        });
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession s,String url) {
                if(CompatibilityPolicy.host(url).isEmpty())return;
                current=url;loadOk = false; error = "없음"; state.setText("호환 엔진 연결 중…");
                progress.setProgress(0);progress.setVisibility(View.VISIBLE);
            }
            @Override public void onProgressChange(GeckoSession s,int value) { progress.setProgress(value); }
            @Override public void onPageStop(GeckoSession s,boolean success) {
                loadOk = success && "없음".equals(error); progress.setVisibility(View.GONE);
                state.setText(loadOk ? "페이지 연결 완료 · Gecko" : "연결 실패 · 설정·진단에서 결과 복사");
            }
        });
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onFullScreen(GeckoSession s,boolean enabled) {
                fullscreen = enabled;
                for(int i=0;i<root.getChildCount();i++) if(root.getChildAt(i)!=view)
                    root.getChildAt(i).setVisibility(enabled?View.GONE:View.VISIBLE);
                if(!enabled)progress.setVisibility(View.GONE);
                if(enabled)getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
                else getWindow().getInsetsController().show(WindowInsets.Type.systemBars());
            }
            @Override public void onExternalResponse(GeckoSession s,WebResponse response) {
                if(response.body!=null) new Thread(()->{try{response.body.close();}catch(Exception ignored){}}).start();
                Toast.makeText(CompatibilityActivity.this,"이 호환 화면은 재생용입니다. 다운로드는 기본 화면에서 이용해 주세요.",Toast.LENGTH_LONG).show();
            }
            @Override public void onCrash(GeckoSession s) {
                crashed=true;loadOk=false;error="콘텐츠 프로세스 종료";state.setText("엔진이 종료되었습니다. 새로고침으로 다시 열어주세요.");
            }
        });
        session.setMediaSessionDelegate(new MediaSession.Delegate(){
            @Override public void onActivated(GeckoSession s,MediaSession media){mediaSession=media;}
            @Override public void onDeactivated(GeckoSession s,MediaSession media){mediaSession=null;}
        });
        session.open(runtime);view.setSession(session);session.loadUri(current);
        root.requestApplyInsets();
    }
    private void showTools() {
        String[] choices={"연결 결과 복사",lock?"다른 사이트 자동 이동 차단: 켜짐":"다른 사이트 자동 이동 차단: 꺼짐","호환 엔진 안내"};
        new AlertDialog.Builder(this).setTitle("내부 호환 엔진").setItems(choices,(d,index)->{
            if(index==0){
                String report="MediaShield 호환 엔진 진단 (v11.5)\n엔진: Gecko 160\n사이트: "+CompatibilityPolicy.host(current)
                        +"\n페이지 로드 완료: "+loadOk+"\n오류: "+error+"\n자동 이동 차단: "+blocked+"건"
                        +"\nHTTPS 전용 · 인증서 검증 유지\n영상 재생 성공은 별도 확인 필요";
                ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("MediaShield",report));
                Toast.makeText(this,"호환 엔진 결과 복사 완료",Toast.LENGTH_SHORT).show();
            }else if(index==1){lock=!lock;Toast.makeText(this,lock?"자동 이동 차단 켜짐":"자동 이동 차단 꺼짐",Toast.LENGTH_SHORT).show();}
            else new AlertDialog.Builder(this).setTitle("앱 내부의 별도 브라우저")
                    .setMessage("Firefox 계열 Gecko 엔진을 사용합니다. Brave나 다른 앱을 실행하지 않습니다. 기본 화면과 로그인·쿠키는 공유하지 않습니다. 이 화면에서는 웹 영상 재생과 기본 추적 방지를 지원하며, 기존 미디어 감지·다운로드 및 광고 필터는 연결되지 않습니다. 사이트 접속과 모든 영상의 재생을 보장하지 않습니다.\n\nGeckoView 160 · Mozilla Public License 2.0\n소스: https://hg.mozilla.org/releases/mozilla-release/rev/b1145b0710192f716be5f59f5b2dfeed650d5294\n라이선스: https://mozilla.org/MPL/2.0/")
                    .setPositiveButton("확인",null).show();
        }).setNegativeButton("닫기",null).show();
    }
    private void goBack(){if(fullscreen)session.exitFullScreen();else if(canBack)session.goBack();else finish();}
    @Override public void onBackPressed(){goBack();}
    @Override protected void onResume(){super.onResume();if(session!=null)session.setActive(true);}
    @Override protected void onPause(){if(mediaSession!=null)mediaSession.pause();if(session!=null)session.setActive(false);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("url",current);state.putBoolean("lock",lock);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){if(view!=null)view.releaseSession();if(session!=null)session.close();super.onDestroy();}
}
