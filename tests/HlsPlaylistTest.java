package com.example.bravemediasaver;

final class HlsPlaylistTest {
    private static void require(boolean ok) { if(!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        String vod="#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-MEDIA-SEQUENCE:7\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n#EXTINF:5,\n../b.ts?auth=3\n#EXT-X-ENDLIST";
        HlsPlaylist p=HlsPlaylist.parse("https://example.com/path/list.m3u8",vod);
        require(p.segments.size()==2 && p.endList && p.targetDuration==6);
        require(p.segments.get(0).sequence==7 && p.segments.get(1).sequence==8);
        require(p.segments.get(1).url.equals("https://example.com/b.ts?auth=3"));
        HlsPlaylist m=HlsPlaylist.parse("https://example.com/master.m3u8","#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100\nlow/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=200,RESOLUTION=1920x1080\nhigh/index.m3u8");
        require(m.variants.size()==2 && m.variants.get(0).url.endsWith("high/index.m3u8"));
        require("1920x1080".equals(m.variants.get(0).resolution));
        boolean rejected=false;
        try{HlsPlaylist.parse("https://example.com/a.m3u8","#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\n#EXTINF:2,\na.ts");}catch(Exception e){rejected=true;}
        require(rejected);
        rejected=false;
        try{HlsPlaylist.parse("https://example.com/a.m3u8","#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2,\na.m4s");}catch(Exception e){rejected=true;}
        require(rejected);
        System.out.println("HlsPlaylistTest: 11 checks passed");
    }
}
