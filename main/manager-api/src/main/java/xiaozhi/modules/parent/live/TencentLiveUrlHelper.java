package xiaozhi.modules.parent.live;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

/**
 * 腾讯云直播推流/播放 URL 生成（推流鉴权 MD5）。
 * 文档：https://cloud.tencent.com/document/product/267/32735
 */
public final class TencentLiveUrlHelper {

    private TencentLiveUrlHelper() {
    }

    public static String buildStreamName(String deviceId, String sessionNo) {
        String dev = normalizeForStream(deviceId);
        String sn = StringUtils.defaultString(sessionNo, "").trim();
        if (sn.startsWith("live_")) {
            sn = sn.substring(5);
        }
        return dev + "_" + sn;
    }

    public static String buildPushUrl(
            String pushDomain,
            String appName,
            String streamName,
            String pushAuthKey,
            long expireEpochSec) {
        String txTime = Long.toHexString(expireEpochSec).toUpperCase(Locale.ROOT);
        String txSecret = md5(pushAuthKey + streamName + txTime);
        String domain = trimPushDomain(pushDomain);
        String app = StringUtils.defaultIfBlank(appName, "parent");
        return String.format(
                "rtmp://%s/%s/%s?txSecret=%s&txTime=%s",
                domain, app, streamName, txSecret, txTime);
    }

    public static String buildPlayFlvUrl(String playDomain, String appName, String streamName) {
        return String.format(
                "https://%s/%s/%s.flv",
                trimPlayDomain(playDomain),
                StringUtils.defaultIfBlank(appName, "parent"),
                streamName);
    }

    public static String buildPlayHlsUrl(String playDomain, String appName, String streamName) {
        return String.format(
                "https://%s/%s/%s.m3u8",
                trimPlayDomain(playDomain),
                StringUtils.defaultIfBlank(appName, "parent"),
                streamName);
    }

    public static long defaultPushExpireEpochSec(int maxDurationSec, int bufferSec) {
        return Instant.now().getEpochSecond() + Math.max(60, maxDurationSec) + Math.max(60, bufferSec);
    }

    private static String normalizeForStream(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return "unknown";
        }
        return deviceId.trim().replace(":", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String trimPushDomain(String domain) {
        String d = StringUtils.trimToEmpty(domain);
        d = d.replace("rtmp://", "").replace("https://", "").replace("http://", "");
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }

    private static String trimPlayDomain(String domain) {
        String d = StringUtils.trimToEmpty(domain);
        d = d.replace("https://", "").replace("http://", "");
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }
}
