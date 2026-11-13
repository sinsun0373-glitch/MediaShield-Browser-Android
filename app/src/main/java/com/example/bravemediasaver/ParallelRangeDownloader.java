package com.example.bravemediasaver;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional 4-way range engine for large files with strong identity validators.
 * It never combines partially-fetched ranges, and does not require external libraries.
 */
final class ParallelRangeDownloader {
    static final int PARTS=4;
    static final long MIN_BYTES=16L*1024*1024;
    private static final Pattern CONTENT_RANGE=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");
    interface Factory { HttpURLConnection connect(String url) throws Exception; }
    interface Control {
        boolean running();
        void onProgress(long downloaded,long total,long bytesPerSecond);
        void onValidator(String validator);
    }
    private static final class Probe {
        final long total;final String validator;
        Probe(long total,String validator){this.total=total;this.validator=validator;}
    }
    static boolean tryDownload(String url,File directory,String id,File output,String previousValidator,
                               Factory factory,Control callback) throws Exception {
        Probe probe=probe(url,factory);
        if(probe==null || probe.total<MIN_BYTES) return false;
        long[][] plan=RangePlanner.split(probe.total,PARTS);
        File[] chunks=new File[PARTS];
        for(int i=0;i<PARTS;i++)chunks[i]=new File(directory,"dl_"+id+"_"+i+".part");
        if(!probe.validator.equals(previousValidator)) {
            for(File c:chunks)if(c.exists()&&!c.delete())throw new IOException("Cannot reset changed file");
        }
        callback.onValidator(probe.validator);
        AtomicLongArray progress=new AtomicLongArray(PARTS);
        for(int i=0;i<PARTS;i++) {
            long expected=plan[i][1]-plan[i][0]+1;
            if(chunks[i].length()>expected && !chunks[i].delete())throw new IOException("Corrupt existing chunk");
            progress.set(i,chunks[i].length());
        }
        ExecutorService pool=Executors.newFixedThreadPool(PARTS);
        AtomicBoolean aborted=new AtomicBoolean(false);
        List<Future<?>> futures=new ArrayList<>();
        long startTime=System.currentTimeMillis();
        long startingBytes=sum(progress);
        try {
            for(int i=0;i<PARTS;i++) {
                final int idx=i;
                futures.add(pool.submit(()->{
                    try {
                        downloadChunk(url,plan[idx][0],plan[idx][1],probe.total,probe.validator,
                                      chunks[idx],idx,progress,factory,callback,aborted,startTime,startingBytes);
                    } catch(Exception error) {
                        aborted.set(true);
                        throw new RuntimeException(error);
                    }
                }));
            }
            for(Future<?> f:futures) {
                try {f.get();}
                catch(ExecutionException e) {
                    aborted.set(true);
                    for(Future<?> other:futures)other.cancel(true);
                    Throwable cause=e.getCause();
                    if(cause instanceof RuntimeException && cause.getCause()!=null)cause=cause.getCause();
                    if(cause instanceof Exception)throw (Exception)cause;
                    throw new IOException("Part download failed",cause);
                }
            }
        } finally {
            aborted.set(true);
            pool.shutdownNow();
            if(!pool.awaitTermination(20,TimeUnit.SECONDS))throw new IOException("Segment workers did not stop");
        }
        if(!callback.running())throw new InterruptedIOException("Download paused or canceled");
        File joined=new File(directory,"dl_"+id+".joining");
        try(FileOutputStream out=new FileOutputStream(joined,false)) {
            byte[] buf=new byte[128*1024];
            for(int i=0;i<PARTS;i++) {
                long expected=plan[i][1]-plan[i][0]+1;
                if(chunks[i].length()!=expected)throw new EOFException("Incomplete part "+i);
                try(InputStream in=new FileInputStream(chunks[i])) {
                    int count;while((count=in.read(buf))!=-1){
                        if(!callback.running())throw new InterruptedIOException("Download paused or canceled");
                        out.write(buf,0,count);
                    }
                }
            }
        } catch(Exception e) {joined.delete();throw e;}
        if(joined.length()!=probe.total){joined.delete();throw new IOException("Combined length mismatch");}
        if(output.exists()&&!output.delete()){joined.delete();throw new IOException("Cannot replace previous output");}
        if(!joined.renameTo(output)){joined.delete();throw new IOException("Cannot finalize download");}
        for(File c:chunks)c.delete();
        callback.onProgress(probe.total,probe.total,0);
        return true;
    }
    private static long sum(AtomicLongArray p){long total=0;for(int i=0;i<p.length();i++)total+=p.get(i);return total;}
    private static Probe probe(String url,Factory factory) throws Exception {
        HttpURLConnection c=factory.connect(url);
        try {
            c.setRequestProperty("Range","bytes=0-0");
            c.setRequestProperty("Accept-Encoding","identity");
            if(c.getResponseCode()!=206)return null;
            Matcher m=CONTENT_RANGE.matcher(safe(c.getHeaderField("Content-Range")));
            if(!m.matches() || !"0".equals(m.group(1))||!"0".equals(m.group(2)))return null;
            long total=Long.parseLong(m.group(3));
            String etag=safe(c.getHeaderField("ETag"));
            String modified=safe(c.getHeaderField("Last-Modified"));
            String validator=etag.startsWith("W/")?"":etag;
            if(validator.isEmpty())validator=modified;
            if(validator.isEmpty()||total<=0)return null; // no safe cross-session resume
            return new Probe(total,validator);
        } finally {c.disconnect();}
    }
    private static String safe(String s){return s==null?"":s.trim();}
    private static void downloadChunk(String url,long begin,long end,long total,String validator,File target,int idx,
            AtomicLongArray progress,Factory factory,Control callback,AtomicBoolean aborted,
            long started,long initialBytes) throws Exception {
        long previous=target.length(),needed=end-begin+1;
        if(previous==needed)return;
        if(previous>needed)throw new IOException("Invalid saved chunk length");
        HttpURLConnection c=factory.connect(url);
        try {
            c.setRequestProperty("Range","bytes="+(begin+previous)+"-"+end);
            c.setRequestProperty("If-Range",validator);
            c.setRequestProperty("Accept-Encoding","identity");
            if(c.getResponseCode()!=206)throw new IOException("Server denied multi-part range: HTTP "+c.getResponseCode());
            Matcher m=CONTENT_RANGE.matcher(safe(c.getHeaderField("Content-Range")));
            if(!m.matches() || Long.parseLong(m.group(1))!=begin+previous ||
                Long.parseLong(m.group(2))!=end || Long.parseLong(m.group(3))!=total)
                throw new IOException("Server returned unexpected byte range");
            String returnedEtag=safe(c.getHeaderField("ETag"));
            if(validator.startsWith("\"") && !returnedEtag.isEmpty() && !returnedEtag.equals(validator))
                throw new IOException("File changed during download");
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(target,true)) {
                byte[] buf=new byte[64*1024];int n;
                long lastUpdate=0;
                while((n=in.read(buf))!=-1){
                    if(aborted.get()||Thread.currentThread().isInterrupted()||!callback.running())
                        throw new InterruptedIOException("Download paused or canceled");
                    if(progress.get(idx)+n>needed)throw new IOException("Server sent extra bytes");
                    out.write(buf,0,n);progress.addAndGet(idx,n);
                    long now=System.currentTimeMillis();
                    if(now-lastUpdate>800){
                        long all=sum(progress), elapsed=Math.max(1,now-started);
                        callback.onProgress(all,total,Math.max(0,(all-initialBytes)*1000/elapsed));
                        lastUpdate=now;
                    }
                }
            }
            if(target.length()!=needed)throw new EOFException("Incomplete byte-range response");
        } finally {c.disconnect();}
    }
}
