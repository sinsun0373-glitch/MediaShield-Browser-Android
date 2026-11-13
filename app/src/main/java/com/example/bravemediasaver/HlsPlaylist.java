package com.example.bravemediasaver;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal, deliberately conservative HLS MPEG-TS playlist parser.
 * No encryption, DRM, byte-range playlists, discontinuities, or fragmented MP4 muxing.
 */
final class HlsPlaylist {
    static final class Segment {
        final long sequence;
        final String url;
        Segment(long sequence, String url) { this.sequence = sequence; this.url = url; }
    }
    static final class Variant {
        final int bandwidth;
        final String url;
        final String resolution;
        Variant(int bandwidth, String url, String resolution) { this.bandwidth = bandwidth; this.url = url; this.resolution = resolution; }
    }
    final List<Segment> segments;
    final List<Variant> variants;
    final boolean endList;
    final int targetDuration;

    private HlsPlaylist(List<Segment> seg, List<Variant> vars, boolean endList, int targetDuration) {
        this.segments = Collections.unmodifiableList(seg);
        this.variants = Collections.unmodifiableList(vars);
        this.endList = endList;
        this.targetDuration = targetDuration;
    }

    static HlsPlaylist parse(String baseUrl, String playlist) throws Exception {
        if (playlist == null || !playlist.trim().startsWith("#EXTM3U"))
            throw new Exception("올바른 HLS 재생목록이 아닙니다.");
        List<Segment> segments = new ArrayList<>();
        List<Variant> variants = new ArrayList<>();
        String[] lines = playlist.replace("\r", "").split("\n");
        boolean end = false, expectVariant = false;
        int pendingBw = 0, target = 4;
        String pendingResolution="";
        long seq = 0;
        Pattern bw = Pattern.compile("(?:^|[:,])BANDWIDTH=(\\d+)");
        Pattern resolution=Pattern.compile("(?:^|,)RESOLUTION=(\\d+x\\d+)");
        for (String raw : lines) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("#EXT-X-SESSION-KEY:"))
                throw new Exception("세션 암호화 HLS는 지원하지 않습니다.");
            if (s.startsWith("#EXT-X-KEY:")) {
                String attrs = s.substring("#EXT-X-KEY:".length()).toUpperCase(Locale.ROOT);
                if (!Pattern.compile("(?:^|,)METHOD=NONE(?:,|$)").matcher(attrs).find())
                    throw new Exception("암호화/DRM 스트림은 지원하지 않습니다.");
            }
            if (s.startsWith("#EXT-X-MAP:") || s.startsWith("#EXT-X-PART:") || s.startsWith("#EXT-X-BYTERANGE:") || s.startsWith("#EXT-X-DISCONTINUITY"))
                throw new Exception("이 HLS 형식(fMP4/부분전송/불연속)은 지원하지 않습니다.");
            if (s.startsWith("#EXT-X-STREAM-INF:")) {
                expectVariant = true;
                Matcher m = bw.matcher(s);
                pendingBw = m.find() ? Integer.parseInt(m.group(1)) : 0;
                Matcher r=resolution.matcher(s);
                pendingResolution=r.find()?r.group(1):"";
                continue;
            }
            if (s.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                seq = Long.parseLong(s.substring("#EXT-X-MEDIA-SEQUENCE:".length()).trim());
            }
            if (s.startsWith("#EXT-X-TARGETDURATION:")) {
                target = Math.max(2, Math.min(30, Integer.parseInt(s.substring("#EXT-X-TARGETDURATION:".length()).trim())));
            }
            if (s.equals("#EXT-X-ENDLIST")) end = true;
            if (s.startsWith("#")) continue;
            String absolute = URI.create(baseUrl).resolve(s).toString();
            URI url = URI.create(absolute);
            if (!expectVariant && url.getPath()!=null && url.getPath().toLowerCase(Locale.ROOT).matches(".*\\.(?:m4s|cmfv|cmfa)$"))
                throw new Exception("fMP4 HLS 영상 조각은 지원하지 않습니다.");
            if (!"https".equalsIgnoreCase(url.getScheme()) && !"http".equalsIgnoreCase(url.getScheme()))
                throw new Exception("지원하지 않는 세그먼트 주소입니다.");
            if (expectVariant) {
                variants.add(new Variant(pendingBw, absolute, pendingResolution));
                expectVariant = false;
            } else {
                segments.add(new Segment(seq++, absolute));
            }
            if (segments.size() > 50000 || variants.size() > 100) throw new Exception("재생목록이 너무 큽니다.");
        }
        if (!variants.isEmpty()) variants.sort((a,b)->Integer.compare(b.bandwidth,a.bandwidth));
        if (segments.isEmpty() && variants.isEmpty()) throw new Exception("HLS 미디어 주소가 없습니다.");
        return new HlsPlaylist(segments, variants, end, target);
    }
}
