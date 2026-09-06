package xiaozhi.modules.learning.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import xiaozhi.modules.growthportrait.util.GrowthAgeBandUtil;
import xiaozhi.modules.parent.entity.DeviceChildEntity;

class ChildGradeOptionsUtilTest {

    @Test
    void gradeOneWithStalePreschoolAgeStage_isNotPreschoolProfile() {
        DeviceChildEntity child = new DeviceChildEntity();
        child.setCurrentGrade(1);
        child.setAgeStage("幼小衔接 3-6岁");

        assertFalse(ChildGradeOptionsUtil.isPreschoolProfile(child));
        assertEquals("lower", GrowthAgeBandUtil.resolveAgeBand(child));
    }

    @Test
    void normalizeGradeProfile_clearsPreschoolAgeStageWhenPromotedToPrimary() {
        DeviceChildEntity child = new DeviceChildEntity();
        child.setCurrentGrade(1);
        child.setAgeStage("幼小衔接 3-6岁");

        ChildGradeOptionsUtil.normalizeGradeProfile(child);

        assertEquals(null, child.getAgeStage());
    }

    @Test
    void preschoolGrade_stillPreschoolProfile() {
        DeviceChildEntity child = new DeviceChildEntity();
        child.setCurrentGrade(0);

        assertTrue(ChildGradeOptionsUtil.isPreschoolProfile(child));
    }
}
