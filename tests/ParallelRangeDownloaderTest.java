package com.example.bravemediasaver;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class ParallelRangeDownloaderTest {
    private static final byte[] BODY=new byte[17*1024*1024+349];
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args) throws Exception {
        for(int i=0;i<BODY.length;i++)BODY[i]=(byte)((i*173L+91)>>3);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/video", exchange->{
            try {
                String range=exchange.getRequestHeaders().getFirst("Range");
                String ifRange=exchange.getRequestHeaders().getFirst("If-Range");
                exchange.getResponseHeaders().set("ETag","\"video-1\"");
                if(range==null){exchange.sendResponseHeaders(200,BODY.length);exchange.getResponseBody().write(BODY);return;}
                if(ifRange!=null && !ifRange.equals("\"video-1\"")){exchange.sendResponseHeaders(200,BODY.length);return;}
                String[] splits=range.substring("bytes=".length()).split("-",-1);
                int start=Integer.parseInt(splits[0]);
                int end=splits[1].isEmpty()?BODY.length-1:Integer.parseInt(splits[1]);
                exchange.getResponseHeaders().set("Content-Range","bytes "+start+"-"+end+"/"+BODY.length);
                exchange.sendResponseHeaders(206,end-start+1);
                exchange.getResponseBody().write(BODY,start,end-start+1);
            } catch(Exception error){throw new RuntimeException(error);}finally{exchange.close();}
        });
        server.createContext("/no-range", exchange->{
            try {exchange.sendResponseHeaders(200,0);}finally{exchange.close();}
        });
        server.start();
        File dir=Files.createTempDirectory("media-v6-test").toFile();
        String url="http://127.0.0.1:"+server.getAddress().getPort()+"/video";
        AtomicReference<String> validator=new AtomicReference<>("");
        AtomicBoolean running=new AtomicBoolean(true);
        ParallelRangeDownloader.Factory factory=u->{HttpURLConnection conn=(HttpURLConnection)new URL(u).openConnection();conn.setConnectTimeout(10000);conn.setReadTimeout(10000);return conn;};
        ParallelRangeDownloader.Control ctl=new ParallelRangeDownloader.Control(){
            public boolean running(){return running.get();}
            public void onProgress(long downloaded,long total,long speed) {if(downloaded>300000)running.set(false);}
            public void onValidator(String value){validator.set(value);}
        };
        File result=new File(dir,"full.part");
        try {
            boolean canceled=false;
            try{ParallelRangeDownloader.tryDownload(url,dir,"example",result,"",factory,ctl);}catch(Exception expected){canceled=true;}
            check(canceled,"should stop on pause");
            running.set(true);
            ParallelRangeDownloader.Control finishCtl=new ParallelRangeDownloader.Control(){
                public boolean running(){return true;}
                public void onProgress(long downloaded,long total,long speed){}
                public void onValidator(String v){validator.set(v);}
            };
            check(ParallelRangeDownloader.tryDownload(url,dir,"example",result,validator.get(),factory,finishCtl),"range success");
            check(result.length()==BODY.length,"correct output length");
            check(Arrays.equals(Files.readAllBytes(result.toPath()),BODY),"whole file correct");
            String noRange="http://127.0.0.1:"+server.getAddress().getPort()+"/no-range";
            check(!ParallelRangeDownloader.tryDownload(noRange,dir,"other",new File(dir,"other.part"),"",factory,finishCtl),"fall back without range support");
            System.out.println("ParallelRangeDownloaderTest: pause, resume, 4-way content, no-range fallback passed");
        } finally {
            server.stop(0);
            File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();
        }
    }
}
