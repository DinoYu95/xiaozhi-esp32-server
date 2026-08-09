package xiaozhi.modules.learning.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LearningKgGraphMatchUtilTest {

    @Test
    void graphGradeWithinRelease_range() {
        assertTrue(LearningKgGraphMatchUtil.graphGradeWithinRelease(1, 1, 3));
        assertTrue(LearningKgGraphMatchUtil.graphGradeWithinRelease(2, 1, 3));
        assertTrue(LearningKgGraphMatchUtil.graphGradeWithinRelease(3, 1, 3));
        assertFalse(LearningKgGraphMatchUtil.graphGradeWithinRelease(4, 1, 3));
        assertTrue(LearningKgGraphMatchUtil.graphGradeWithinRelease(1, 1, 1));
        assertFalse(LearningKgGraphMatchUtil.graphGradeWithinRelease(2, 1, 1));
    }

    @Test
    void normalizeSubject() {
        assertEquals("math", LearningKgGraphMatchUtil.normalizeSubject(null));
        assertEquals("chn", LearningKgGraphMatchUtil.normalizeSubject(" CHN "));
    }

    @Test
    void normalizeProvince_beijingVariants() {
        assertEquals("beijing", LearningProfileConstants.normalizeProvince("beijing"));
        assertEquals("beijing", LearningProfileConstants.normalizeProvince("北京"));
        assertEquals("beijing", LearningProfileConstants.normalizeProvince("北京市"));
        assertEquals("CN", LearningProfileConstants.normalizeProvince(null));
    }

    @Test
    void normalizeTextbook_legacySeries() {
        assertEquals("pep", LearningProfileConstants.textbookFromLegacySeries("人教版", null));
        assertEquals("pep", LearningProfileConstants.textbookFromLegacySeries(null, "pep"));
    }
}
