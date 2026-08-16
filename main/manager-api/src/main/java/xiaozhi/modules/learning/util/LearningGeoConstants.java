package xiaozhi.modules.learning.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import xiaozhi.common.exception.RenException;

/** 与 teaching-api geo-regions.json 保持一致 */
public final class LearningGeoConstants {

    public static final String SEMESTER_UPPER = "upper";
    public static final String SEMESTER_LOWER = "lower";
    public static final String SEMESTER_ANY = "all";
    public static final String CITY_ANY = "all";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Map<String, String>> PROVINCES;
    private static final List<Map<String, String>> SEMESTERS;
    private static final Map<String, List<Map<String, String>>> CITIES_BY_PROVINCE;

    static {
        try (InputStream in = LearningGeoConstants.class.getResourceAsStream("/geo-regions.json")) {
            if (in == null) {
                throw new IllegalStateException("geo-regions.json missing");
            }
            JsonNode root = JSON.readTree(in);
            PROVINCES = readOptions(root.get("provinces"));
            SEMESTERS = readOptions(root.get("semesters"));
            Map<String, List<Map<String, String>>> map = new LinkedHashMap<>();
            JsonNode cities = root.get("citiesByProvince");
            if (cities != null && cities.isObject()) {
                cities.fields().forEachRemaining(e -> map.put(e.getKey(), readOptions(e.getValue())));
            }
            CITIES_BY_PROVINCE = Map.copyOf(map);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
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
        throw new RenException("上下册无效");
    }

    public static String normalizeCity(String provinceCode, String cityCode) {
        String province = normalizeProvince(provinceCode);
        if (cityCode == null || cityCode.isBlank()) {
            return province + "_all";
        }
        String city = cityCode.trim();
        for (Map<String, String> c : citiesOf(province)) {
            if (city.equalsIgnoreCase(c.get("code"))) {
                return c.get("code");
            }
        }
        throw new RenException("城市与省份不匹配");
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
}
