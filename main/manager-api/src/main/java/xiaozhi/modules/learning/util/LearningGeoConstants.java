package xiaozhi.modules.learning.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import xiaozhi.common.exception.RenException;

/** 与 teaching-api geo-regions.json 保持一致 */
public final class LearningGeoConstants {

    private static final Logger log = LoggerFactory.getLogger(LearningGeoConstants.class);

    public static final String SEMESTER_UPPER = "upper";
    public static final String SEMESTER_LOWER = "lower";
    public static final String SEMESTER_ANY = "all";
    public static final String CITY_ANY = "all";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Map<String, String>> PROVINCES;
    private static final List<Map<String, String>> SEMESTERS;
    private static final Map<String, List<Map<String, String>>> CITIES_BY_PROVINCE;

    static {
        List<Map<String, String>> provinces;
        List<Map<String, String>> semesters;
        Map<String, List<Map<String, String>>> cities;
        try (InputStream in = LearningGeoConstants.class.getResourceAsStream("/geo-regions.json")) {
            if (in == null) {
                throw new IllegalStateException("geo-regions.json missing from classpath");
            }
            JsonNode root = JSON.readTree(in);
            provinces = readOptions(root.get("provinces"));
            semesters = readOptions(root.get("semesters"));
            Map<String, List<Map<String, String>>> map = new LinkedHashMap<>();
            JsonNode citiesNode = root.get("citiesByProvince");
            if (citiesNode != null && citiesNode.isObject()) {
                citiesNode.fields().forEachRemaining(e -> map.put(e.getKey(), readOptions(e.getValue())));
            }
            cities = Map.copyOf(map);
        } catch (Exception e) {
            log.error("Failed to load geo-regions.json, using embedded fallback", e);
            provinces = fallbackProvinces();
            semesters = fallbackSemesters();
            cities = fallbackCities();
        }
        PROVINCES = provinces;
        SEMESTERS = semesters;
        CITIES_BY_PROVINCE = cities;
    }

    private LearningGeoConstants() {}

    public static List<Map<String, String>> provinces() {
        return PROVINCES;
    }

    public static List<Map<String, String>> semesters() {
        return SEMESTERS;
    }

    public static Map<String, List<Map<String, String>>> citiesByProvince() {
        return CITIES_BY_PROVINCE;
    }

    public static List<Map<String, String>> citiesOf(String provinceCode) {
        return CITIES_BY_PROVINCE.getOrDefault(normalizeProvince(provinceCode), List.of());
    }

    public static String normalizeProvince(String code) {
        if (code == null || code.isBlank()) {
            return LearningProfileConstants.DEFAULT_PROVINCE;
        }
        String raw = code.trim();
        if ("CN".equalsIgnoreCase(raw) || raw.contains("全国") || raw.contains("未指定")) {
            return LearningProfileConstants.DEFAULT_PROVINCE;
        }
        for (Map<String, String> p : PROVINCES) {
            if (matchesOption(raw, p.get("code"), p.get("label"))) {
                return p.get("code");
            }
        }
        return LearningProfileConstants.normalizeProvince(code);
    }

    public static String normalizeSemester(String semester) {
        if (semester == null || semester.isBlank() || SEMESTER_ANY.equalsIgnoreCase(semester.trim())) {
            return SEMESTER_UPPER;
        }
        String s = semester.trim();
        if ("上册".equals(s) || SEMESTER_UPPER.equalsIgnoreCase(s) || "1".equals(s)) {
            return SEMESTER_UPPER;
        }
        if ("下册".equals(s) || SEMESTER_LOWER.equalsIgnoreCase(s) || "2".equals(s)) {
            return SEMESTER_LOWER;
        }
        for (Map<String, String> opt : SEMESTERS) {
            if (matchesOption(s, opt.get("code"), opt.get("label"))) {
                return opt.get("code");
            }
        }
        throw new RenException("上下册无效，请选 upper/lower 或 上册/下册");
    }

    /** 接受 code（shandong_qingdao）或中文 label（青岛市、青岛） */
    public static String normalizeCity(String provinceCode, String cityCode) {
        String province = normalizeProvince(provinceCode);
        if (cityCode == null || cityCode.isBlank()) {
            return province + "_all";
        }
        String raw = cityCode.trim();
        List<Map<String, String>> cities = citiesOf(province);
        for (Map<String, String> c : cities) {
            if (matchesOption(raw, c.get("code"), c.get("label"))) {
                return c.get("code");
            }
        }
        String compact = compactPlaceName(raw);
        for (Map<String, String> c : cities) {
            String labelCompact = compactPlaceName(c.get("label"));
            if (compact.equals(labelCompact) || labelCompact.contains(compact) || compact.contains(labelCompact)) {
                return c.get("code");
            }
        }
        if (raw.contains("_")) {
            log.warn("Unknown city code {} for province {}, fallback to {}_all", raw, province, province);
            return province + "_all";
        }
        throw new RenException("城市与省份不匹配，请传 cityCode（如 shandong_qingdao），勿只传中文名");
    }

    public static void validateCity(String provinceCode, String cityCode) {
        normalizeCity(provinceCode, cityCode);
    }

    public static String semesterLabel(String semester) {
        String s = normalizeSemester(semester);
        for (Map<String, String> opt : SEMESTERS) {
            if (s.equals(opt.get("code"))) {
                return opt.get("label");
            }
        }
        return s;
    }

    private static boolean matchesOption(String raw, String code, String label) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        if (code != null && raw.equalsIgnoreCase(code)) {
            return true;
        }
        if (label != null && raw.equals(label)) {
            return true;
        }
        return label != null && compactPlaceName(raw).equals(compactPlaceName(label));
    }

    private static String compactPlaceName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim()
                .replace("省", "")
                .replace("市", "")
                .replace("自治区", "")
                .replace("壮族", "")
                .replace("回族", "")
                .replace("维吾尔", "")
                .replace("特别行政区", "")
                .toLowerCase(Locale.ROOT);
    }

    private static List<Map<String, String>> readOptions(JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (JsonNode n : arr) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", n.path("code").asText(""));
            m.put("label", n.path("label").asText(""));
            out.add(m);
        }
        return List.copyOf(out);
    }

    private static List<Map<String, String>> fallbackProvinces() {
        List<Map<String, String>> out = new ArrayList<>();
        out.add(option("CN", "全国通用"));
        out.add(option("shandong", "山东省"));
        out.add(option("beijing", "北京市"));
        out.add(option("shanghai", "上海市"));
        out.add(option("guangdong", "广东省"));
        out.add(option("zhejiang", "浙江省"));
        out.add(option("jiangsu", "江苏省"));
        return List.copyOf(out);
    }

    private static List<Map<String, String>> fallbackSemesters() {
        return List.of(option(SEMESTER_UPPER, "上册"), option(SEMESTER_LOWER, "下册"));
    }

    private static Map<String, List<Map<String, String>>> fallbackCities() {
        Map<String, List<Map<String, String>>> map = new LinkedHashMap<>();
        map.put("CN", List.of(option("CN_all", "全国")));
        map.put(
                "shandong",
                List.of(
                        option("shandong_all", "全省通用"),
                        option("shandong_jinan", "济南市"),
                        option("shandong_qingdao", "青岛市")));
        return Map.copyOf(map);
    }

    private static Map<String, String> option(String code, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        return m;
    }
}
