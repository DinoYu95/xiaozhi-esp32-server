package xiaozhi.modules.ota.util;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * 灰度命中：hash(mac)%100 &lt; rollout 或 MAC 在白名单池 / extra 列表。
 */
public final class OtaRolloutMatcher {

    private OtaRolloutMatcher() {
    }

    /**
     * 与 DevOps 本地实现一致的稳定哈希，便于灰度结果可复现。
     */
    public static int hashMac(String mac) {
        int h = 0;
        String s = normalizeMac(mac).replace(":", "");
        for (int i = 0; i < s.length(); i++) {
            h = (h * 31 + s.charAt(i)) % 100;
        }
        return h;
    }

    public static String normalizeMac(String mac) {
        return StringUtils.trimToEmpty(mac).toLowerCase(Locale.ROOT);
    }

    public static boolean hitRollout(String mac, int rolloutPercent) {
        int percent = Math.max(0, Math.min(100, rolloutPercent));
        return hashMac(mac) < percent;
    }

    public static boolean isEligible(String mac, int rolloutPercent, Collection<String> whitelistMacs) {
        String macL = normalizeMac(mac);
        if (macL.isEmpty()) {
            return false;
        }
        if (whitelistMacs != null) {
            for (String w : whitelistMacs) {
                if (macL.equals(normalizeMac(w))) {
                    return true;
                }
            }
        }
        return hitRollout(macL, rolloutPercent);
    }

    public static boolean channelCanSeeRelease(String deviceChannel, String releaseChannel) {
        String device = StringUtils.defaultIfBlank(deviceChannel, "stable").toLowerCase(Locale.ROOT);
        String release = StringUtils.defaultIfBlank(releaseChannel, "stable").toLowerCase(Locale.ROOT);
        if ("stable".equals(release)) {
            return "stable".equals(device) || "beta".equals(device);
        }
        if ("beta".equals(release)) {
            return "beta".equals(device);
        }
        return false;
    }

    public static String[] visibleChannels(String deviceChannel) {
        if ("beta".equalsIgnoreCase(StringUtils.trimToEmpty(deviceChannel))) {
            return new String[] { "beta", "stable" };
        }
        return new String[] { "stable" };
    }

    public static Set<String> normalizeMacSet(Collection<String> macs) {
        return macs == null ? Set.of()
                : macs.stream().map(OtaRolloutMatcher::normalizeMac).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet());
    }
}
