package xiaozhi.modules.learning.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import xiaozhi.common.exception.RenException;

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
    void normalizeCity_keepsDistrictCodeWhenProvincePrefixMatches() {
        assertEquals("beijing_dongcheng", LearningGeoConstants.normalizeCity("beijing", "beijing_dongcheng"));
        assertEquals("beijing_dongcheng", LearningGeoConstants.normalizeCity("beijing", "东城区"));
    }

    @Test
    void municipality_helpers() {
        assertTrue(LearningGeoConstants.isMunicipality("beijing"));
        assertFalse(LearningGeoConstants.isMunicipality("shandong"));
        assertEquals("东城区", LearningGeoConstants.cityLabel("beijing", "beijing_dongcheng"));
        assertEquals("青岛市", LearningGeoConstants.cityLabel("shandong", "shandong_qingdao"));
    }

    @Test
    void normalizeCity_rejectsCrossProvinceMismatch() {
        assertThrows(
                RenException.class,
                () -> LearningGeoConstants.normalizeCity("beijing", "shandong_qingdao"));
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
