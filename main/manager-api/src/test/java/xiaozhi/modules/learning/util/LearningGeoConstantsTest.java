package xiaozhi.modules.learning.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LearningGeoConstantsTest {

    @Test
    void normalizeProvince_acceptsChineseLabel() {
        assertEquals("shandong", LearningGeoConstants.normalizeProvince("山东省"));
    }

    @Test
    void normalizeCity_acceptsChineseLabel() {
        assertEquals("shandong_qingdao", LearningGeoConstants.normalizeCity("shandong", "青岛市"));
        assertEquals("shandong_qingdao", LearningGeoConstants.normalizeCity("山东省", "青岛"));
    }

    @Test
    void normalizeSemester_acceptsChineseLabel() {
        assertEquals(LearningGeoConstants.SEMESTER_UPPER, LearningGeoConstants.normalizeSemester("上册"));
    }

    @Test
    void profileOptionsData_loads() {
        assertFalse(LearningGeoConstants.provinces().isEmpty());
        assertFalse(LearningGeoConstants.citiesByProvince().isEmpty());
        assertFalse(LearningGeoConstants.semesters().isEmpty());
    }
}
