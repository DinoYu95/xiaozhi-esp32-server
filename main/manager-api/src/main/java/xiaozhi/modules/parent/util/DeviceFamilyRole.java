package xiaozhi.modules.parent.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.common.exception.RenException;

/**
 * 设备家庭共享成员在本设备上的角色备注（与声纹 sourceName 独立）。
 */
public enum DeviceFamilyRole {

    FATHER("father", "爸爸"),
    MOTHER("mother", "妈妈"),
    PATERNAL_GRANDFATHER("paternal_grandfather", "爷爷"),
    PATERNAL_GRANDMOTHER("paternal_grandmother", "奶奶"),
    MATERNAL_GRANDFATHER("maternal_grandfather", "外公"),
    MATERNAL_GRANDMOTHER("maternal_grandmother", "外婆"),
    OTHER("other", "其他");

    private static final Map<String, DeviceFamilyRole> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(r -> r.code, r -> r, (a, b) -> a));
    private static final Map<String, DeviceFamilyRole> BY_LABEL = Arrays.stream(values())
            .collect(Collectors.toMap(r -> r.label, r -> r, (a, b) -> a));

    private final String code;
    private final String label;

    DeviceFamilyRole(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static String resolveLabel(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        DeviceFamilyRole role = BY_CODE.get(code.trim().toLowerCase(Locale.ROOT));
        return role != null ? role.label : null;
    }

    /**
     * @return 规范化 code；blank 表示清除备注。支持 code（father）或中文 label（爸爸）。
     */
    public static String normalizeOrNull(String raw) {
        if (raw == null || StringUtils.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        DeviceFamilyRole byLabel = BY_LABEL.get(trimmed);
        if (byLabel != null) {
            return byLabel.code;
        }
        String code = trimmed.toLowerCase(Locale.ROOT);
        if (BY_CODE.containsKey(code)) {
            return code;
        }
        throw new RenException("家庭角色无效，请传 code（father）或中文（爸爸）");
    }

    public static List<DeviceFamilyRoleOption> listOptions() {
        return Arrays.stream(values())
                .map(r -> new DeviceFamilyRoleOption(r.code, r.label))
                .collect(Collectors.toList());
    }

    public record DeviceFamilyRoleOption(String code, String label) {
    }
}
