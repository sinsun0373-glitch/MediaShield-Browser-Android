package com.example.bravemediasaver;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;

import java.util.*;
import java.util.regex.*;

/** Android WebView-powered browser; not a fork of Brave or Chromium. */
public class MainActivity extends Activity {
    private WebView web;
    private LinearLayout playerPane, playbackRow;
    private VideoView playerView;
    private View customVideoView;
    private WebChromeClient.CustomViewCallback videoCallback;
    private FrameLayout videoContainer;
    private LinearLayout browserChrome;
    private LinearLayout addressRow, bottomBar;
    private ProgressBar pageProgress;
    private TextView shieldButton;
    private int lastWebError=0;
    private String lastFailedUrl="";
    private String lastErrorDescription="";
    private boolean mainFrameFailed=false;
    private String lastMediaErrorHost="";
    private String lastMediaErrorDetail="";
    private int mediaErrorCount=0;
    private String originalUserAgent="";
    private boolean compatibilityMode=false;
    private volatile boolean videoCompatibilityActive=false;
    private long pageNavigationStartedAt=0;
    private int blockedPopupNavigations=0;
    private String recentNavigationChange="기록 없음";
    private String lastAutomaticNavigation="기록 없음";
    private int sameHostPathChanges=0;
    private int blockedSameHostNavigations=0;
    private String lastBlockedNavigation="기록 없음";

    private EditText urlBox;
    private LinearLayout foundList, downloadsList, root, tools, adPane, bookmarkPane;
    private TextView status;
    private AdBlocker adBlocker;
    private volatile String pageUrl="";
    private final LinkedHashSet<String> media=new LinkedHashSet<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Pattern mediaPattern=Pattern.compile("(?i)\\.(mp4|webm|mov|m4v|mp3|m4a|jpg|jpeg|png|webp|gif|m3u8|mpd)(?:[?#]|$)");
    private final Pattern sharedUrl=Pattern.compile("https?://[^\\s<>\\\"]+");
    private boolean liveScan=true;
    private boolean fastDownloads=true;
    private boolean refresh=true;
    private int scanTick=0;
    private EditText directInputBox;
    private String typedDirectUrl="";

    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(15,20,29));getWindow().setNavigationBarColor(Color.rgb(15,20,29));
        adBlocker=new AdBlocker(this);
        fastDownloads=getSharedPreferences("download_settings",MODE_PRIVATE).getBoolean("fast",true);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(19,24,34));setContentView(root);
        // Compact browser chrome: one address bar, one bottom navigation rail.
        browserChrome=new LinearLayout(this);browserChrome.setOrientation(LinearLayout.VERTICAL);
        browserChrome.setBackgroundColor(0xff17191f);root.addView(browserChrome);
        LinearLayout nav=row();addressRow=nav;nav.setPadding(dp(7),dp(7),dp(7),dp(7));browserChrome.addView(nav);
        pageProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        pageProgress.setMax(100);pageProgress.setVisibility(View.GONE);
        pageProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xfff4512c));
        browserChrome.addView(pageProgress,new LinearLayout.LayoutParams(-1,dp(2)));
        TextView shield=text("◆",20);shield.setTextColor(0xffff663d);shield.setGravity(Gravity.CENTER);
        nav.addView(shield,new LinearLayout.LayoutParams(dp(40),dp(46)));
        shieldButton=shield;
        shield.setContentDescription("개인정보 보호 및 광고 차단 설정");
        shield.setOnClickListener(v->showAdblock());
        urlBox=new EditText(this);urlBox.setSingleLine(true);urlBox.setTextColor(Color.WHITE);
        urlBox.setSelectAllOnFocus(true);urlBox.setHintTextColor(0xff9eabbf);urlBox.setHint("검색어 또는 주소 입력");
        urlBox.setTextSize(14);urlBox.setPadding(dp(12),0,dp(6),0);
        urlBox.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        urlBox.setBackground(makeRounded(0xff292e38,20));
        nav.addView(urlBox,new LinearLayout.LayoutParams(0,dp(46),1));
        urlBox.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        urlBox.setOnFocusChangeListener((v,hasFocus)->{if(hasFocus)urlBox.selectAll();});
        urlBox.setOnEditorActionListener((v,action,event)->{go();return true;});
        addNavButton(nav,"↓",this::showDownloadChooser,42);
        addNavButton(nav,"⟳",()->{showWeb();web.reload();},42);
        addNavButton(nav,"⋮",this::showBrowserMenu,42);
        tools=row();tools.setBackgroundColor(0xff17191f);
        status=text("",10);status.setVisibility(View.GONE);
        web=new WebView(this);web.setBackgroundColor(Color.WHITE);
        WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setMediaPlaybackRequiresUserGesture(true);ws.setBuiltInZoomControls(true);ws.setDisplayZoomControls(false);ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setJavaScriptCanOpenWindowsAutomatically(!adBlocker.isPopupBlockEnabled());
        ws.setSafeBrowsingEnabled(true);ws.setAllowFileAccessFromFileURLs(false);ws.setAllowUniversalAccessFromFileURLs(false);ws.setSaveFormData(false);
        ws.setSupportMultipleWindows(false);
        ws.setSupportZoom(true);
        // WebView is the rendering engine; these options do not emulate Brave\'s full Chromium stack.
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        originalUserAgent=ws.getUserAgentString();
        compatibilityMode=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("chrome_ua_compat",false);
        applyUserAgentMode();
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web,getSharedPreferences("privacy",MODE_PRIVATE).getBoolean("third_party_cookies",false));
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int progress){
                if(progress==100 && !urlBox.hasFocus() && view.getUrl()!=null)urlBox.setText(view.getUrl());
                pageProgress.setProgress(progress);
                pageProgress.setVisibility(progress>=100?View.GONE:View.VISIBLE);
            }
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(customVideoView!=null){callback.onCustomViewHidden();return;}
                customVideoView=view;videoCallback=callback;
                root.setVisibility(View.GONE);
                videoContainer=new FrameLayout(MainActivity.this);
                videoContainer.setBackgroundColor(Color.BLACK);
                videoContainer.addView(view,new FrameLayout.LayoutParams(-1,-1));
                // Always provide an exit control in fullscreen; playback buttons are
                // best-effort for HTML5 media in the current page only.
                LinearLayout videoButtons=row();
                videoButtons.setBackgroundColor(0xcc17191f);
                addButton(videoButtons,"▶ 재생",()->webPlayback("play"));
                addButton(videoButtons,"Ⅱ 일시정지",()->webPlayback("pause"));
                addButton(videoButtons,"■ 정지",()->webPlayback("stop"));
                addButton(videoButtons,"× 닫기",MainActivity.this::hideVideoFullscreen);
                FrameLayout.LayoutParams controlsLp=new FrameLayout.LayoutParams(-1,dp(53),Gravity.BOTTOM);
                videoContainer.addView(videoButtons,controlsLp);
                addContentView(videoContainer,new ViewGroup.LayoutParams(-1,-1));
                getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            @Override public void onHideCustomView(){hideVideoFullscreen();}
            @Override public boolean onCreateWindow(WebView view,boolean isDialog,boolean userGesture,android.os.Message resultMsg){return false;}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap icon){
                notePageNavigation(pageUrl,u);
                pageUrl=u;pageNavigationStartedAt=android.os.SystemClock.elapsedRealtime();mainFrameFailed=false;lastWebError=0;adBlocker.resetPageCount();media.clear();
                videoCompatibilityActive=isVideoCompatibilityEnabled(u);
                boolean globalCookies=getSharedPreferences("privacy",MODE_PRIVATE).getBoolean("third_party_cookies",false);
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(v,globalCookies || videoCompatibilityActive);
                if(!urlBox.hasFocus())urlBox.setText(u);
            }
            @Override public void onPageFinished(WebView v,String u){
                pageUrl=u;urlBox.setText(u);
                if(mainFrameFailed)return;
                scanDomMedia();
                v.evaluateJavascript("(function(){document.querySelectorAll('video').forEach(function(x){x.controls=true;});})();",null);
                if(adBlocker.isEnabled()&&adBlocker.isCosmeticEnabled()&&!adBlocker.isSiteAllowed(u)){
                    v.evaluateJavascript(AdBlocker.COSMETIC_JS,null);
                    // Only press an already-available skip button on YouTube; no stream URL rewriting.
                    if(u!=null && (u.startsWith("https://www.youtube.com/") || u.startsWith("https://m.youtube.com/")))
                        v.evaluateJavascript(AdBlocker.YOUTUBE_SKIPPABLE_AD_JS,null);
                }
                status.setText("미디어 " + media.size() + "개 · 광고/추적 요청 차단 " + adBlocker.getPageCount() + "건");
                shieldButton.setText(adBlocker.isEnabled()&&!adBlocker.isSiteAllowed(u)?"◆":"◇");
                shieldButton.setTextColor(adBlocker.isEnabled()&&!adBlocker.isSiteAllowed(u)?0xffff663d:0xff9fa7b2);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
                String u=request.getUrl().toString();
                if(!videoCompatibilityActive && adBlocker.shouldBlock(u,pageUrl,request.isForMainFrame()))return adBlocker.blankResponse();
                if(liveScan&&looksLikeMedia(u))runOnUiThread(()->addMedia(u));
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
                String target=request.getUrl().toString();
                if(!target.startsWith("http://") && !target.startsWith("https://"))return true;
                // Capture only hosts and the first path segment, never full URLs or query tokens.
                if(request.isForMainFrame() && !request.hasGesture())
                    lastAutomaticNavigation=safeNavigationLabel(pageUrl)+" → "+safeNavigationLabel(target);
                // An opt-in navigation lock keeps the current page from being replaced by
                // gesture-free scripted navigations, including same-site path changes.
                // Do not block ordinary user taps or early server redirects.
                if(request.isForMainFrame() && !request.hasGesture()
                        && isNavigationLockEnabled(pageUrl)
                        && pageNavigationStartedAt>0
                        && android.os.SystemClock.elapsedRealtime()-pageNavigationStartedAt>1200
                        && !siteHostname(target).isEmpty()
                        && !siteHostname(pageUrl).isEmpty()
                        && !target.equals(pageUrl)){
                    lastBlockedNavigation=safeNavigationLabel(pageUrl)+" → "+safeNavigationLabel(target);
                    if(siteHostname(target).equals(siteHostname(pageUrl)))blockedSameHostNavigations++;
                    else blockedPopupNavigations++;
                    runOnUiThread(()->Toast.makeText(MainActivity.this,
                        "자동 페이지 이동 차단 · 주소 변경 잠금",Toast.LENGTH_SHORT).show());
                    return true;
                }
                // Pop-under scripts sometimes redirect the current tab after a video starts.
                // Only intercept automatic cross-site *main-frame* navigations after the page settled.
                if(request.isForMainFrame() && !request.hasGesture() && adBlocker.isPopupBlockEnabled()
                        && !videoCompatibilityActive && pageNavigationStartedAt>0
                        && android.os.SystemClock.elapsedRealtime()-pageNavigationStartedAt>2500
                        && !siteHostname(target).isEmpty()
                        && !siteHostname(pageUrl).isEmpty()
                        && !siteHostname(target).equals(siteHostname(pageUrl))){
                    blockedPopupNavigations++;
                    runOnUiThread(()->Toast.makeText(MainActivity.this,
                        "자동 광고 페이지 이동 차단 · 메뉴에서 팝업 차단을 끌 수 있습니다",Toast.LENGTH_SHORT).show());
                    return true;
                }
                return false;
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError error){
                if(!req.isForMainFrame()){
                    String failed=req.getUrl().toString();
                    if(looksLikeMedia(failed) || failed.contains("videoplayback") || failed.contains("googlevideo")){
                        String host=req.getUrl().getHost();
                        lastMediaErrorHost=host==null?"알 수 없음":host;
                        lastMediaErrorDetail=error.getErrorCode()+" · "+error.getDescription();
                        mediaErrorCount++;
                    }
                    return;
                }
                mainFrameFailed=true;
                lastWebError=error.getErrorCode();
                lastFailedUrl=req.getUrl().toString();
                lastErrorDescription=String.valueOf(error.getDescription());
                runOnUiThread(()->showConnectionFailure(lastFailedUrl,lastWebError,lastErrorDescription));
            }
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();}
        });
        web.setDownloadListener((u,ua,disposition,mime,size)->{
            if(!TaskStore.validDownloadUrl(u)){
                Toast.makeText(this,"HTTPS 주소만 다운로드할 수 있습니다",Toast.LENGTH_LONG).show();
                return;
            }
            if(looksLikeMedia(u))addMedia(u);
            // A page cannot silently enqueue files; downloading requires a user tap.
            new AlertDialog.Builder(this).setTitle("파일 다운로드 확인")
                .setMessage("파일: "+URLUtil.guessFileName(u,disposition,mime)+"\\n사이트에서 다운로드를 요청했습니다. 저장하시겠습니까?")
                .setPositiveButton("다운로드",(d,w)->{
                    if(mime!=null && mime.toLowerCase(Locale.ROOT).contains("mpegurl"))selectHlsQuality(u);
                    else enqueue(u,mime,URLUtil.guessFileName(u,disposition,mime),ua);
                }).setNegativeButton("취소",null).show();
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        foundList=new LinearLayout(this);foundList.setOrientation(LinearLayout.VERTICAL);
        downloadsList=new LinearLayout(this);downloadsList.setOrientation(LinearLayout.VERTICAL);
        adPane=new LinearLayout(this);adPane.setOrientation(LinearLayout.VERTICAL);
        bookmarkPane=new LinearLayout(this);bookmarkPane.setOrientation(LinearLayout.VERTICAL);
        playerPane=new LinearLayout(this);playerPane.setOrientation(1);playerPane.setBackgroundColor(Color.BLACK);
        root.addView(foundList,new LinearLayout.LayoutParams(-1,0,1));root.addView(downloadsList,new LinearLayout.LayoutParams(-1,0,1));root.addView(adPane,new LinearLayout.LayoutParams(-1,0,1));root.addView(bookmarkPane,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(playerPane,new LinearLayout.LayoutParams(-1,0,1));
        playbackRow=row();playbackRow.setBackgroundColor(0xff17191f);
        addButton(playbackRow,"▶ 재생",()->webPlayback("play"));
        addButton(playbackRow,"Ⅱ 일시정지",()->webPlayback("pause"));
        addButton(playbackRow,"■ 정지",()->webPlayback("stop"));
        root.addView(playbackRow,new LinearLayout.LayoutParams(-1,dp(52)));
        playbackRow.setVisibility(View.GONE);
        LinearLayout bottom=row();bottomBar=bottom;bottom.setBackgroundColor(0xff17191f);
        bottom.setPadding(dp(5),dp(3),dp(5),dp(5));root.addView(bottom);
        addBottomButton(bottom,"‹",()->{if(web.canGoBack()){showWeb();web.goBack();}});
        addBottomButton(bottom,"›",()->{if(web.canGoForward()){showWeb();web.goForward();}});
        addBottomButton(bottom,"⌂",()->{showWeb();web.loadUrl("https://www.google.com");});
        addBottomButton(bottom,"▶",this::showMedia);
        addBottomButton(bottom,"⏯",this::togglePlaybackRow);

        addBottomButton(bottom,"◆",this::showAdblock);
        setAddressBarBottom(getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("bottom_address",false));
        showWeb(); // Keep settings panes hidden until opened.
        handler.postDelayed(this::tick,1000);
        if(saved!=null){web.restoreState(saved);}else{web.loadUrl("https://www.google.com");}
        handleShare(getIntent());
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},92);
    }
    private android.graphics.drawable.GradientDrawable makeRounded(int color,int radiusDp){
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(radiusDp));return bg;
    }
    private void addBottomButton(LinearLayout bar,String label,Runnable click){
        TextView btn=text(label,23);btn.setTextColor(0xffe1e5ed);btn.setGravity(Gravity.CENTER);
        btn.setBackground(makeRounded(0xff222630,16));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);
        params.setMargins(dp(2),0,dp(2),0);
        bar.addView(btn,params);
        btn.setOnClickListener(v->click.run());
    }
    private void hideVideoFullscreen(){
        if(customVideoView==null)return;
        if(videoContainer!=null){videoContainer.removeAllViews();
            ((ViewGroup)videoContainer.getParent()).removeView(videoContainer);videoContainer=null;}
        customVideoView=null;root.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if(videoCallback!=null){videoCallback.onCustomViewHidden();videoCallback=null;}
    }
    private void openExternalBrowser(){
        String url=web.getUrl();
        if(TaskStore.validUrl(url))openExternalUrl(url);
    }
    private void showBrowserMenu(){
        String[] actions={"영상 연결 진단", "DNS·HTTPS 직접 검사", "이 사이트 자동 이동 잠금 켜기/끄기", "이 사이트 영상 호환 모드", "주소창 위/아래 이동", "Chrome 호환 User-Agent 켜기/끄기", "현재 사이트 즐겨찾기", "즐겨찾기 목록", "광고 차단 설정", "이 사이트 광고 차단 잠시 끄기", "서드파티 쿠키 허용 설정", "Brave에서 현재 사이트 열기", "새로고침", "마지막 연결 오류 정보", "이 브라우저에 관하여", "영상 실행 방식 및 내부 플레이어"};
        new AlertDialog.Builder(this).setTitle("MediaShield · 메뉴")
        .setItems(actions,(d,which)->{
            switch(which){
                case 0:showMediaConnectionDiagnostics();break;
                case 1:runNetworkProbe();break;
                case 2:toggleNavigationLockForCurrentSite();break;
                case 3:toggleVideoCompatibilityForCurrentSite();break;
                case 4:
                    boolean bottom=!getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("bottom_address",false);
                    getSharedPreferences("appearance",MODE_PRIVATE).edit().putBoolean("bottom_address",bottom).apply();
                    setAddressBarBottom(bottom);break;
                case 5:toggleCompatibilityMode();break;
                case 6:bookmarkCurrent();break;
                case 7:showBookmarks();break;
                case 8:showAdblock();break;
                case 9:
                    String url=web.getUrl();if(!TaskStore.validUrl(url))break;
                    adBlocker.setSiteAllowed(url,!adBlocker.isSiteAllowed(url));
                    Toast.makeText(this,adBlocker.isSiteAllowed(url)?"이 사이트 광고 차단 제외":"광고 차단 적용",Toast.LENGTH_SHORT).show();
                    web.reload();break;
                case 10:showThirdPartyCookiesSetting();break;
                case 11:openExternalBrowser();break;
                case 12:web.reload();break;
                case 13:showConnectionFailure(lastFailedUrl,lastWebError,lastErrorDescription);break;
                case 14:new AlertDialog.Builder(this).setTitle("MediaShield Browser · 호환성 테스트")
                    .setMessage("Android WebView 기반 브라우저이며 Brave의 Chromium/Shield 엔진과 다릅니다. Chrome User-Agent 호환 모드는 웹사이트 식별 정보만 변경하며 서버 연결 문제 해결이나 영상 광고 제거를 보장하지 않습니다.")
                    .setPositiveButton("확인",null).show();break;
                case 15:showMediaEngineSetting();break;
            }
        }).show();
    }
    private void navigateTo(String url){
        if(!TaskStore.validUrl(url))return;
        showWeb();web.loadUrl(url);
    }
    private void showMediaEngineSetting(){
        new AlertDialog.Builder(this).setTitle("영상 실행 · MediaShield 내부")
            .setMessage("모든 사이트가 MediaShield 내부에서 열립니다. 다른 브라우저로 자동 이동하지 않습니다. "+
                "다만 WebView는 Brave 광고 차단 엔진이 아니며 일부 사이트는 연결이나 재생이 제한될 수 있습니다. "+
                "감지된 직접 영상 주소는 앱내 플레이어로 재생할 수 있습니다.")
            .setPositiveButton("영상 찾기",(d,w)->showMedia())
            .setNeutralButton("Brave 수동 열기",(d,w)->openExternalBrowser())
            .setNegativeButton("닫기",null).show();
    }
    private String siteHostname(String url){
        if(url==null)return "";
        try {
            Uri uri=Uri.parse(url);
            String host=uri.getHost();
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && host!=null?host.toLowerCase(Locale.ROOT):"";
        }catch(Exception ignored){return "";}
    }
    private boolean isNavigationLockEnabled(String url){
        String host=siteHostname(url);
        if(host.isEmpty())return false;
        android.content.SharedPreferences prefs=getSharedPreferences("nav_lock",MODE_PRIVATE);
        Set<String> disabled=prefs.getStringSet("disabled",Collections.emptySet());
        Set<String> enabled=prefs.getStringSet("enabled",Collections.emptySet());
        // For the reported site, start with protection enabled. It is reversible from the menu.
        if(host.equals("njavtv.com"))return !disabled.contains(host);
        return enabled.contains(host);
    }
    private void toggleNavigationLockForCurrentSite(){
        String url=web==null?null:web.getUrl();
        String host=siteHostname(url);
        if(host.isEmpty()){
            Toast.makeText(this,"먼저 웹사이트를 열어주세요",Toast.LENGTH_SHORT).show();
            return;
        }
        boolean active=isNavigationLockEnabled(url);
        new AlertDialog.Builder(this).setTitle("사이트별 자동 이동 잠금")
            .setMessage("대상: "+host+"\n현재 상태: "+(active?"켜짐":"꺼짐")+
                "\n\n영상 재생 중 사용자 터치 없이 다른 페이지로 자동 이동하는 것을 막습니다. "+
                "같은 사이트의 경로 이동도 막을 수 있으며, 사이트 기능이 정상 동작하지 않으면 잠금을 끄세요. "+
                "서버가 직접 연결을 끊는 경우에는 도움이 되지 않습니다.")
            .setPositiveButton(active?"잠금 끄기":"잠금 켜기",(d,w)->{
                android.content.SharedPreferences prefs=getSharedPreferences("nav_lock",MODE_PRIVATE);
                Set<String> disabled=new HashSet<>(prefs.getStringSet("disabled",Collections.emptySet()));
                Set<String> enabled=new HashSet<>(prefs.getStringSet("enabled",Collections.emptySet()));
                if(active){disabled.add(host);enabled.remove(host);}
                else {disabled.remove(host);enabled.add(host);}
                prefs.edit().putStringSet("disabled",disabled).putStringSet("enabled",enabled).apply();
                Toast.makeText(this,active?"자동 이동 잠금 꺼짐":"자동 이동 잠금 켜짐",Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("취소",null).show();
    }
    private boolean isVideoCompatibilityEnabled(String url){
        String host=siteHostname(url);
        if(host.isEmpty())return false;
        return getSharedPreferences("video_compat",MODE_PRIVATE)
            .getStringSet("hosts",Collections.emptySet()).contains(host);
    }
    private void toggleVideoCompatibilityForCurrentSite(){
        String url=web==null?null:web.getUrl();
        String host=siteHostname(url);
        if(host.isEmpty()){
            Toast.makeText(this,"먼저 사이트를 열어주세요",Toast.LENGTH_SHORT).show();return;
        }
        boolean enabled=isVideoCompatibilityEnabled(url);
        String explanation=(enabled?"현재 호환 모드가 켜져 있습니다. 끄시겠습니까?":
            "이 사이트에서만 광고·추적 요청 차단을 일시 제외하고 영상 임베드용 서드파티 쿠키를 허용합니다. "+
            "영상 재생에 도움될 수 있지만 이 사이트에서 추적 위험이 증가합니다. "+
            "다른 사이트에는 적용되지 않으며 연결 오류 해결을 보장하지 않습니다.")+
            "\n\n대상: "+host;
        new AlertDialog.Builder(this).setTitle("사이트별 영상 호환 모드")
            .setMessage(explanation)
            .setPositiveButton(enabled?"모드 끄기":"모드 켜고 재시도",(d,w)->{
                android.content.SharedPreferences prefs=getSharedPreferences("video_compat",MODE_PRIVATE);
                Set<String> hosts=new HashSet<>(prefs.getStringSet("hosts",Collections.emptySet()));
                if(enabled)hosts.remove(host);else hosts.add(host);
                prefs.edit().putStringSet("hosts",hosts).apply();
                videoCompatibilityActive=!enabled;
                boolean baseCookies=getSharedPreferences("privacy",MODE_PRIVATE).getBoolean("third_party_cookies",false);
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web,baseCookies || !enabled);
                showWeb();web.reload();
            })
            .setNeutralButton("Brave에서 재생",(d,w)->openExternalUrl(url))
            .setNegativeButton("취소",null).show();
    }
    private String safeNavigationLabel(String url){
        if(url==null || url.isEmpty())return "없음";
        try{
            Uri parsed=Uri.parse(url);
            String host=parsed.getHost();
            if(host==null)return "알 수 없음";
            java.util.List<String> paths=parsed.getPathSegments();
            String first=paths==null||paths.isEmpty()?"":paths.get(0);
            // Do not copy a media ID, query token, or personal URL into diagnostics.
            if(first.length()>24)first=first.substring(0,24)+"…";
            return host+(first.isEmpty()?"/":"/"+first+"/…");
        }catch(Exception ignored){return "알 수 없음";}
    }
    private void notePageNavigation(String previous,String current){
        if(previous==null || previous.isEmpty() || current==null || current.equals(previous))return;
        String fromHost=siteHostname(previous),toHost=siteHostname(current);
        if(fromHost.isEmpty()||toHost.isEmpty())return;
        recentNavigationChange=safeNavigationLabel(previous)+" → "+safeNavigationLabel(current);
        if(fromHost.equals(toHost)){
            try{
                String oldPath=Uri.parse(previous).getPath(),newPath=Uri.parse(current).getPath();
                if(oldPath!=null && newPath!=null && !oldPath.equals(newPath))sameHostPathChanges++;
            }catch(Exception ignored){}
        }
    }
    private String buildDiagnosticsReport(){
        String page=web==null?"":web.getUrl();
        String pageHost=siteHostname(page);
        android.content.pm.PackageInfo pkg=android.webkit.WebView.getCurrentWebViewPackage();
        String engineVersion=pkg==null?"알 수 없음":pkg.versionName;
        String lastFailedHost=siteHostname(lastFailedUrl);
        return "MediaShield 연결 진단 (v11.3)"+
            "\n영상 사이트 실행 엔진: MediaShield WebView"+
            "\nBrave 패키지 확인: "+(installedBravePackage()==null?"설치 여부 미확인":installedBravePackage())+
            "\n현재 페이지: "+(pageHost.isEmpty()?"없음":pageHost)+
            "\nWebView 버전: "+engineVersion+
            "\nChrome 식별 모드: "+(compatibilityMode?"켜짐":"꺼짐")+
            "\n서드파티 쿠키: "+(getSharedPreferences("privacy",MODE_PRIVATE).getBoolean("third_party_cookies",false)?"허용":"차단")+
            "\n광고 차단: "+(adBlocker.isEnabled()?"켜짐":"꺼짐")+
            "\n영상 호환 모드: "+(isVideoCompatibilityEnabled(page)?"켜짐":"꺼짐")+
            "\n자동 이동 잠금: "+(isNavigationLockEnabled(page)?"켜짐":"꺼짐")+
            "\n서로 다른 호스트 자동 이동 차단: "+blockedPopupNavigations+"건"+
            "\n동일 호스트 자동 이동 차단: "+blockedSameHostNavigations+"건"+
            "\n최근 차단된 자동 이동: "+lastBlockedNavigation+
            "\n동일 호스트 경로 변경: "+sameHostPathChanges+"건"+
            "\n최근 페이지 이동: "+recentNavigationChange+
            "\n최근 자동 이동 요청: "+lastAutomaticNavigation+
            "\n메인 페이지 오류 코드: "+lastWebError+
            "\n오류 발생 호스트: "+(lastFailedHost.isEmpty()?"없음":lastFailedHost)+
            "\n메인 페이지 오류 설명: "+(lastErrorDescription.isEmpty()?"없음":lastErrorDescription)+
            "\n미디어 네트워크 오류: "+mediaErrorCount+"건"+
            "\n최근 미디어 요청 호스트: "+(lastMediaErrorHost.isEmpty()?"기록 없음":lastMediaErrorHost)+
            "\n최근 미디어 오류: "+(lastMediaErrorDetail.isEmpty()?"기록 없음":lastMediaErrorDetail)+
            "\n(전체 URL, URL 쿼리, 쿠키 및 비밀번호는 포함하지 않음)";
    }
    private void copyDiagnosticsReport(){
        android.content.ClipboardManager clip=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clip.setPrimaryClip(ClipData.newPlainText("MediaShield Connection Diagnostics",buildDiagnosticsReport()));
        Toast.makeText(this,"진단 정보가 복사되었습니다",Toast.LENGTH_SHORT).show();
    }
    private void showMediaConnectionDiagnostics(){
        new AlertDialog.Builder(this).setTitle("영상 연결 진단")
            .setMessage(buildDiagnosticsReport())
            .setPositiveButton("진단 정보 복사",(d,w)->copyDiagnosticsReport())
            .setNeutralButton("네트워크 검사",(d,w)->runNetworkProbe())
            .setNegativeButton("닫기",null).show();
    }
    private void runNetworkProbe(){
        Toast.makeText(this,"운영체제 DNS 및 HTTPS 연결 검사 중…",Toast.LENGTH_LONG).show();
        new Thread(()->{
            StringBuilder result=new StringBuilder("MediaShield 네트워크 직접 검사 (v11.3)\n");
            result.append("이 검사는 WebView가 아닌 Android 네트워크 API를 사용합니다.\n")
                  .append("HTTP 403·404도 연결에 성공했다는 의미일 수 있습니다.\n\n");
            for(String host:new String[]{"njavtv.com","www.youtube.com"}){
                result.append(host).append("\n");
                try{
                    InetAddress[] addresses=InetAddress.getAllByName(host);
                    result.append("  DNS: 성공 (").append(addresses.length).append("개 주소)\n");
                }catch(Exception e){
                    result.append("  DNS: 실패 · ").append(e.getClass().getSimpleName()).append("\n");
                }
                HttpsURLConnection connection=null;
                try{
                    URL url=new URL("https://"+host+"/");
                    connection=(HttpsURLConnection)url.openConnection();
                    connection.setRequestMethod("HEAD");
                    connection.setConnectTimeout(6000);
                    connection.setReadTimeout(6000);
                    connection.setInstanceFollowRedirects(false);
                    int code=connection.getResponseCode();
                    result.append("  HTTPS: 응답 ").append(code).append("\n");
                }catch(Exception e){
                    result.append("  HTTPS: 실패 · ").append(e.getClass().getSimpleName())
                          .append(" · ").append(e.getMessage()==null?"설명 없음":e.getMessage()).append("\n");
                }finally{if(connection!=null)connection.disconnect();}
                result.append("\n");
            }
            String report=result.toString();
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("직접 연결 검사 결과")
                .setMessage(report)
                .setPositiveButton("결과 복사",(d,w)->{
                    android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("MediaShield Network Test",report));
                    Toast.makeText(this,"검사 결과 복사 완료",Toast.LENGTH_SHORT).show();
                }).setNegativeButton("닫기",null).show());
        },"MediaShieldNetworkProbe").start();
    }
    /** The UA toggle only changes identification: it is not a network bypass or another browser engine. */
    private void applyUserAgentMode(){
        if(web==null || originalUserAgent.isEmpty())return;
        if(!compatibilityMode){web.getSettings().setUserAgentString(originalUserAgent);return;}
        String ua=originalUserAgent.replace("; wv","").replace("Version/4.0 ","");
        web.getSettings().setUserAgentString(ua);
    }
    private void toggleCompatibilityMode(){
        compatibilityMode=!compatibilityMode;
        getSharedPreferences("appearance",MODE_PRIVATE).edit().putBoolean("chrome_ua_compat",compatibilityMode).apply();
        applyUserAgentMode();
        Toast.makeText(this,compatibilityMode?"Chrome 식별 호환 모드 켜짐 (브라우저 엔진은 동일)":"기본 WebView 식별 모드",Toast.LENGTH_LONG).show();
        if(web!=null && web.getUrl()!=null)web.reload();
    }
    private void setAddressBarBottom(boolean bottom){
        if(addressRow==null || browserChrome==null || root==null)return;
        ViewGroup parent=(ViewGroup)addressRow.getParent();
        if(parent!=null)parent.removeView(addressRow);
        if(bottom)root.addView(addressRow,new LinearLayout.LayoutParams(-1,dp(60)));
        else browserChrome.addView(addressRow,0,new LinearLayout.LayoutParams(-1,dp(60)));
        addressRow.setContentDescription(bottom?"하단 주소창":"상단 주소창");
    }
    private void hideKeyboard(){
        try{((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(urlBox.getWindowToken(),0);urlBox.clearFocus();}
        catch(Exception ignored){}
    }
    private void showThirdPartyCookiesSetting(){
        boolean current=getSharedPreferences("privacy",MODE_PRIVATE).getBoolean("third_party_cookies",false);
        new AlertDialog.Builder(this).setTitle("서드파티 쿠키")
            .setMessage("일부 로그인 및 영상 임베드에 필요할 수 있으나 사이트 간 추적 위험이 증가합니다.\n\n현재 상태: "+(current?"허용":"차단"))
            .setPositiveButton(current?"차단":"허용",(d,w)->{
                boolean enabled=!current;
                getSharedPreferences("privacy",MODE_PRIVATE).edit().putBoolean("third_party_cookies",enabled).apply();
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web,enabled || isVideoCompatibilityEnabled(web.getUrl()));
                web.reload();
            })
            .setNegativeButton("취소",null).show();
    }
    private void showConnectionFailure(String failedUrl,int code,String detail){
        if(failedUrl==null || failedUrl.isEmpty()){
            new AlertDialog.Builder(this).setTitle("연결 오류 기록 없음")
                .setMessage("아직 기록된 메인 페이지 연결 오류가 없습니다.")
                .setPositiveButton("확인",null).show();return;
        }
        Uri uri=Uri.parse(failedUrl);
        String host=uri.getHost()==null?failedUrl:uri.getHost();
        String explanation="연결 오류 " + code + " · " + detail + "\n\n사이트: " + host +
            "\n\n연결 재설정은 서버, 통신망, DNS 또는 사이트 정책에서 발생할 수 있습니다. 광고 차단을 꺼도 해결되지 않을 수 있습니다. SSL 인증서 오류는 우회하지 않습니다.";
        new AlertDialog.Builder(this).setTitle("사이트 연결 실패")
            .setMessage(explanation+"\n\n오류 원인을 확인하려면 아래 '진단 정보 복사'를 누르세요.")
            .setPositiveButton("진단 정보 복사",(d,w)->copyDiagnosticsReport())
            .setNeutralButton("DNS·HTTPS 검사",(d,w)->runNetworkProbe())
            .setNegativeButton("Brave에서 열기",(d,w)->openExternalUrl(failedUrl)).show();
    }
    private String installedBravePackage(){
        for(String candidate:new String[]{"com.brave.browser","com.brave.browser_beta","com.brave.browser_nightly"}){
            try{getPackageManager().getPackageInfo(candidate,0);return candidate;}
            catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        }
        return null;
    }
    private void showBraveUnavailable(String url,String message){
        new AlertDialog.Builder(this).setTitle("Brave 실행 불가")
            .setMessage(message+"\n사이트는 MediaShield 내부에서 계속 사용할 수 있습니다.")
            .setPositiveButton("내부에서 열기",(d,w)->navigateTo(url))
            .setNegativeButton("닫기",null).show();
    }
    private void openExternalUrl(String url){
        if(!TaskStore.validUrl(url))return;
        String brave=installedBravePackage();
        if(brave==null){
            showBraveUnavailable(url,"설치된 Brave 앱의 패키지를 확인하지 못했습니다.");
            return;
        }
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        // A strict package pin prevents accidental system/default-app deep-link handoff.
        intent.setPackage(brave);
        try{
            startActivity(intent);
        }catch(android.content.ActivityNotFoundException | SecurityException error){
            showBraveUnavailable(url,"Brave 앱에서 주소를 열 수 없습니다: "+error.getClass().getSimpleName());
        }
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String str,int size){TextView v=new TextView(this);v.setText(str);v.setTextSize(size);v.setTextColor(Color.WHITE);return v;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(0);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private void addButton(LinearLayout parent,String name,Runnable click){
        Button b=new Button(this);b.setText(name);b.setTextSize(11);b.setAllCaps(false);b.setOnClickListener(v->click.run());
        if(parent.getOrientation()==LinearLayout.VERTICAL)
            parent.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));
        else parent.addView(b,new LinearLayout.LayoutParams(0,dp(47),1));
    }
    private void addNavButton(LinearLayout r,String label,Runnable click,int w){
        TextView b=text(label,23);b.setGravity(Gravity.CENTER);
        b.setTextColor(0xffdce2ee);b.setBackground(makeRounded(0xff242932,14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(w),dp(46));
        lp.setMargins(dp(3),0,dp(2),0);r.addView(b,lp);
        b.setOnClickListener(v->click.run());
    }
    private void bookmarkCurrent(){
        String url=web.getUrl();if(!TaskStore.validUrl(url))return;
        android.content.SharedPreferences prefs=getSharedPreferences("bookmarks",MODE_PRIVATE);
        Set<String> links=new LinkedHashSet<>(prefs.getStringSet("urls",Collections.emptySet()));
        if(links.contains(url)){links.remove(url);Toast.makeText(this,"즐겨찾기 삭제",Toast.LENGTH_SHORT).show();}
        else{links.add(url);Toast.makeText(this,"즐겨찾기 저장",Toast.LENGTH_SHORT).show();}
        prefs.edit().putStringSet("urls",links).apply();
    }
    private void showBookmarks(){
        clearPanels();bookmarkPane.setVisibility(View.VISIBLE);bookmarkPane.removeAllViews();
        TextView title=text("  즐겨찾기 · 주소를 눌러 열기",16);title.setPadding(dp(10),dp(15),0,dp(10));bookmarkPane.addView(title);
        addButton(bookmarkPane,"브라우저로 돌아가기",this::showWeb);
        ScrollView sv=new ScrollView(this);bookmarkPane.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=new LinearLayout(this);list.setOrientation(1);sv.addView(list);
        Set<String> links=getSharedPreferences("bookmarks",MODE_PRIVATE).getStringSet("urls",Collections.emptySet());
        for(String u:links){addButton(list,u,()->navigateTo(u));}
        if(links.isEmpty())list.addView(text("  저장된 주소가 없습니다. 상단 ★ 버튼으로 추가할 수 있습니다.",13));
    }
    private void clearPanels(){
        hideKeyboard();web.setVisibility(View.GONE);foundList.setVisibility(View.GONE);
        downloadsList.setVisibility(View.GONE);adPane.setVisibility(View.GONE);
        bookmarkPane.setVisibility(View.GONE);playerPane.setVisibility(View.GONE);
        playbackRow.setVisibility(View.GONE);
        if(playerView!=null&&playerView.isPlaying())playerView.pause();
    }
    private void showWeb(){clearPanels();web.setVisibility(View.VISIBLE);}
    private void go(){String u=urlBox.getText().toString().trim();if(u.isEmpty())return;hideKeyboard();if(!u.startsWith("http://")&&!u.startsWith("https://")){if(u.contains(".")&&!u.contains(" "))u="https://"+u;else u="https://www.google.com/search?q="+Uri.encode(u);}navigateTo(u);}
    private boolean looksLikeMedia(String u){
        try {
            Uri uri=Uri.parse(u);
            String path=uri.getPath();
            if(path!=null && mediaPattern.matcher(path).find())return true;
            if(isHls(u))return true;
            String type=uri.getQueryParameter("format");
            return type!=null && type.matches("(?i)mp4|webm|m4v|mov");
        } catch(Exception e){return false;}
    }
    private void addMedia(String u){if(!TaskStore.validUrl(u))return;if(media.size()>=250)return;if(media.add(u)){status.setText("미디어 " + media.size() + "개 감지됨");if(foundList.getVisibility()==View.VISIBLE)populateMedia();}}
    /** Collect direct <video>/<source>/<audio>/image URLs after navigation and in dynamic pages.
     * Returns only browser-accessible HTTP(S) URLs; blob: and DRM media are intentionally omitted.
     */
    private void scanDomMedia(){
        if(web==null || !liveScan)return;
        String js="(function(){var out=[];function add(v){if(v&&/^https?:\\/\\//i.test(v)&&out.indexOf(v)<0)out.push(v);}"+
            "document.querySelectorAll('video,video source,audio,audio source').forEach(function(e){add(e.currentSrc);add(e.src);});"+
            "Array.prototype.slice.call(document.querySelectorAll('img'),0,25).forEach(function(e){add(e.currentSrc||e.src);});"+
            "Array.prototype.slice.call(document.querySelectorAll('a[href]'),0,80).forEach(function(e){if(/\\.(mp4|webm|m4v|mov|m3u8|mp3|jpg|png|webp)(\\?|#|$)/i.test(e.href))add(e.href);});"+
            "return out.slice(0,120);})()";
        try{web.evaluateJavascript(js,json->{
            try{JSONArray arr=new JSONArray(json);for(int i=0;i<arr.length();i++){
                String url=arr.optString(i);
                if(TaskStore.validUrl(url)&&(looksLikeMedia(url)||i<30))addMedia(url);
            }}catch(Exception ignored){}
        });}catch(Exception ignored){}
    }
    private boolean isHls(String u){return u!=null && (u.matches("(?i).*\\.m3u8(?:[?#].*)?$") || u.matches("(?i).*[?&](?:format|ext|type)=m3u8(?:&.*)?$"));}
    private boolean isDash(String u){return u!=null&&u.matches("(?i).*\\.mpd(?:[?#].*)?$");}
    private boolean isVideoFileCandidate(String url){
        if(url==null)return false;
        try {
            Uri u=Uri.parse(url);
            String path=u.getPath();
            if(path!=null && path.matches("(?i).*\\.(mp4|webm|m4v|mov|m3u8|mpd|ts|mp3|m4a)(?:$)"))return true;
            return isHls(url) || isDash(url) || url.contains("videoplayback");
        }catch(Exception ignored){return false;}
    }
    private void showDownloadChooser(){
        // The toolbar button must begin a download flow, not only show completed tasks.
        scanDomMedia();
        ArrayList<String> available=new ArrayList<>();
        for(String url:media)if(isVideoFileCandidate(url))available.add(url);
        if(available.isEmpty()){
            new AlertDialog.Builder(this).setTitle("영상 다운로드")
                .setMessage("이 페이지에서 직접 저장 가능한 영상 주소를 찾지 못했습니다. 재생 버튼을 누른 후 다시 시도해 주세요. blob·DRM 영상은 지원하지 않습니다.")
                .setPositiveButton("감지된 파일 보기",(d,w)->showMedia())
                .setNeutralButton("다운로드 목록",(d,w)->showDownloads())
                .setNegativeButton("취소",null).show();
            return;
        }
        String[] names=new String[available.size()];
        for(int i=0;i<available.size();i++){
            Uri u=Uri.parse(available.get(i));
            String part=u.getLastPathSegment();
            String host=u.getHost();
            names[i]=(part==null||part.isEmpty()?"미디어 파일":part.length()>44?part.substring(0,44)+"…":part)
                +" · "+(host==null?"":host);
        }
        new AlertDialog.Builder(this).setTitle("저장할 영상 선택 · "+available.size()+"개")
            .setItems(names,(d,index)->downloadMedia(available.get(index)))
            .setNeutralButton("다운로드 목록",(d,w)->showDownloads())
            .setNegativeButton("취소",null).show();
    }
    private void showMedia(){clearPanels();foundList.setVisibility(View.VISIBLE);scanDomMedia();populateMedia();}
    private void populateMedia(){foundList.removeAllViews();TextView hdr=text("  감지된 미디어 " + media.size()+"개 · HLS 화질 선택 / 라이브 TS 녹화 지원",15);hdr.setPadding(dp(10),dp(12),0,dp(12));foundList.addView(hdr);
        addButton(foundList,"브라우저로 돌아가기",this::showWeb);
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);foundList.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(String u:new ArrayList<>(media)){
            LinearLayout item=row();
            String filename=Uri.parse(u).getLastPathSegment();
            TextView name=text(filename==null?"미디어":filename,12);
            name.setMaxLines(2);name.setPadding(dp(8),dp(6),dp(4),dp(6));
            item.addView(name,new LinearLayout.LayoutParams(0,dp(60),1));
            if(isDash(u))addButton(item,"DASH 미지원",()->Toast.makeText(this,"DASH(.mpd) 재생은 지원하지 않습니다",Toast.LENGTH_LONG).show());
            else if(isVideoFileCandidate(u))addButton(item,"앱내 재생",()->playNativeVideo(u));
            addButton(item,isHls(u)?"HLS 받기":"다운로드",()->downloadMedia(u));
            list.addView(item);
        }
    }
    private void togglePlaybackRow(){
        if(playerPane!=null&&playerPane.getVisibility()==View.VISIBLE){
            Toast.makeText(this,"앱내 플레이어 하단의 재생·정지 버튼을 사용하세요",Toast.LENGTH_SHORT).show();
            return;
        }
        if(web.getVisibility()!=View.VISIBLE)showWeb();
        playbackRow.setVisibility(playbackRow.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
    }
    private void webPlayback(String cmd){
        String operation="play".equals(cmd)?"v.play().catch(function(){});":
            "pause".equals(cmd)?"v.pause();":"v.pause();v.currentTime=0;";
        String js="(function(){var n=0;document.querySelectorAll('video').forEach(function(v){try{v.controls=true;"
            +operation+"n++;}catch(e){}});return n;})()";
        web.evaluateJavascript(js,value->{
            if("0".equals(value))Toast.makeText(this,
                "조작 가능한 영상이 없습니다. 다른 도메인 iframe·DRM 플레이어는 직접 제어할 수 없습니다.",Toast.LENGTH_LONG).show();
            else Toast.makeText(this,("play".equals(cmd)?"재생":"pause".equals(cmd)?"일시정지":"정지")+" 요청: "+value+"개 영상",Toast.LENGTH_SHORT).show();
        });
    }
    private void stopNativeVideo(){
        if(playerView!=null){
            try{playerView.stopPlayback();}catch(Exception ignored){}
            playerView=null;
        }
    }
    private void playNativeVideo(String url){
        if(!TaskStore.validDownloadUrl(url)||isDash(url)){
            Toast.makeText(this,"지원되는 HTTPS 영상 주소가 아닙니다",Toast.LENGTH_LONG).show();
            return;
        }
        stopNativeVideo();clearPanels();
        playerPane.removeAllViews();playerPane.setVisibility(View.VISIBLE);
        TextView title=text("MediaShield 영상 플레이어 · 탐색은 영상 터치",13);
        title.setPadding(dp(8),dp(10),dp(8),dp(10));playerPane.addView(title);
        playerView=new VideoView(this);
        playerPane.addView(playerView,new LinearLayout.LayoutParams(-1,0,1));
        MediaController seek=new MediaController(this);
        seek.setAnchorView(playerView);playerView.setMediaController(seek);
        playerView.setOnPreparedListener(mp->{if(playerView!=null){playerView.start();seek.show(4000);}});
        playerView.setOnErrorListener((mp,what,extra)->{
            new AlertDialog.Builder(this).setTitle("영상 재생 실패")
                .setMessage("스트림 형식이나 영상 서버 정책으로 인해 기본 Android 플레이어에서 재생할 수 없습니다. "+
                    "blob 기반·DRM 영상은 지원하지 않습니다.\n오류: "+what+" / "+extra)
                .setPositiveButton("확인",null).show();
            return true;
        });
        LinearLayout buttons=row();
        addButton(buttons,"▶ 재생",()->{if(playerView!=null){playerView.start();seek.show(3000);}});
        addButton(buttons,"Ⅱ 일시정지",()->{if(playerView!=null&&playerView.isPlaying())playerView.pause();});
        addButton(buttons,"■ 정지",()->{if(playerView!=null){playerView.pause();playerView.seekTo(0);}});
        addButton(buttons,"← 웹",()->{stopNativeVideo();showWeb();});
        playerPane.addView(buttons,new LinearLayout.LayoutParams(-1,dp(55)));
        HashMap<String,String> headers=new HashMap<>();
        headers.put("User-Agent",web.getSettings().getUserAgentString());
        String referer=web.getUrl();
        if(TaskStore.validUrl(referer))headers.put("Referer",referer);
        if(siteHostname(url).equals(siteHostname(referer))){
            String cookie=android.webkit.CookieManager.getInstance().getCookie(url);
            if(cookie!=null&&!cookie.isEmpty())headers.put("Cookie",cookie);
        }
        playerView.setVideoURI(Uri.parse(url),headers);
        playerView.requestFocus();
        playerView.start();
    }
    private void downloadMedia(String url) {
        if(isHls(url)) selectHlsQuality(url);
        else enqueue(url,null,null,web.getSettings().getUserAgentString());
    }
    /** Inspect an explicit HLS URL on a worker thread. The network is never opened on the UI thread. */
    private void selectHlsQuality(String playlistUrl) {
        Toast.makeText(this,"HLS 화질 정보를 읽는 중…",Toast.LENGTH_SHORT).show();
        String agent=web.getSettings().getUserAgentString();
        String referer=web.getUrl();
        String cookie=android.webkit.CookieManager.getInstance().getCookie(playlistUrl);
        new Thread(()->{
            try {
                HlsPlaylist list=HlsInspector.load(playlistUrl,agent,referer,cookie);
                runOnUiThread(()->{
                    if(list.variants.isEmpty()) {enqueue(playlistUrl,"video/mp2t",null,agent);return;}
                    String[] labels=new String[list.variants.size()];
                    for(int i=0;i<labels.length;i++){
                        HlsPlaylist.Variant v=list.variants.get(i);
                        String res=v.resolution.isEmpty()?"해상도 미상":v.resolution;
                        labels[i]=res+" · "+(v.bandwidth/1000)+"kbps";
                    }
                    new AlertDialog.Builder(this).setTitle("HLS 다운로드 화질 선택")
                        .setItems(labels,(dialog,which)->enqueue(list.variants.get(which).url,"video/mp2t",null,agent))
                        .setNegativeButton("취소",null).show();
                });
            } catch(Exception error) {
                runOnUiThread(()->new AlertDialog.Builder(this)
                    .setTitle("HLS 정보 확인 실패")
                    .setMessage(error.getMessage()+"\n직접 주소로 다운로드를 시도하시겠습니까?")
                    .setPositiveButton("시도",(d,w)->enqueue(playlistUrl,"video/mp2t",null,agent))
                    .setNegativeButton("취소",null).show());
            }
        },"HLS-quality-check").start();
    }
    private void enqueue(String u,String mime,String name,String agent){if(!TaskStore.validDownloadUrl(u)){Toast.makeText(this,"안전을 위해 HTTPS 파일 주소만 다운로드할 수 있습니다",Toast.LENGTH_LONG).show();return;}
        if(isDash(u)){Toast.makeText(this,"DASH(.mpd)는 아직 지원하지 않습니다",Toast.LENGTH_LONG).show();return;}
        if(isHls(u) || "video/mp2t".equals(mime)){
            if(name==null || name.trim().isEmpty()){
                String urlName=Uri.parse(u).getLastPathSegment();
                name=urlName==null||urlName.isEmpty()?"recording":urlName;
            }
            name=name.replaceAll("(?i)\\.m3u8$","");
            if(!name.toLowerCase(Locale.ROOT).endsWith(".ts"))name+=".ts";
            mime="video/mp2t";
        }
        String id=TaskStore.add(this,u,mime,web.getUrl(),android.webkit.CookieManager.getInstance().getCookie(u),agent,name);
        if(id==null)return;Intent svc=new Intent(this,DownloadService.class).setAction(DownloadService.ACTION_ENQUEUE).putExtra(DownloadService.EXTRA_ID,id);
        try{startForegroundService(svc);Toast.makeText(this,"다운로드 대기열에 추가",Toast.LENGTH_SHORT).show();}catch(Exception e){TaskStore.patch(this,id,"status","failed");Toast.makeText(this,"다운로드 시작 실패: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void showDownloads(){clearPanels();downloadsList.setVisibility(View.VISIBLE);populateDownloads();}
    private void action(String act,String id){Intent svc=new Intent(this,DownloadService.class).setAction(act).putExtra(DownloadService.EXTRA_ID,id);try{startForegroundService(svc);}catch(Exception e){Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();}populateDownloads();}
    private void populateDownloads(){downloadsList.removeAllViews();TextView hdr=text("  다운로드 대기열 · 일시정지 / 재개 / 취소",15);hdr.setPadding(dp(8),dp(12),0,dp(12));downloadsList.addView(hdr);
        EditText direct=field("파일 주소 또는 HLS(.m3u8) 붙여넣기");
        direct.setText(typedDirectUrl);directInputBox=direct;
        direct.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence str,int start,int count,int after){}
            public void onTextChanged(CharSequence str,int start,int before,int count){typedDirectUrl=str.toString();}
            public void afterTextChanged(android.text.Editable e){}
        });downloadsList.addView(direct);
        addButton(downloadsList,"입력한 URL 다운로드 / HLS 화질 선택",()->downloadMedia(direct.getText().toString().trim()));
        Switch fast=new Switch(this);fast.setText("고속 분할 다운로드 (지원 서버 · 큰 파일에서 자동 적용)");fast.setTextColor(Color.WHITE);
        fast.setChecked(fastDownloads);fast.setOnCheckedChangeListener((button,checked)->{
            fastDownloads=checked;getSharedPreferences("download_settings",MODE_PRIVATE).edit().putBoolean("fast",checked).apply();
        });downloadsList.addView(fast);
        addButton(downloadsList,"브라우저로 돌아가기",this::showWeb);
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);downloadsList.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));JSONArray data=TaskStore.all(this);
        for(int i=data.length()-1;i>=0;i--){JSONObject t=data.optJSONObject(i);if(t==null)continue;String id=t.optString("id"),state=t.optString("status");long done=t.optLong("done"),total=t.optLong("total",-1);int pct=total>0?(int)Math.min(100,done*100/total):-1;
            String speed=t.optLong("speed")>0?" · "+(t.optLong("speed")/1024)+" KB/s":"";
            TextView label=text(t.optString("name")+"\n"+state+" · "+(pct>=0?pct+"%":done/1024+" KB")+speed+("failed".equals(state)?" · "+t.optString("error"):""),12);label.setPadding(dp(10),dp(10),dp(10),dp(6));list.addView(label);
            LinearLayout buttons=row();if("downloading".equals(state)||"queued".equals(state))addButton(buttons,"일시정지",()->action(DownloadService.ACTION_PAUSE,id));
            if("paused".equals(state)||"failed".equals(state))addButton(buttons,"재개",()->action(DownloadService.ACTION_RESUME,id));
            if("hls".equals(t.optString("kind")) && "downloading".equals(state))addButton(buttons,"녹화 종료·저장",()->action(DownloadService.ACTION_FINISH,id));
            if(!"completed".equals(state)&&!"canceled".equals(state)&&!"finishing".equals(state))addButton(buttons,"취소",()->action(DownloadService.ACTION_CANCEL,id));list.addView(buttons);
        }
    }
    private void tick(){
        if(refresh && downloadsList!=null && downloadsList.getVisibility()==View.VISIBLE && (directInputBox==null || !directInputBox.hasFocus()))populateDownloads();
        if(refresh && web!=null && web.getVisibility()==View.VISIBLE && ++scanTick%4==0)scanDomMedia();
        if(refresh)handler.postDelayed(this::tick,1100);
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleShare(i);}
    private void handleShare(Intent i){if(i==null)return;String raw=null;
        if(Intent.ACTION_SEND.equals(i.getAction()))raw=i.getStringExtra(Intent.EXTRA_TEXT);
        else if(Intent.ACTION_VIEW.equals(i.getAction())&&i.getData()!=null)raw=i.getData().toString();
        if(raw==null)return;Matcher m=sharedUrl.matcher(raw);if(m.find()){String url=m.group();urlBox.setText(url);if(looksLikeMedia(url)){addMedia(url);showMedia();}else{navigateTo(url);}}}
    private void showAdblock(){
        clearPanels();adPane.setVisibility(View.VISIBLE);adPane.removeAllViews();
        ScrollView scroll=new ScrollView(this);adPane.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout pane=new LinearLayout(this);pane.setOrientation(1);pane.setPadding(dp(12),dp(10),dp(12),dp(16));scroll.addView(pane);
        TextView heading=text("광고 · 추적기 차단",20);pane.addView(heading);
        TextView info=text("브라우저 안에서 광고/추적 주소를 차단합니다. 다른 앱의 광고는 차단하지 않습니다.",12);
        info.setPadding(0,dp(8),0,dp(8));pane.addView(info);
        TextView counter=text("현재 페이지 차단 "+adBlocker.getPageCount()+"건 · 앱 실행 후 "+adBlocker.getSessionCount()+"건",13);
        pane.addView(counter);
        Switch main=new Switch(this);main.setText("광고 및 추적기 차단 (기본 켜짐)");main.setTextColor(Color.WHITE);
        main.setChecked(adBlocker.isEnabled());pane.addView(main);
        main.setOnCheckedChangeListener((button,checked)->{adBlocker.setEnabled(checked);reloadPageForBlocker();});
        Switch cosmetic=new Switch(this);cosmetic.setText("광고 영역 숨기기");cosmetic.setTextColor(Color.WHITE);
        cosmetic.setChecked(adBlocker.isCosmeticEnabled());pane.addView(cosmetic);
        cosmetic.setOnCheckedChangeListener((button,checked)->{adBlocker.setCosmetic(checked);reloadPageForBlocker();});
        Switch popups=new Switch(this);popups.setText("자동 팝업 제한");popups.setTextColor(Color.WHITE);
        popups.setChecked(adBlocker.isPopupBlockEnabled());pane.addView(popups);
        popups.setOnCheckedChangeListener((button,checked)->{
            adBlocker.setPopups(checked);
            web.getSettings().setJavaScriptCanOpenWindowsAutomatically(!checked);
            // WebView never creates a separate app tab. Disabling this switch permits JS-triggered same-view popups on some sites.
        });
        String current=pageUrl;
        TextView site=text("현재 사이트: "+(Uri.parse(current).getHost()==null?"없음":Uri.parse(current).getHost()),13);
        site.setPadding(0,dp(16),0,dp(4));pane.addView(site);
        Switch except=new Switch(this);except.setText("이 사이트는 광고 차단 제외");except.setTextColor(Color.WHITE);
        except.setChecked(adBlocker.isSiteAllowed(current));pane.addView(except);
        except.setOnCheckedChangeListener((button,checked)->{
            if(Uri.parse(pageUrl).getHost()==null){Toast.makeText(this,"먼저 웹사이트를 열어주세요",Toast.LENGTH_SHORT).show();except.setChecked(false);return;}
            adBlocker.setSiteAllowed(pageUrl,checked);reloadPageForBlocker();
        });
        TextView rules=text("내장 규칙: "+adBlocker.getBuiltInRuleCount()+"개 · 외부 규칙: "+adBlocker.getRemoteRuleCount()+"개",12);
        rules.setPadding(0,dp(12),0,dp(8));pane.addView(rules);
        addButton(pane,"무료 공개 광고 차단 목록 업데이트",()->{
            rules.setText("광고 차단 목록 다운로드 중…");
            adBlocker.updateFromCommunityList((ok,message)->{
                rules.setText("내장 규칙: "+adBlocker.getBuiltInRuleCount()+"개 · 외부 규칙: "+adBlocker.getRemoteRuleCount()+"개\n"+message);
                if(ok)reloadPageForBlocker();
            });
        });
        TextView note=text("외부 목록: StevenBlack/hosts. 이용자가 업데이트를 누를 때만 내려받습니다. 일부 광고는 WebView/사이트 구조상 차단되지 않을 수 있습니다. 영상이 재생되지 않으면 사이트별 예외를 사용하세요.",12);
        note.setPadding(0,dp(10),0,dp(14));pane.addView(note);
        addButton(pane,"브라우저로 돌아가기",this::showWeb);
    }
    private void reloadPageForBlocker(){
        // Preferences are stored immediately. Reload keeps any per-site override effective.
        if(web!=null&&web.getUrl()!=null)web.reload();
    }
    private EditText field(String hint){EditText e=new EditText(this);e.setTextColor(Color.WHITE);e.setHintTextColor(0xffa5b2c1);e.setHint(hint);e.setSingleLine(true);return e;}
    @Override public void onBackPressed(){if(customVideoView!=null){hideVideoFullscreen();return;}if(playerPane.getVisibility()==View.VISIBLE){stopNativeVideo();showWeb();return;}if(web.getVisibility()!=View.VISIBLE){showWeb();return;}if(web.canGoBack()){web.goBack();return;}super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){web.saveState(out);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){stopNativeVideo();hideVideoFullscreen();refresh=false;handler.removeCallbacksAndMessages(null);if(web!=null){web.destroy();}if(adBlocker!=null)adBlocker.close();super.onDestroy();}
}