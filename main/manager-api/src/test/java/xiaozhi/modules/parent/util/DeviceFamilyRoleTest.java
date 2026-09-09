package xiaozhi.modules.parent.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import xiaozhi.common.exception.RenException;

class DeviceFamilyRoleTest {

    @Test
    void normalizeOrNull_acceptsKnownCodes() {
        assertEquals("father", DeviceFamilyRole.normalizeOrNull("father"));
        assertEquals("maternal_grandmother", DeviceFamilyRole.normalizeOrNull("MATERNAL_GRANDMOTHER"));
    }

    @Test
    void normalizeOrNull_blankMeansClear() {
        assertNull(DeviceFamilyRole.normalizeOrNull(null));
        assertNull(DeviceFamilyRole.normalizeOrNull("  "));
    }

    @Test
    void normalizeOrNull_rejectsUnknown() {
        assertThrows(RenException.class, () -> DeviceFamilyRole.normalizeOrNull("uncle"));
    }

    @Test
    void resolveLabel_returnsChinese() {
        assertEquals("爸爸", DeviceFamilyRole.resolveLabel("father"));
        assertEquals("外婆", DeviceFamilyRole.resolveLabel("maternal_grandmother"));
        assertNull(DeviceFamilyRole.resolveLabel(null));
    }
}
