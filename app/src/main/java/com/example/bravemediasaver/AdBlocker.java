package com.example.bravemediasaver;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-app WebView ad/tracker request blocking; not a device-wide ad blocker.
 * Host matching is exact or on dot-boundary: ad.example.com matches example.com,
 * but not notexample.com.
 *
 * Third-party list source (on explicit user update only):
 * https://github.com/StevenBlack/hosts (aggregated lists, various licenses).
 */
public final class AdBlocker {
    private static final String PREF = "ad_block_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_COSMETIC = "cosmetic";
    private static final String KEY_POPUPS = "popups";
    private static final String KEY_EXCEPTIONS = "exceptions";
    private static final String LIST_FILE = "downloaded_ad_hosts.txt";
    private static final String REMOTE_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";

    // Compact built-in fallback that works offline before any community list update.
    private static final String[] BUILT_IN = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org", "rubiconproject.com",
        "pubmatic.com", "openx.net", "criteo.com", "criteo.net", "taboola.com",
        "outbrain.com", "revcontent.com", "smartadserver.com", "adform.net",
        "advertising.com", "bidswitch.net", "casalemedia.com", "contextweb.com",
        "yieldmo.com", "lijit.com", "sharethrough.com", "triplelift.com",
        "sovrn.com", "mediavine.com", "media.net", "mgid.com", "zedo.com",
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "trafficjunky.net", "juicyads.com", "adsterra.com", "hilltopads.net",
        "adroll.com", "quantserve.com", "scorecardresearch.com", "moatads.com",
        "hotjar.com", "mouseflow.com", "clicktale.net", "chartbeat.net",
        "newrelic.com", "segment.io", "branch.io", "appsflyer.com",
        "adjust.com", "mixpanel.com", "amplitude.com", "optimizely.com",
        "onesignal.com", "permutive.com", "lotame.com", "bluekai.com",
        "demdex.net", "adsafeprotected.com", "innovid.com", "teads.tv",
        "google-analytics.com", "googletagmanager.com", "facebook.net",
        "analytics.yahoo.com", "ads.yahoo.com", "advertising.yahoo.com",
        "amazon-adsystem.com", "aaxads.com", "indexww.com", "sonobi.com",
        "gumgum.com", "undertone.com", "flashtalking.com", "spotxchange.com",
        "spotx.tv", "springserve.com", "fyber.com", "inmobi.com",
        "unityads.unity3d.com", "applovin.com", "vungle.com", "ironsrc.com",
        "adcolony.com", "smaato.net", "startappservice.com", "tapjoy.com",
        "adskeeper.co.uk", "adcash.com", "bidvertiser.com", "popunder.net"
    };

    // Safely targeted, less intrusive than hiding all elements containing "ad".
    // Only remove clearly labeled ad containers. This does not claim to block in-stream YouTube ads.
    public static final String COSMETIC_JS = "(function(){" +
        "if(document.getElementById('media_browser_hide_ads'))return;" +
        "var css='ins.adsbygoogle,[id^=\\\"div-gpt-ad\\\"]," +
        "[id^=\\\"google_ads_\\\"],[data-ad-client]," +
        "iframe[src*=\\\"doubleclick.net\\\"],iframe[src*=\\\"googlesyndication.com\\\"]," +
        "[class~=\\\"ad-slot\\\"],[class~=\\\"ad-container\\\"]," +
        "[data-testid=\\\"ad-container\\\"],ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,ytm-companion-ad-renderer{display:none!important}';" +
        "var style=document.createElement('style');style.id='media_browser_hide_ads';style.textContent=css;" +
        "(document.head||document.documentElement).appendChild(style);" +
        "})();";

    // Best-effort UI enhancement for SKIPPABLE YouTube ads only.
    // Does not intercept streaming data, bypass DRM, or claim to remove every video ad.
    public static final String YOUTUBE_SKIPPABLE_AD_JS = "(function(){"
        + "if(!/(^|\\\\.)youtube\\\\.com$/.test(location.hostname))return;"
        + "if(window.__mediaShieldSkipUi)return;window.__mediaShieldSkipUi=true;"
        + "setInterval(function(){"
        + "var buttons=document.querySelectorAll('button.ytp-ad-skip-button,button.ytp-ad-skip-button-modern,button.ytp-skip-ad-button,ytm-skip-ad-button button');"
        + "for(var i=0;i<buttons.length;i++){var b=buttons[i];"
        + "if(b&&b.getClientRects().length&&b.getAttribute('aria-disabled')!=='true'&&!b.disabled){b.click();break;}}"
        + "},1500);"
        + "})();";

    private final Context app;
    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger pageCount = new AtomicInteger();
    private final AtomicInteger sessionCount = new AtomicInteger();
    private volatile Set<String> downloaded = Collections.emptySet();
    private volatile Set<String> exceptions = Collections.emptySet();
    private volatile boolean enabled, cosmetic, popups;

    public AdBlocker(Context context) {
        app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        enabled = prefs.getBoolean(KEY_ENABLED, true);
        cosmetic = prefs.getBoolean(KEY_COSMETIC, true);
        popups = prefs.getBoolean(KEY_POPUPS, true);
        exceptions = readExceptions();
        io.execute(() -> {
            File file = new File(app.getFilesDir(), LIST_FILE);
            if (file.exists()) try {
                downloaded = readHostList(file);
            } catch (Exception ignored) { /* Built-in list still works */ }
        });
    }

    public boolean isEnabled() { return enabled; }
    public boolean isCosmeticEnabled() { return cosmetic; }
    public boolean isPopupBlockEnabled() { return popups; }
    public int getPageCount() { return pageCount.get(); }
    public int getSessionCount() { return sessionCount.get(); }
    public int getRemoteRuleCount() { return downloaded.size(); }
    public int getBuiltInRuleCount() { return BUILT_IN.length; }
    public void resetPageCount() { pageCount.set(0); }

    public void setEnabled(boolean b) { enabled = b; prefs.edit().putBoolean(KEY_ENABLED,b).apply(); }
    public void setCosmetic(boolean b) { cosmetic = b; prefs.edit().putBoolean(KEY_COSMETIC,b).apply(); }
    public void setPopups(boolean b) { popups = b; prefs.edit().putBoolean(KEY_POPUPS,b).apply(); }

    public boolean isSiteAllowed(String pageUrl) { return HostMatcher.containsHostOrParent(exceptions, host(pageUrl)); }

    public synchronized void setSiteAllowed(String pageUrl, boolean allowed) {
        String site = host(pageUrl);
        if (site == null) return;
        Set<String> next = new HashSet<>(exceptions);
        if (allowed) next.add(site); else next.remove(site);
        exceptions = Collections.unmodifiableSet(next);
        prefs.edit().putStringSet(KEY_EXCEPTIONS, new HashSet<>(next)).apply();
    }

    private Set<String> readExceptions() {
        // Defensive copy: SharedPreferences documentation prohibits editing returned sets.
        return Collections.unmodifiableSet(new HashSet<>(prefs.getStringSet(KEY_EXCEPTIONS, Collections.emptySet())));
    }

    public boolean shouldBlock(String resourceUrl, String topPageUrl, boolean isMainFrame) {
        if (!enabled || isMainFrame || isSiteAllowed(topPageUrl)) return false;
        String target = host(resourceUrl);
        if (target == null) return false;
        // Never block the document's own host; reduces failures on sites that serve media and UI together.
        String top = host(topPageUrl);
        if (top != null && (target.equals(top) || target.endsWith("." + top))) return false;
        boolean deny = HostMatcher.containsHostOrParent(downloaded, target);
        if (!deny) for (String domain : BUILT_IN) {
            if (target.equals(domain) || target.endsWith("." + domain)) { deny = true; break; }
        }
        if (deny) { pageCount.incrementAndGet(); sessionCount.incrementAndGet(); }
        return deny;
    }

    public WebResourceResponse blankResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    private static String host(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch(Exception e) { return null; }
    }

    public void updateFromCommunityList(UpdateListener callback) {
        io.execute(() -> {
            HttpURLConnection conn = null;
            File tmp = null;
            try {
                conn = (HttpURLConnection)new URL(REMOTE_URL).openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", "MediaShieldBrowser/6.0");
                if(conn.getResponseCode()!=200) throw new Exception("서버 응답 HTTP "+conn.getResponseCode());
                tmp = File.createTempFile("hosts", ".tmp", app.getFilesDir());
                long bytes = 0;
                try (java.io.InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192]; int n;
                    while((n = in.read(buf))!=-1) {
                        bytes += n;
                        if(bytes > 14 * 1024 * 1024) throw new Exception("차단 목록 최대 크기 초과");
                        out.write(buf,0,n);
                    }
                }
                Set<String> parsed = readHostList(tmp);
                if(parsed.size() < 1000) throw new Exception("유효한 규칙이 너무 적습니다");
                File saved = new File(app.getFilesDir(), LIST_FILE);
                // Save only after full parsing succeeded; preserve old list on errors.
                if(!tmp.renameTo(saved))
                    throw new Exception("새 차단 규칙 파일 저장 실패 (기존 목록 유지)");
                downloaded = parsed;
                notifyUpdate(callback, true, "광고 차단 규칙 " + parsed.size() + "개 업데이트 완료");
            } catch (Exception e) {
                notifyUpdate(callback, false, "업데이트 실패: " + e.getMessage());
            } finally {
                if(conn!=null) conn.disconnect();
                if(tmp!=null) tmp.delete();
            }
        });
    }

    private void notifyUpdate(UpdateListener cb, boolean ok, String message) {
        new Handler(Looper.getMainLooper()).post(() -> cb.onUpdate(ok, message));
    }

    private static Set<String> readHostList(File file) throws Exception {
        Set<String> hosts = new HashSet<>();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                if(comment>=0) line = line.substring(0,comment);
                line=line.trim();
                if(line.isEmpty()) continue;
                String[] fields = line.split("\\s+");
                if(fields.length<2 || !(fields[0].equals("0.0.0.0") || fields[0].equals("127.0.0.1"))) continue;
                for(int i=1;i<fields.length;i++) {
                    String d=fields[i].toLowerCase(Locale.ROOT);
                    if(d.length()<4 || d.length()>253 || d.equals("localhost") || d.indexOf('.')<0 || !d.matches("[a-z0-9._-]+"))continue;
                    hosts.add(d);
                    if(hosts.size() > 220000) throw new Exception("규칙 수 초과");
                }
            }
        }
        return Collections.unmodifiableSet(hosts);
    }

    public void close() { io.shutdown(); }
    public interface UpdateListener { void onUpdate(boolean success, String message); }
}
