package xiaozhi.modules.learning.util;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.modules.learning.entity.KgNodeEntity;

/** 教研入库与掌握地图展示共用的节点类型口径。 */
public final class LearningKgNodeTypeUtil {

    private LearningKgNodeTypeUtil() {}

    /** 教研侧常见别名 → 运行时 SKILL / MISCONCEPTION 等 */
    public static String normalizeTeachingNodeType(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "SKILL";
        }
        String t = raw.trim();
        if ("知识点".equals(t) || "知识节点".equals(t) || t.contains("知识点")) {
            return "SKILL";
        }
        String upper = t.toUpperCase(java.util.Locale.ROOT);
        return switch (upper) {
            case "SKILL", "KNOWLEDGE", "KNOWLEDGE_POINT", "KP", "LEAF", "POINT" -> "SKILL";
            case "MISCONCEPTION", "MIS" -> "MISCONCEPTION";
            case "MODULE", "UNIT", "CHAPTER" -> "MODULE";
            default -> upper;
        };
    }

    /** 掌握地图 / graphReady 只统计可练 SKILL 节点（兼容历史入库的非标准 node_type）。 */
    public static boolean isMasterySkill(KgNodeEntity node) {
        if (node == null || StringUtils.isBlank(node.getNodeType())) {
            return false;
        }
        String t = node.getNodeType().trim();
        if ("SKILL".equalsIgnoreCase(t)) {
            return true;
        }
        String upper = t.toUpperCase(java.util.Locale.ROOT);
        if ("KNOWLEDGE".equals(upper)
                || "KNOWLEDGE_POINT".equals(upper)
                || "KP".equals(upper)
                || "LEAF".equals(upper)
                || "POINT".equals(upper)) {
            return true;
        }
        return t.contains("知识点");
    }
}
