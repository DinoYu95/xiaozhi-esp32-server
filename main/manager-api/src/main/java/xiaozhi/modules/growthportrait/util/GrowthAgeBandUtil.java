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

    private static boolean ListContains(String stage) {
        return "preschool".equals(stage) || "lower".equals(stage)
                || "upper".equals(stage) || "middle".equals(stage);
    }
}
