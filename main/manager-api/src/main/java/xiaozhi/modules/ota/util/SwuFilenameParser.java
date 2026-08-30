package xiaozhi.modules.ota.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.common.exception.RenException;

/**
 * SWU 文件名解析：{type}_{hardware}_{version}_{channel}.swu
 */
public final class SwuFilenameParser {

    public static final Pattern SWU_NAME_RE = Pattern.compile(
            "^(system|app)_([A-Za-z0-9_-]+)_(\\d+\\.\\d+\\.\\d+(?:[-+][\\w.]+)?)_(stable|beta)\\.swu$",
            Pattern.CASE_INSENSITIVE);

    private SwuFilenameParser() {
    }

    public static ParsedSwu parse(String filename) {
        String name = StringUtils.trimToEmpty(filename);
        if (name.contains("/") || name.contains("\\")) {
            name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        }
        if (!name.toLowerCase().endsWith(".swu")) {
            throw new RenException("文件必须是 .swu 格式");
        }
        Matcher m = SWU_NAME_RE.matcher(name);
        if (!m.matches()) {
            throw new RenException(
                    "文件名不符合规则: {type}_{hardware}_{version}_{channel}.swu 例: system_k230_linux_board_1.3.1_stable.swu");
        }
        return new ParsedSwu(
                m.group(1).toLowerCase(),
                m.group(2),
                m.group(3),
                m.group(4).toLowerCase(),
                name);
    }

    public static String ossKey(ParsedSwu parsed) {
        return "ota/" + parsed.hardware() + "/" + parsed.channel() + "/" + parsed.type()
                + "/" + parsed.version() + "/" + parsed.filename();
    }

    public record ParsedSwu(String type, String hardware, String version, String channel, String filename) {
    }
}
