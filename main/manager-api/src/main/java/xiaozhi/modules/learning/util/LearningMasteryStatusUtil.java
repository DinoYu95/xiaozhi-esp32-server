package xiaozhi.modules.learning.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 掌握度展示 status 枚举（与小程序约定）。
 */
public final class LearningMasteryStatusUtil {

    public static final String UNOBSERVED = "unobserved";
    public static final String NEED_CONSOLIDATE = "need_consolidate";
    public static final String PRACTICING = "practicing";
    public static final String STABLE = "stable";

    private LearningMasteryStatusUtil() {
    }

    public static String resolveStatus(Integer evidenceCount, java.math.BigDecimal pMastery) {
        if (evidenceCount == null || evidenceCount <= 0) {
            return UNOBSERVED;
        }
        java.math.BigDecimal p = pMastery != null ? pMastery : new java.math.BigDecimal("0.50");
        if (p.compareTo(new java.math.BigDecimal("0.45")) < 0) {
            return NEED_CONSOLIDATE;
        }
        if (p.compareTo(new java.math.BigDecimal("0.75")) < 0) {
            return PRACTICING;
        }
        return STABLE;
    }

    public static String moduleKeyFromSkillCode(String code) {
        if (StringUtils.isBlank(code)) {
            return "OTHER";
        }
        String[] parts = code.split("\\.");
        if (parts.length >= 3) {
            return parts[2].trim().toUpperCase();
        }
        return "OTHER";
    }

    /** 从节点 code 首段推断学科（MATH / CHN / ENG）。 */
    public static String subjectFromSkillCode(String code) {
        if (StringUtils.isBlank(code)) {
            return "math";
        }
        String head = code.split("\\.", 2)[0].trim().toUpperCase();
        return switch (head) {
            case "CHN" -> "chinese";
            case "ENG" -> "english";
            default -> "math";
        };
    }

    /** 从 MATH.G1.NUM.001 解析年级 G 段。 */
    public static int gradeFromSkillCode(String code) {
        if (StringUtils.isBlank(code)) {
            return 0;
        }
        String[] parts = code.split("\\.");
        if (parts.length >= 2 && parts[1].length() > 1 && parts[1].charAt(0) == 'G') {
            try {
                return Integer.parseInt(parts[1].substring(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public static String subjectLabel(String subject) {
        return switch (StringUtils.defaultIfBlank(subject, "math").toLowerCase()) {
            case "math" -> "数学";
            case "chinese" -> "语文";
            case "english" -> "英语";
            case "science" -> "科学";
            default -> subject;
        };
    }
}
