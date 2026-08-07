package xiaozhi.modules.learning.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * SKILL code 第三段 → 家长可读模块名。
 */
public final class LearningMasteryModuleLabels {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("NUM", "数的认识");
        LABELS.put("ADD", "加法");
        LABELS.put("SUB", "减法");
        LABELS.put("MUL", "乘法");
        LABELS.put("DIV", "除法");
        LABELS.put("WORD", "应用题");
        LABELS.put("GEO", "图形与几何");
        LABELS.put("MEA", "测量");
        LABELS.put("TIME", "时间");
        LABELS.put("FRA", "分数");
        LABELS.put("DATA", "数据");
        LABELS.put("OTHER", "其它");
        // 语文 / 英语试点模块键（code 第三段）
        LABELS.put("PY", "拼音");
        LABELS.put("WR", "书写");
        LABELS.put("READ", "阅读");
        LABELS.put("VOC", "字词");
        LABELS.put("WRITE", "写话");
        LABELS.put("PH", "语音");
        LABELS.put("GRM", "语法");
        LABELS.put("LIS", "听力");
        LABELS.put("SPE", "口语");
    }

    /** 模块 Tab 排序（未列出的排在后面按字母） */
    private static final List<String> ORDER = List.of(
            "NUM", "ADD", "SUB", "MUL", "DIV", "WORD", "FRA", "GEO", "MEA", "TIME", "DATA", "OTHER");

    private LearningMasteryModuleLabels() {
    }

    public static String labelFor(String moduleKey) {
        String k = StringUtils.defaultIfBlank(moduleKey, "OTHER").toUpperCase();
        return LABELS.getOrDefault(k, k);
    }

    public static int sortIndex(String moduleKey) {
        String k = StringUtils.defaultIfBlank(moduleKey, "OTHER").toUpperCase();
        int i = ORDER.indexOf(k);
        return i >= 0 ? i : ORDER.size() + k.hashCode();
    }
}
