package xiaozhi.modules.learning.util;

import org.apache.commons.lang3.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import xiaozhi.modules.learning.entity.KgGraphReleaseEntity;

/**
 * 图谱 release 匹配：学科 / 省 / 教材 / 年级 与 {@link LearningProfileConstants} 对齐。
 */
public final class LearningKgGraphMatchUtil {

    private LearningKgGraphMatchUtil() {}

    public static String normalizeSubject(String subject) {
        return StringUtils.defaultIfBlank(subject, "math").trim().toLowerCase();
    }

    /** 请求的图谱年级是否落在 release 声明的 [gradeMin, gradeMax] 内 */
    public static boolean graphGradeWithinRelease(int graphGrade, Integer gradeMin, Integer gradeMax) {
        if (graphGrade <= 0) {
            return false;
        }
        if (gradeMin == null || gradeMax == null) {
            return true;
        }
        return gradeMin <= graphGrade && graphGrade <= gradeMax;
    }

    /** published 查询：年级落在 release 区间内（兼容 CSV 的 1–3 与教研单册 1–1） */
    public static void applyGraphGradeWithinRelease(
            LambdaQueryWrapper<KgGraphReleaseEntity> w, int graphGrade) {
        w.le(KgGraphReleaseEntity::getGradeMin, graphGrade)
                .ge(KgGraphReleaseEntity::getGradeMax, graphGrade);
    }
}
