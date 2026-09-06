package xiaozhi.modules.learning.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.modules.growthportrait.util.GrowthAgeBandUtil;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.vo.DeviceChildVO;

/**
 * 孩子档案年级选项：含「幼小衔接 3-6岁」→ 成长星图 preschool。
 */
public final class ChildGradeOptionsUtil {

    public static final int GRADE_PRESCHOOL = 0;
    public static final String PRESCHOOL_AGE_STAGE = "幼小衔接 3-6岁";
    public static final String PRESCHOOL_LABEL = "幼小衔接 3-6岁";

    private ChildGradeOptionsUtil() {
    }

    /** 档案年级 Picker 下拉（与 profile-options 接口一致） */
    public static List<Map<String, Object>> profileGradeOptions() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(gradeOption(GRADE_PRESCHOOL, PRESCHOOL_LABEL, PRESCHOOL_AGE_STAGE, "preschool", "preschool"));
        for (int g = 1; g <= 6; g++) {
            String growth = g <= 2 ? "lower" : "upper";
            list.add(gradeOption(g, "小学" + g + "年级", null, growth, "primary"));
        }
        for (int g = 7; g <= 9; g++) {
            list.add(gradeOption(g, "初中" + (g - 6) + "年级", null, "middle", "middle"));
        }
        return list;
    }

    private static Map<String, Object> gradeOption(
            int value, String label, String ageStage, String growthAgeBand, String kind) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("label", label);
        if (ageStage != null) {
            m.put("ageStage", ageStage);
        }
        m.put("growthAgeBand", growthAgeBand);
        m.put("kind", kind);
        return m;
    }

    /** 保存档案时：幼小衔接自动补全 ageStage；升小学后清掉残留学前 ageStage */
    public static void normalizeGradeProfile(DeviceChildEntity entity) {
        if (entity == null || entity.getCurrentGrade() == null) {
            return;
        }
        if (entity.getCurrentGrade() <= GRADE_PRESCHOOL) {
            entity.setCurrentGrade(GRADE_PRESCHOOL);
            if (StringUtils.isBlank(entity.getAgeStage())) {
                entity.setAgeStage(PRESCHOOL_AGE_STAGE);
            }
            return;
        }
        if (StringUtils.isNotBlank(entity.getAgeStage())) {
            String s = entity.getAgeStage().toLowerCase();
            if (s.contains("幼小衔接") || s.contains("学前") || s.contains("幼儿") || s.contains("preschool")) {
                entity.setAgeStage(null);
            }
        }
    }

    public static boolean isGradeConfigured(DeviceChildEntity child) {
        return child != null && child.getCurrentGrade() != null;
    }

    public static boolean isPreschoolProfile(DeviceChildEntity child) {
        if (child == null) {
            return false;
        }
        if (child.getCurrentGrade() != null && child.getCurrentGrade() >= 1) {
            return false;
        }
        if (child.getCurrentGrade() != null && child.getCurrentGrade() <= GRADE_PRESCHOOL) {
            return true;
        }
        return "preschool".equals(GrowthAgeBandUtil.resolveAgeBand(child));
    }

    public static String resolveGradeLabel(DeviceChildEntity child) {
        if (child == null || child.getCurrentGrade() == null) {
            return null;
        }
        int g = child.getCurrentGrade();
        if (g <= GRADE_PRESCHOOL) {
            return PRESCHOOL_LABEL;
        }
        if (g >= 1 && g <= 6) {
            return "小学" + g + "年级";
        }
        if (g >= 7 && g <= 9) {
            return "初中" + (g - 6) + "年级";
        }
        return g + "年级";
    }

    public static String resolveGrowthAgeBand(DeviceChildEntity child) {
        return GrowthAgeBandUtil.resolveAgeBand(child);
    }

    public static void enrichChildVo(DeviceChildEntity entity, DeviceChildVO vo) {
        if (entity == null || vo == null) {
            return;
        }
        vo.setGradeLabel(resolveGradeLabel(entity));
        vo.setGrowthAgeBand(resolveGrowthAgeBand(entity));
        vo.setPreschoolProfile(isPreschoolProfile(entity));
        vo.setGradeConfigured(isGradeConfigured(entity));
    }
}
