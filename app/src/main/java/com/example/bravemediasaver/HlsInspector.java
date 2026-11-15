package com.example.bravemediasaver;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;

/** Reads a playlist on a worker thread for user-initiated quality selection. */
final class HlsInspector {
    static HlsPlaylist load(String url, String agent, String referer, String cookie) throws Exception {
        HttpURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(16000);
            if(agent!=null && !agent.isEmpty())connection.setRequestProperty("User-Agent",agent);
            if(referer!=null && (referer.startsWith("https://")||referer.startsWith("http://")))
                connection.setRequestProperty("Referer",referer);
            if(cookie!=null && !cookie.isEmpty())connection.setRequestProperty("Cookie",cookie);
            if(connection.getResponseCode()!=200)throw new Exception("재생목록 HTTP "+connection.getResponseCode());
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] bytes=new byte[8192];int count;
                while((count=in.read(bytes))!=-1){
                    if(out.size()+count>2*1024*1024)throw new Exception("HLS 재생목록이 너무 큽니다.");
                    out.write(bytes,0,count);
                }
                return HlsPlaylist.parse(url,out.toString("UTF-8"));
            }
        } finally {connection.disconnect();}
    }
}
