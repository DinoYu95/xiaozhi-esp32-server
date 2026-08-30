package xiaozhi.modules.ota;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import xiaozhi.common.exception.RenException;
import xiaozhi.modules.ota.util.SwuFilenameParser;

class SwuFilenameParserTest {

    @Test
    void parseStableSystem() {
        SwuFilenameParser.ParsedSwu parsed = SwuFilenameParser.parse("system_k230_linux_board_1.3.1_stable.swu");
        assertEquals("system", parsed.type());
        assertEquals("k230_linux_board", parsed.hardware());
        assertEquals("1.3.1", parsed.version());
        assertEquals("stable", parsed.channel());
        assertEquals("ota/k230_linux_board/stable/system/1.3.1/system_k230_linux_board_1.3.1_stable.swu",
                SwuFilenameParser.ossKey(parsed));
    }

    @Test
    void parseBetaAppWithPrerelease() {
        SwuFilenameParser.ParsedSwu parsed = SwuFilenameParser.parse("app_k230_linux_board_2.1.0-rc.1_beta.swu");
        assertEquals("app", parsed.type());
        assertEquals("2.1.0-rc.1", parsed.version());
        assertEquals("beta", parsed.channel());
    }

    @Test
    void parseStripsDirectory() {
        SwuFilenameParser.ParsedSwu parsed = SwuFilenameParser.parse("/tmp/upload/system_k230_linux_board_1.0.0_stable.swu");
        assertEquals("system", parsed.type());
        assertEquals("1.0.0", parsed.version());
    }

    @Test
    void rejectWrongExtension() {
        RenException ex = assertThrows(RenException.class, () -> SwuFilenameParser.parse("firmware.bin"));
        assertTrue(ex.getMsg().contains(".swu"));
    }

    @Test
    void rejectInvalidPattern() {
        assertThrows(RenException.class, () -> SwuFilenameParser.parse("system_k230_1.3_stable.swu"));
        assertThrows(RenException.class, () -> SwuFilenameParser.parse("fw_k230_linux_board_1.3.1_stable.swu"));
        assertThrows(RenException.class, () -> SwuFilenameParser.parse("system_k230_linux_board_1.3.1_pause.swu"));
    }
}
