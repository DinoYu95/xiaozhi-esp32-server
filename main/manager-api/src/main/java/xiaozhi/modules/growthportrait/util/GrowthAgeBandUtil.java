package xiaozhi.modules.growthportrait.util;

import java.time.LocalDate;
import java.time.Period;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.modules.parent.entity.DeviceChildEntity;

public final class GrowthAgeBandUtil {

    private GrowthAgeBandUtil() {
    }

    public static String resolveAgeBand(DeviceChildEntity child) {
        if (child == null) {
            return "upper";
        }
        String stage = StringUtils.trimToEmpty(child.getAgeStage()).toLowerCase();
        String mapped = mapAgeStage(stage);
        if (mapped != null) {
            return mapped;
        }
        if (ListContains(stage)) {
            return stage;
        }
        if (child.getCurrentGrade() != null) {
            int g = child.getCurrentGrade();
            if (g <= 0) {
                return "preschool";
            }
            if (g <= 2) {
                return "lower";
            }
            if (g <= 6) {
                return "upper";
            }
            return "middle";
        }
        if (child.getBirthday() != null) {
            int years = Period.between(child.getBirthday(), LocalDate.now()).getYears();
            if (years < 6) {
                return "preschool";
            }
            if (years <= 8) {
                return "lower";
            }
            if (years <= 12) {
                return "upper";
            }
            return "middle";
        }
        return "upper";
    }

    /** 档案里常见中文/混写 → 模板 age_band */
    private static String mapAgeStage(String stage) {
        if (StringUtils.isBlank(stage)) {
            return null;
        }
        String s = stage.toLowerCase();
        if (s.contains("幼儿") || s.contains("学龄前") || s.contains("3-6") || s.contains("3～6")
                || s.contains("3~6") || s.contains("preschool")) {
            return "preschool";
        }
        if (s.contains("1-2") || s.contains("一二年级") || s.contains("小低") || s.contains("lower")) {
            return "lower";
        }
        if (s.contains("初中") || s.contains("middle") || s.contains("7-9") || s.contains("中学")) {
            return "middle";
        }
        if (s.contains("小学") || s.contains("3-6年级") || s.contains("upper") || s.contains("小高")) {
            return "upper";
        }
        return null;
    }

    private static boolean ListContains(String stage) {
        return "preschool".equals(stage) || "lower".equals(stage)
                || "upper".equals(stage) || "middle".equals(stage);
    }
}
