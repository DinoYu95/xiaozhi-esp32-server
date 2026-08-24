package xiaozhi.modules.learning.util;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.common.exception.RenException;
import xiaozhi.modules.parent.entity.DeviceChildEntity;

public final class LearningChildProfileUtil {

    private LearningChildProfileUtil() {}

    public static String resolveCity(DeviceChildEntity child) {
        if (child == null || child.getCityCode() == null || child.getCityCode().isBlank()) {
            String province = resolveProvince(child);
            return province + "_all";
        }
        return LearningGeoConstants.normalizeCity(resolveProvince(child), child.getCityCode());
    }

    public static String resolveSemester(DeviceChildEntity child) {
        if (child == null || child.getSemester() == null || child.getSemester().isBlank()) {
            return LearningGeoConstants.SEMESTER_UPPER;
        }
        return LearningGeoConstants.normalizeSemester(child.getSemester());
    }

    public static String resolveProvince(DeviceChildEntity child) {
        if (child == null) {
            return LearningProfileConstants.DEFAULT_PROVINCE;
        }
        return LearningProfileConstants.normalizeProvince(child.getProvinceCode());
    }

    public static String resolveTextbook(DeviceChildEntity child) {
        if (child == null) {
            return LearningProfileConstants.DEFAULT_TEXTBOOK;
        }
        return LearningProfileConstants.textbookFromLegacySeries(
                child.getTextbookSeries(), child.getTextbookEdition());
    }

    public static int resolveChildMaxGrade(DeviceChildEntity child) {
        if (child != null && child.getCurrentGrade() != null) {
            if (child.getCurrentGrade() <= 0) {
                return 0;
            }
            return child.getCurrentGrade();
        }
        return 1;
    }

    public static int clampGraphGrade(DeviceChildEntity child, Integer requestedGrade) {
        int max = resolveChildMaxGrade(child);
        if (max <= 0) {
            return 0;
        }
        int g = requestedGrade != null && requestedGrade > 0 ? requestedGrade : max;
        return Math.min(g, max);
    }

    public static void validateGraphGradeVisible(DeviceChildEntity child, int graphGrade) {
        int max = resolveChildMaxGrade(child);
        if (graphGrade > max) {
            throw new RenException("不可查看高于孩子当前年级（" + max + " 年级）的知识图谱");
        }
    }

    public static String resolveSubject(String subject) {
        return LearningKgGraphMatchUtil.normalizeSubject(subject);
    }
}
