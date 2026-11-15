package com.example.bravemediasaver;

import android.content.Context;
import android.net.Uri;
import android.webkit.URLUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent queue shared by Activity and foreground Service. All access within one process. */
public final class TaskStore {
    private static final String PREF = "queue";
    private static final String KEY = "items";
    private TaskStore() {}

    public static synchronized JSONArray all(Context ctx) {
        String raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]");
        try { return new JSONArray(raw); } catch (Exception e) { return new JSONArray(); }
    }
    private static void save(Context ctx, JSONArray data) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, data.toString()).commit();
    }
    public static synchronized JSONObject get(Context ctx, String id) {
        JSONArray data=all(ctx);
        for(int i=0;i<data.length();i++) {
            JSONObject t=data.optJSONObject(i);
            if(t!=null && id.equals(t.optString("id"))) return t;
        }
        return null;
    }
    public static synchronized String add(Context ctx,String url,String mime,String referer,String cookie,String ua,String suggestedName) {
        if (!validDownloadUrl(url)) return null;
        String name=suggestedName;
        if(name==null || name.trim().isEmpty()) name=URLUtil.guessFileName(url, null, mime);
        name=safeName(name);
        JSONObject t=new JSONObject();
        String id=UUID.randomUUID().toString();
        try {
            t.put("id",id);t.put("url",url);t.put("name",name);
            t.put("kind",(url.matches("(?i).*\\.m3u8(?:[?#].*)?$") || "video/mp2t".equals(mime))?"hls":"file");
            t.put("lastSeq",-1L);t.put("speed",0L);
            t.put("mime",mime==null?"":mime);t.put("referer",validUrl(referer)?referer:"");
            // Never persist website authentication cookies in the download queue.
            t.put("cookie","");t.put("ua",ua==null?"":ua);
            t.put("status","queued");t.put("done",0L);t.put("total",-1L);t.put("error","");
            t.put("created",System.currentTimeMillis());
        } catch(Exception ignored) {}
        JSONArray data=all(ctx);data.put(t);save(ctx,data);return id;
    }
    public static synchronized void patch(Context ctx,String id,String key,Object value) {
        JSONArray data=all(ctx);
        for(int i=0;i<data.length();i++) {
            JSONObject t=data.optJSONObject(i);
            if(t!=null && id.equals(t.optString("id"))) {
                try { t.put(key,value); } catch(Exception ignored) {}
                save(ctx,data);return;
            }
        }
    }
    public static synchronized void progress(Context ctx,String id,long done,long total) {
        JSONArray data=all(ctx);
        for(int i=0;i<data.length();i++) {
            JSONObject t=data.optJSONObject(i);
            if(t!=null && id.equals(t.optString("id"))) {
                try {t.put("done",done);t.put("total",total);}catch(Exception ignored){}
                save(ctx,data);return;
            }
        }
    }
    public static synchronized String nextQueued(Context ctx) {
        JSONArray data=all(ctx);
        for(int i=0;i<data.length();i++) {
            JSONObject t=data.optJSONObject(i);
            if(t!=null && "queued".equals(t.optString("status"))) return t.optString("id");
        }
        return null;
    }
    public static synchronized void recoverInterrupted(Context ctx) {
        JSONArray data=all(ctx);boolean modified=false;
        for(int i=0;i<data.length();i++) {
            JSONObject t=data.optJSONObject(i);
            if(t!=null && "downloading".equals(t.optString("status"))) {
                try {t.put("status","paused");modified=true;}catch(Exception ignored){}
            }
        }
        if(modified)save(ctx,data);
    }
    public static boolean validUrl(String text) {
        if(text==null)return false;
        try { Uri uri=Uri.parse(text);return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost()!=null; }
        catch (Exception e) {return false;}
    }
    /** Downloads never use unencrypted HTTP; navigation may still accept HTTP addresses. */
    public static boolean validDownloadUrl(String text) {
        if (!validUrl(text)) return false;
        try { return "https".equalsIgnoreCase(Uri.parse(text).getScheme()); }
        catch (Exception ignored) { return false; }
    }
    public static String safeName(String input) {
        if(input==null) input="media";
        String s=input.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").replaceAll("^\\.+", "_").trim();
        if(s.isEmpty())s="media";
        if(s.length()>100)s=s.substring(s.length()-100);
        return s;
    }
}
