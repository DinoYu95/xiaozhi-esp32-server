package xiaozhi.modules.ota.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 轻量 semver 比较（主.次.修订；预发布后缀仅作次级比较）。
 */
public final class OtaVersionUtils {

    private OtaVersionUtils() {
    }

    public static int compare(String a, String b) {
        SemverParts pa = parse(a);
        SemverParts pb = parse(b);
        for (int i = 0; i < 3; i++) {
            if (pa.core[i] != pb.core[i]) {
                return Integer.compare(pa.core[i], pb.core[i]);
            }
        }
        boolean aPre = StringUtils.isNotBlank(pa.pre);
        boolean bPre = StringUtils.isNotBlank(pb.pre);
        if (aPre && !bPre) {
            return -1;
        }
        if (!aPre && bPre) {
            return 1;
        }
        if (aPre && bPre) {
            return pa.pre.compareTo(pb.pre);
        }
        return 0;
    }

    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    private static SemverParts parse(String raw) {
        String v = StringUtils.defaultIfBlank(raw, "0.0.0").trim();
        int plus = v.indexOf('+');
        if (plus >= 0) {
            v = v.substring(0, plus);
        }
        String pre = "";
        int dash = v.indexOf('-');
        if (dash >= 0) {
            pre = v.substring(dash + 1);
            v = v.substring(0, dash);
        }
        String[] bits = v.split("\\.");
        int[] core = new int[] { 0, 0, 0 };
        for (int i = 0; i < Math.min(3, bits.length); i++) {
            core[i] = parseIntSafe(bits[i]);
        }
        return new SemverParts(core, pre);
    }

    private static int parseIntSafe(String s) {
        String digits = s.replaceAll("[^0-9].*$", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record SemverParts(int[] core, String pre) {
    }
}
