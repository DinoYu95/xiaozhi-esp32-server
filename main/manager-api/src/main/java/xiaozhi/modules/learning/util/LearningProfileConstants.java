package xiaozhi.modules.learning.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与教研 teaching-web/app.js 中 REGIONS / TEXTBOOKS / PROVINCE_API_MAP 保持一致。
 */
public final class LearningProfileConstants {

    private LearningProfileConstants() {}

    public static final String DEFAULT_PROVINCE = "CN";
    public static final String DEFAULT_TEXTBOOK = "generic";

    public static final Map<String, String> TEXTBOOKS = linked(
            "generic", "课标通用",
            "pep", "人教版",
            "bnu", "北师大版",
            "su", "苏教版",
            "hs", "沪教版",
            "yj", "粤教版");

    /** UI region key → API province_code */
    public static final Map<String, String> PROVINCE_API_MAP = linked(
            "national", "CN",
            "beijing", "beijing",
            "shanghai", "shanghai",
            "jiangsu", "jiangsu",
            "zhejiang", "zhejiang",
            "guangdong", "guangdong",
            "shandong", "shandong",
            "sichuan", "sichuan",
            "other", "other");

    public static final List<Map<String, String>> PROVINCE_OPTIONS = List.of(
            option("CN", "全国 / 未指定省"),
            option("beijing", "北京"),
            option("shanghai", "上海"),
            option("jiangsu", "江苏"),
            option("zhejiang", "浙江"),
            option("guangdong", "广东"),
            option("shandong", "山东"),
            option("sichuan", "四川"),
            option("other", "其它"));

    public static String normalizeProvince(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT_PROVINCE;
        }
        String raw = code.trim();
        if ("CN".equalsIgnoreCase(raw) || "全国".equals(raw) || raw.contains("未指定")) {
            return DEFAULT_PROVINCE;
        }
        for (Map<String, String> opt : PROVINCE_OPTIONS) {
            if (raw.equalsIgnoreCase(opt.get("code")) || raw.equals(opt.get("label"))) {
                return opt.get("code");
            }
        }
        for (Map.Entry<String, String> e : PROVINCE_API_MAP.entrySet()) {
            if (e.getKey().equalsIgnoreCase(raw)) {
                return e.getValue();
            }
        }
        if ("北京".equals(raw) || raw.startsWith("北京")) {
            return "beijing";
        }
        if ("上海".equals(raw) || raw.startsWith("上海")) {
            return "shanghai";
        }
        if ("江苏".equals(raw) || raw.startsWith("江苏")) {
            return "jiangsu";
        }
        if ("浙江".equals(raw) || raw.startsWith("浙江")) {
            return "zhejiang";
        }
        if ("广东".equals(raw) || raw.startsWith("广东")) {
            return "guangdong";
        }
        if ("山东".equals(raw) || raw.startsWith("山东")) {
            return "shandong";
        }
        if ("四川".equals(raw) || raw.startsWith("四川")) {
            return "sichuan";
        }
        return raw.toLowerCase(java.util.Locale.ROOT);
    }

    public static String normalizeTextbook(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT_TEXTBOOK;
        }
        String c = code.trim().toLowerCase();
        return TEXTBOOKS.containsKey(c) ? c : DEFAULT_TEXTBOOK;
    }

    /** 兼容旧字段 textbookSeries：若为已知 code 则映射，否则 generic */
    public static String textbookFromLegacySeries(String textbookSeries, String textbookEdition) {
        if (textbookEdition != null && !textbookEdition.isBlank()) {
            return normalizeTextbook(textbookEdition);
        }
        if (textbookSeries == null || textbookSeries.isBlank()) {
            return DEFAULT_TEXTBOOK;
        }
        String s = textbookSeries.trim().toLowerCase();
        if (TEXTBOOKS.containsKey(s)) {
            return s;
        }
        if (s.contains("人教")) {
            return "pep";
        }
        if (s.contains("北师大")) {
            return "bnu";
        }
        if (s.contains("苏教")) {
            return "su";
        }
        if (s.contains("沪教")) {
            return "hs";
        }
        if (s.contains("粤教")) {
            return "yj";
        }
        return DEFAULT_TEXTBOOK;
    }

    public static String schoolLevelFromGrade(Integer grade) {
        if (grade == null || grade <= 0) {
            return "PRIMARY";
        }
        if (grade <= 6) {
            return "PRIMARY";
        }
        if (grade <= 9) {
            return "MIDDLE";
        }
        return "HIGH";
    }

    private static Map<String, String> linked(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, String> option(String code, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        return m;
    }
}
