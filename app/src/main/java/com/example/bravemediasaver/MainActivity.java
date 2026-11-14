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
import java.util.*;
import java.util.regex.*;

/** Android WebView-powered browser; not a fork of Brave or Chromium. */
public class MainActivity extends Activity {
    private WebView web;
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
        TextView shield=text("◆",18);shield.setTextColor(0xffff663d);shield.setGravity(Gravity.CENTER);
        nav.addView(shield,new LinearLayout.LayoutParams(dp(40),dp(46)));
        shieldButton=shield;
        shield.setContentDescription("개인정보 보호 및 광고 차단 설정");
        shield.setOnClickListener(v->showAdblock());
        urlBox=new EditText(this);urlBox.setSingleLine(true);urlBox.setTextColor(Color.WHITE);
        urlBox.setSelectAllOnFocus(true);urlBox.setHintTextColor(0xff9eabbf);urlBox.setHint("검색어 또는 주소 입력");
        urlBox.setTextSize(14);urlBox.setPadding(dp(12),0,dp(6),0);
        urlBox.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        urlBox.setBackground(makeRounded(0xff30333a,14));
        nav.addView(urlBox,new LinearLayout.LayoutParams(0,dp(46),1));
        urlBox.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        urlBox.setOnFocusChangeListener((v,hasFocus)->{if(hasFocus)urlBox.selectAll();});
        urlBox.setOnEditorActionListener((v,action,event)->{go();return true;});
        addNavButton(nav,"↻",()->{showWeb();web.reload();},42);
        addNavButton(nav,"⋮",this::showBrowserMenu,42);
        tools=row();tools.setBackgroundColor(0xff17191f);
        status=text("",10);status.setVisibility(View.GONE);
        web=new WebView(this);web.setBackgroundColor(Color.WHITE);
        WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setMediaPlaybackRequiresUserGesture(true);ws.setBuiltInZoomControls(true);ws.setDisplayZoomControls(false);ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setJavaScriptCanOpenWindowsAutomatically(!adBlocker.isPopupBlockEnabled());
        ws.setSafeBrowsingEnabled(true);ws.setAllowFileAccessFromFileURLs(false);ws.setAllowUniversalAccessFromFileURLs(false);ws.setSaveFormData(false);
        ws.setSupportMultipleWindows(false);
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
                addContentView(videoContainer,new ViewGroup.LayoutParams(-1,-1));
                getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            @Override public void onHideCustomView(){hideVideoFullscreen();}
            @Override public boolean onCreateWindow(WebView view,boolean isDialog,boolean userGesture,android.os.Message resultMsg){return false;}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap icon){pageUrl=u;mainFrameFailed=false;lastWebError=0;adBlocker.resetPageCount();media.clear();urlBox.setText(u);}
            @Override public void onPageFinished(WebView v,String u){
                pageUrl=u;urlBox.setText(u);
                if(mainFrameFailed)return;
                scanDomMedia();
                if(adBlocker.isEnabled()&&adBlocker.isCosmeticEnabled()&&!adBlocker.isSiteAllowed(u))
                    v.evaluateJavascript(AdBlocker.COSMETIC_JS,null);
                status.setText("미디어 " + media.size() + "개 · 광고/추적 요청 차단 " + adBlocker.getPageCount() + "건");
                shieldButton.setText(adBlocker.isEnabled()&&!adBlocker.isSiteAllowed(u)?"◆":"◇");
                shieldButton.setTextColor(adBlocker.isEnabled()&&!adBlocker.isSiteAllowed(u)?0xffff663d:0xff9fa7b2);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
                String u=request.getUrl().toString();
                if(adBlocker.shouldBlock(u,pageUrl,request.isForMainFrame()))return adBlocker.blankResponse();
                if(liveScan&&looksLikeMedia(u))runOnUiThread(()->addMedia(u));
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){String u=request.getUrl().toString();if(u.startsWith("http://")||u.startsWith("https://"))return false;return true;}
            @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError error){
                if(!req.isForMainFrame())return;
                mainFrameFailed=true;
                lastWebError=error.getErrorCode();
                lastFailedUrl=req.getUrl().toString();
                lastErrorDescription=String.valueOf(error.getDescription());
                runOnUiThread(()->showConnectionFailure(lastFailedUrl,lastWebError,lastErrorDescription));
            }
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();}
        });
        web.setDownloadListener((u,ua,disposition,mime,size)->{
            if(looksLikeMedia(u))addMedia(u);
            if(mime!=null && mime.toLowerCase(Locale.ROOT).contains("mpegurl"))selectHlsQuality(u);
            else enqueue(u,mime,URLUtil.guessFileName(u,disposition,mime),ua);
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        foundList=new LinearLayout(this);foundList.setOrientation(LinearLayout.VERTICAL);
        downloadsList=new LinearLayout(this);downloadsList.setOrientation(LinearLayout.VERTICAL);
        adPane=new LinearLayout(this);adPane.setOrientation(LinearLayout.VERTICAL);
        bookmarkPane=new LinearLayout(this);bookmarkPane.setOrientation(LinearLayout.VERTICAL);
        root.addView(foundList,new LinearLayout.LayoutParams(-1,0,1));root.addView(downloadsList,new LinearLayout.LayoutParams(-1,0,1));root.addView(adPane,new LinearLayout.LayoutParams(-1,0,1));root.addView(bookmarkPane,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bottom=row();bottomBar=bottom;bottom.setBackgroundColor(0xff17191f);
        bottom.setPadding(dp(5),dp(2),dp(5),dp(5));root.addView(bottom);
        addBottomButton(bottom,"⌂",()->{showWeb();web.loadUrl("https://www.google.com");});
        addBottomButton(bottom,"‹",()->{if(web.canGoBack()){showWeb();web.goBack();}});
        addBottomButton(bottom,"›",()->{if(web.canGoForward()){showWeb();web.goForward();}});
        addBottomButton(bottom,"▶",this::showMedia);
        addBottomButton(bottom,"⇩",this::showDownloads);
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
        bar.addView(btn,new LinearLayout.LayoutParams(0,dp(48),1));
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
        String[] actions={"주소창 위/아래 이동", "현재 사이트 즐겨찾기", "즐겨찾기 목록", "광고 차단 설정", "이 사이트 광고 차단 잠시 끄기", "서드파티 쿠키 허용 설정", "Brave에서 현재 사이트 열기", "새로고침", "마지막 연결 오류 정보", "이 브라우저에 관하여"};
        new AlertDialog.Builder(this).setTitle("MediaShield · 메뉴")
        .setItems(actions,(d,which)->{
            switch(which){
                case 0:
                    boolean bottom=!getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("bottom_address",false);
                    getSharedPreferences("appearance",MODE_PRIVATE).edit().putBoolean("bottom_address",bottom).apply();
                    setAddressBarBottom(bottom);break;
                case 1:bookmarkCurrent();break;
                case 2:showBookmarks();break;
                case 3:showAdblock();break;
                case 4:
                    String url=web.getUrl();if(!TaskStore.validUrl(url))break;
                    adBlocker.setSiteAllowed(url,!adBlocker.isSiteAllowed(url));
                    Toast.makeText(this,adBlocker.isSiteAllowed(url)?"이 사이트 광고 차단 제외":"광고 차단 적용",Toast.LENGTH_SHORT).show();
                    web.reload();break;
                case 5:showThirdPartyCookiesSetting();break;
                case 6:openExternalBrowser();break;
                case 7:web.reload();break;
                case 8:showConnectionFailure(lastFailedUrl,lastWebError,lastErrorDescription);break;
                case 9:new AlertDialog.Builder(this).setTitle("MediaShield Browser · 보안 점검 버전")
                    .setMessage("Android WebView 기반 독립 브라우저입니다. Brave 공식 앱이 아니며 모든 사이트 및 DRM 영상과 호환되지는 않습니다.\n\n사이트 연결이 끊어지면 네트워크·DNS·사이트 정책 때문일 수도 있습니다.")
                    .setPositiveButton("확인",null).show();break;
            }
        }).show();
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
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web,enabled);
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
            .setMessage(explanation)
            .setPositiveButton("다시 시도",(d,w)->{showWeb();web.loadUrl(failedUrl);})
            .setNeutralButton("Brave에서 열기",(d,w)->openExternalUrl(failedUrl))
            .setNegativeButton("광고 차단 예외",(d,w)->{
                if(TaskStore.validUrl(failedUrl)){
                    adBlocker.setSiteAllowed(failedUrl,true);
                    Toast.makeText(this,"이 사이트에서만 광고 차단을 해제했습니다",Toast.LENGTH_SHORT).show();
                    showWeb();web.loadUrl(failedUrl);
                }
            }).show();
    }
    private void openExternalUrl(String url){
        if(!TaskStore.validUrl(url))return;
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setPackage("com.brave.browser");
        try{startActivity(intent);}catch(Exception e){intent.setPackage(null);
            try{startActivity(Intent.createChooser(intent,"외부 브라우저로 열기"));}
            catch(Exception ignored){Toast.makeText(this,"열 수 있는 브라우저가 없습니다",Toast.LENGTH_LONG).show();}}
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
        Button b=new Button(this);b.setText(label);b.setTextSize(11);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);
        b.setOnClickListener(v->click.run());r.addView(b,new LinearLayout.LayoutParams(dp(w),dp(48)));
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
        for(String u:links){addButton(list,u,()->{showWeb();web.loadUrl(u);});}
        if(links.isEmpty())list.addView(text("  저장된 주소가 없습니다. 상단 ★ 버튼으로 추가할 수 있습니다.",13));
    }
    private void clearPanels(){hideKeyboard();web.setVisibility(View.GONE);foundList.setVisibility(View.GONE);downloadsList.setVisibility(View.GONE);adPane.setVisibility(View.GONE);bookmarkPane.setVisibility(View.GONE);}
    private void showWeb(){clearPanels();web.setVisibility(View.VISIBLE);}
    private void go(){String u=urlBox.getText().toString().trim();if(u.isEmpty())return;hideKeyboard();if(!u.startsWith("http://")&&!u.startsWith("https://")){if(u.contains(".")&&!u.contains(" "))u="https://"+u;else u="https://www.google.com/search?q="+Uri.encode(u);}showWeb();web.loadUrl(u);}
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
    private void showMedia(){clearPanels();foundList.setVisibility(View.VISIBLE);scanDomMedia();populateMedia();}
    private void populateMedia(){foundList.removeAllViews();TextView hdr=text("  감지된 미디어 " + media.size()+"개 · HLS 화질 선택 / 라이브 TS 녹화 지원",15);hdr.setPadding(dp(10),dp(12),0,dp(12));foundList.addView(hdr);
        addButton(foundList,"브라우저로 돌아가기",this::showWeb);
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);foundList.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(String u:new ArrayList<>(media)){LinearLayout item=row();TextView name=text(Uri.parse(u).getLastPathSegment(),12);name.setMaxLines(2);name.setPadding(dp(8),dp(6),dp(4),dp(6));item.addView(name,new LinearLayout.LayoutParams(0,dp(60),1));
            if(isDash(u))addButton(item,"DASH 안내",()->Toast.makeText(this,"DASH(.mpd) 저장은 지원하지 않습니다",Toast.LENGTH_LONG).show());
            else addButton(item,isHls(u)?"화질 선택 / HLS 받기":"다운로드",()->downloadMedia(u));list.addView(item);}
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
        if(raw==null)return;Matcher m=sharedUrl.matcher(raw);if(m.find()){String url=m.group();urlBox.setText(url);if(looksLikeMedia(url)){addMedia(url);showMedia();}else{showWeb();web.loadUrl(url);}}}
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
    @Override public void onBackPressed(){if(customVideoView!=null){hideVideoFullscreen();return;}if(web.getVisibility()!=View.VISIBLE){showWeb();return;}if(web.canGoBack()){web.goBack();return;}super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){web.saveState(out);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){hideVideoFullscreen();refresh=false;handler.removeCallbacksAndMessages(null);if(web!=null){web.destroy();}if(adBlocker!=null)adBlocker.close();super.onDestroy();}
}