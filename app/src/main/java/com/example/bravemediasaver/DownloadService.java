package com.example.bravemediasaver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** One download at a time, resumable with HTTP Range when supported. */
public class DownloadService extends Service {
    public static final String ACTION_ENQUEUE="com.example.bravemediasaver.ENQUEUE";
    public static final String ACTION_PAUSE="com.example.bravemediasaver.PAUSE";
    public static final String ACTION_RESUME="com.example.bravemediasaver.RESUME";
    public static final String ACTION_CANCEL="com.example.bravemediasaver.CANCEL";
    public static final String ACTION_FINISH="com.example.bravemediasaver.FINISH";
    public static final String EXTRA_ID="id";
    private static final String CHANNEL="media_downloads";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Object lock=new Object();
    private boolean scheduled=false;
    private volatile String activeId;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"미디어 다운로드",NotificationManager.IMPORTANCE_LOW));
        TaskStore.recoverInterrupted(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        String action=intent==null?ACTION_ENQUEUE:intent.getAction();
        String id=intent==null?null:intent.getStringExtra(EXTRA_ID);
        if(id!=null) {
            JSONObject task=TaskStore.get(this,id);
            if(task!=null) {
                String old=task.optString("status");
                if(ACTION_PAUSE.equals(action) && ("queued".equals(old)||"downloading".equals(old))) TaskStore.patch(this,id,"status","paused");
                if(ACTION_RESUME.equals(action) && ("paused".equals(old)||"failed".equals(old))) TaskStore.patch(this,id,"status","queued");
                if(ACTION_FINISH.equals(action) && "hls".equals(task.optString("kind")) && "downloading".equals(old)) TaskStore.patch(this,id,"status","finishing");
                if(ACTION_CANCEL.equals(action) && !"completed".equals(old)) {
                    TaskStore.patch(this,id,"status","canceled");
                    if(!id.equals(activeId)) cleanParts(id);
                }
            }
        }
        startForeground(4201,notification("대기열 확인 중",0,-1));
        schedule();
        return START_NOT_STICKY;
    }
    private void schedule() {
        synchronized(lock) {
            if(scheduled)return;
            scheduled=true;
        }
        worker.execute(()-> {
            try {
                String id;
                while((id=TaskStore.nextQueued(this))!=null) {
                    activeId=id;
                    perform(id);
                    activeId=null;
                }
            } finally {
                synchronized(lock){scheduled=false;}
                if(TaskStore.nextQueued(this)!=null) schedule();
                else {stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
            }
        });
    }
    private Notification notification(String title,long done,long total) {
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=new Notification.Builder(this,CHANNEL)
            .setContentTitle("MediaShield Browser")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi).setOnlyAlertOnce(true).setOngoing(true);
        if(total>0)b.setProgress(100,(int)Math.min(100,done*100/total),false);
        else b.setProgress(0,0,true);
        return b.build();
    }
    private File partFile(String id) {return new File(getFilesDir(),"dl_"+id+".part");}
    private void cleanParts(String id) {
        partFile(id).delete();
        new File(getFilesDir(),"dl_"+id+".joining").delete();
        for(int i=0;i<ParallelRangeDownloader.PARTS;i++)
            new File(getFilesDir(),"dl_"+id+"_"+i+".part").delete();
    }
    private void perform(String id) {
        JSONObject t=TaskStore.get(this,id);
        if(t==null || !"queued".equals(t.optString("status")))return;
        TaskStore.patch(this,id,"status","downloading");
        File temp=partFile(id);
        if("hls".equals(t.optString("kind"))) {
            try { performHls(id,t,temp); }
            catch(Exception e) {
                JSONObject now=TaskStore.get(this,id);
                if(now!=null && ("downloading".equals(now.optString("status")) || "finishing".equals(now.optString("status")))) {
                    TaskStore.patch(this,id,"status","failed");
                    TaskStore.patch(this,id,"error",e.getMessage()==null?"HLS 다운로드 오류":e.getMessage());
                }
                if(now!=null && "canceled".equals(now.optString("status")))cleanParts(id);
            }
            return;
        }
        HttpURLConnection conn=null;
        try {
            boolean accelerated=getSharedPreferences("download_settings",MODE_PRIVATE).getBoolean("fast",true);
            if(accelerated && (!temp.exists()||temp.length()==0)) {
                String input=t.getString("url");
                AtomicLong lastNotification=new AtomicLong();
                boolean done=ParallelRangeDownloader.tryDownload(input,getFilesDir(),id,temp,
                    t.optString("rangeValidator"),
                    connectionUrl->{
                        HttpURLConnection c=(HttpsURLConnection)new URL(connectionUrl).openConnection();
                        c.setConnectTimeout(15000);c.setReadTimeout(15000);
                        c.setInstanceFollowRedirects(true);
                        String cookie=t.optString("cookie"),ua=t.optString("ua"),ref=t.optString("referer");
                        if(!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
                        if(!ua.isEmpty())c.setRequestProperty("User-Agent",ua);
                        if(!ref.isEmpty())c.setRequestProperty("Referer",ref);
                        return c;
                    },new ParallelRangeDownloader.Control(){
                        public boolean running() {
                            JSONObject state=TaskStore.get(DownloadService.this,id);
                            return state!=null && "downloading".equals(state.optString("status"));
                        }
                        public void onValidator(String validator){TaskStore.patch(DownloadService.this,id,"rangeValidator",validator);}
                        public void onProgress(long count,long size,long speed) {
                            long time=System.currentTimeMillis();
                            if(count<size && time-lastNotification.get()<700)return;
                            lastNotification.set(time);
                            TaskStore.progress(DownloadService.this,id,count,size);
                            TaskStore.patch(DownloadService.this,id,"speed",speed);
                            getSystemService(NotificationManager.class).notify(4201,notification(t.optString("name"),count,size));
                        }
                    });
                if(done) {
                    String state=TaskStore.get(this,id).optString("status");
                    if("canceled".equals(state)){cleanParts(id);return;}
                    if("paused".equals(state))return;
                    publish(temp,t.optString("name"),t.optString("mime"),"application/octet-stream");
                    cleanParts(id);TaskStore.patch(this,id,"status","completed");return;
                }
            }
            long offset=temp.exists()?temp.length():0;
            URL url=new URL(t.getString("url"));
            conn=(HttpsURLConnection)url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);conn.setReadTimeout(10000);
            String cookie=t.optString("cookie"),ua=t.optString("ua"),referer=t.optString("referer");
            if(!cookie.isEmpty())conn.setRequestProperty("Cookie",cookie);
            if(!ua.isEmpty())conn.setRequestProperty("User-Agent",ua);
            if(!referer.isEmpty())conn.setRequestProperty("Referer",referer);
            if(offset>0){
                conn.setRequestProperty("Range","bytes="+offset+"-");
                String validator=t.optString("rangeValidator");
                if(!validator.isEmpty())conn.setRequestProperty("If-Range",validator);
            }
            conn.setRequestProperty("Accept-Encoding","identity");
            int code=conn.getResponseCode();
            if(code==416 && offset>0) {
                // Partial data may already be complete. Recheck from byte zero to avoid corrupt copies.
                conn.disconnect();conn=null;temp.delete();TaskStore.progress(this,id,0,-1);
                TaskStore.patch(this,id,"status","queued");return;
            }
            if(code!=200 && code!=206)throw new Exception("HTTP "+code);
            String type=conn.getContentType();
            if(type!=null && (type.toLowerCase().contains("mpegurl")||type.toLowerCase().contains("dash+xml")))
                throw new Exception("스트리밍 재생목록은 아직 지원하지 않습니다.");
            if(type!=null && type.toLowerCase().contains("text/html"))
                throw new Exception("파일 대신 HTML이 반환되었습니다. 직접 파일 주소가 필요합니다.");
            boolean append=(code==206 && offset>0);
            if(code==206 && offset>0) {
                String range=conn.getHeaderField("Content-Range");
                if(range==null || !range.startsWith("bytes "+offset+"-"))throw new Exception("서버가 잘못된 이어받기 범위를 반환했습니다.");
            }
            if(!append)offset=0;
            if(offset==0) {
                String validator=conn.getHeaderField("ETag");
                if(validator==null || validator.startsWith("W/"))validator=conn.getHeaderField("Last-Modified");
                TaskStore.patch(this,id,"rangeValidator",validator==null?"":validator);
            }
            long total=conn.getContentLengthLong();
            if(total>=0)total+=offset;
            if(!append && temp.exists())temp.delete();
            long done=offset,last=0,checkpoint=System.currentTimeMillis(),checkpointBytes=offset;
            TaskStore.progress(this,id,done,total);
            try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(temp,append)) {
                byte[] buf=new byte[64*1024];int count;
                while((count=in.read(buf))!=-1) {
                    String current=TaskStore.get(this,id).optString("status");
                    if(!"downloading".equals(current))break;
                    out.write(buf,0,count);done+=count;
                    long now=System.currentTimeMillis();
                    if(now-last>=750) {
                        TaskStore.progress(this,id,done,total);
                        long elapsed=Math.max(1,now-checkpoint);
                        TaskStore.patch(this,id,"speed",(done-checkpointBytes)*1000/elapsed);
                        checkpoint=now;checkpointBytes=done;
                        getSystemService(NotificationManager.class).notify(4201,notification(t.optString("name"),done,total));
                        last=now;
                    }
                }
                out.flush();
            }
            TaskStore.progress(this,id,done,total);
            String state=TaskStore.get(this,id).optString("status");
            if("canceled".equals(state)) {cleanParts(id);return;}
            if("paused".equals(state))return;
            if(total>=0 && done!=total)throw new Exception("전송이 끝나기 전에 연결이 종료되었습니다.");
            if(done==0)throw new Exception("빈 파일이 반환되었습니다.");
            publish(temp,t.optString("name"),t.optString("mime"),type);
            cleanParts(id);TaskStore.patch(this,id,"status","completed");
        } catch(Exception e) {
            JSONObject now=TaskStore.get(this,id);
            if(now!=null && "downloading".equals(now.optString("status"))) {
                TaskStore.patch(this,id,"status","failed");
                TaskStore.patch(this,id,"error",e.getMessage()==null?"다운로드 오류":e.getMessage());
            }
            if(now!=null && "canceled".equals(now.optString("status")))cleanParts(id);
        } finally {if(conn!=null)conn.disconnect();}
    }

    /** Captures unencrypted MPEG-TS HLS, including sliding live playlists (stop & save).
     * No encrypted/DRM/FMP4/DASH recording or format transcoding.
     */
    private void performHls(String id, JSONObject initial, File temp) throws Exception {
        String url=initial.getString("url");
        long lastSequence=initial.optLong("lastSeq",-1);
        if(!temp.exists() && lastSequence>=0) {
            lastSequence=-1;TaskStore.patch(this,id,"lastSeq",-1);
        }
        if(temp.exists() && lastSequence<0)temp.delete();
        long done=temp.exists()?temp.length():0;
        long started=System.currentTimeMillis(), lastNotify=started;
        long startBytes=done;
        int emptyPolls=0;
        while(true) {
            JSONObject task=TaskStore.get(this,id);
            if(task==null) return;
            String state=task.optString("status");
            if("canceled".equals(state)){cleanParts(id);return;}
            if("paused".equals(state))return;
            if("finishing".equals(state))break;
            if(!"downloading".equals(state))return;
            HlsPlaylist list=fetchPlaylist(url,task);
            for(int depth=0; !list.variants.isEmpty() && depth<3;depth++) {
                url=list.variants.get(0).url; // Highest advertised bandwidth.
                list=fetchPlaylist(url,task);
            }
            if(!list.variants.isEmpty())throw new Exception("중첩된 HLS 재생목록이 너무 깊습니다.");
            int added=0;
            for(HlsPlaylist.Segment segment:list.segments) {
                if(segment.sequence<=lastSequence)continue;
                JSONObject current=TaskStore.get(this,id);
                if(current==null)return;
                state=current.optString("status");
                if("canceled".equals(state)){cleanParts(id);return;}
                if("paused".equals(state))return;
                if("finishing".equals(state))break;
                if(!"downloading".equals(state))return;
                long before=temp.exists()?temp.length():0;
                try { appendSegment(segment.url,task,temp); }
                catch(Exception error) {
                    try(RandomAccessFile raf=new RandomAccessFile(temp,"rw")){raf.setLength(before);}
                    throw error;
                }
                lastSequence=segment.sequence;
                done=temp.length();added++;
                TaskStore.patch(this,id,"lastSeq",lastSequence);
                TaskStore.progress(this,id,done,-1);
                long now=System.currentTimeMillis(), elapsed=Math.max(1,now-started);
                TaskStore.patch(this,id,"speed",(done-startBytes)*1000/elapsed);
                if(now-lastNotify>=900) {
                    getSystemService(NotificationManager.class).notify(4201,notification(initial.optString("name"),done,-1));
                    lastNotify=now;
                }
            }
            if("finishing".equals(TaskStore.get(this,id).optString("status")))break;
            if(list.endList)break;
            if(added==0)emptyPolls++;else emptyPolls=0;
            // Poll a live/event playlist until the user presses 'stop & save'.
            Thread.sleep(Math.max(2500L,Math.min(12000L,list.targetDuration*750L)));
        }
        JSONObject current=TaskStore.get(this,id);
        if(current==null || "canceled".equals(current.optString("status"))){cleanParts(id);return;}
        if("paused".equals(current.optString("status")))return;
        if(!temp.exists() || temp.length()==0)throw new Exception("저장된 HLS 영상 구간이 없습니다.");
        publish(temp,initial.optString("name"),"video/mp2t","video/mp2t");
        cleanParts(id);TaskStore.patch(this,id,"status","completed");
    }

    private HttpURLConnection hlsConnection(String url, JSONObject task) throws Exception {
        HttpURLConnection conn=(HttpsURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(18000);
        conn.setInstanceFollowRedirects(true);
        String agent=task.optString("ua"),cookie=task.optString("cookie"),referer=task.optString("referer");
        if(!agent.isEmpty())conn.setRequestProperty("User-Agent",agent);
        if(!referer.isEmpty())conn.setRequestProperty("Referer",referer);
        // Never forward credentials to a third-party host specified by a playlist.
        String sourceHost=new URL(task.optString("url")).getHost();
        if(!cookie.isEmpty() && sourceHost.equalsIgnoreCase(new URL(url).getHost()))conn.setRequestProperty("Cookie",cookie);
        return conn;
    }
    private HlsPlaylist fetchPlaylist(String url, JSONObject task) throws Exception {
        HttpURLConnection conn=hlsConnection(url,task);
        try {
            if(conn.getResponseCode()!=200)throw new Exception("HLS 목록 HTTP "+conn.getResponseCode());
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))>=0) {
                    if(out.size()+n>2*1024*1024)throw new Exception("HLS 목록이 2MB를 초과합니다.");
                    out.write(buf,0,n);
                }
                return HlsPlaylist.parse(url,out.toString("UTF-8"));
            }
        } finally {conn.disconnect();}
    }
    private void appendSegment(String url, JSONObject task, File temp) throws Exception {
        HttpURLConnection conn=hlsConnection(url,task);
        try {
            if(conn.getResponseCode()!=200)throw new Exception("HLS 영상 구간 HTTP "+conn.getResponseCode());
            String contentType=conn.getContentType();
            if(contentType!=null && (contentType.toLowerCase().contains("text/html") || contentType.toLowerCase().contains("mpegurl")))
                throw new Exception("영상 구간 대신 웹페이지/재생목록이 반환되었습니다.");
            long segmentBytes=0;
            try(InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(temp,true)) {
                byte[] buffer=new byte[32*1024];int n;
                while((n=in.read(buffer))!=-1) {
                    segmentBytes+=n;
                    if(segmentBytes>120L*1024*1024)throw new Exception("영상 구간 크기 제한 초과");
                    out.write(buffer,0,n);
                }
            }
            if(segmentBytes==0)throw new Exception("빈 HLS 영상 구간");
        } finally {conn.disconnect();}
    }

    @Override public void onTimeout(int startId,int fgsType) {
        if(activeId!=null)TaskStore.patch(this,activeId,"status","paused");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void publish(File src,String name,String givenMime,String responseMime) throws Exception {
        String mime=givenMime==null?"":givenMime;
        if(mime.isEmpty())mime=responseMime==null?"application/octet-stream":responseMime.split(";")[0];
        if(mime.equals("application/octet-stream")) {
            String low=name.toLowerCase();
            if(low.endsWith(".mp4"))mime="video/mp4";
            else if(low.endsWith(".jpg")||low.endsWith(".jpeg"))mime="image/jpeg";
            else if(low.endsWith(".png"))mime="image/png";
        }
        ContentValues values=new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME,TaskStore.safeName(name));
        values.put(MediaStore.Downloads.MIME_TYPE,mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MediaShieldBrowser");
        values.put(MediaStore.Downloads.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new Exception("다운로드 폴더에 저장할 수 없습니다.");
        boolean success=false;
        try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(uri)) {
            if(out==null)throw new Exception("파일 저장 실패");
            byte[] bytes=new byte[64*1024];int n;
            while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
            success=true;
        } finally {
            if(success) {
                ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);
                getContentResolver().update(uri,done,null,null);
            } else getContentResolver().delete(uri,null,null);
        }
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
