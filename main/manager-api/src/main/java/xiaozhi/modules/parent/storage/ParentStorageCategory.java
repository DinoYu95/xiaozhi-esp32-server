package xiaozhi.modules.parent.storage;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;

/**
 * 家长端可上传的图片类别，对应 OSS 路径段 parent/{pathSegment}/...
 */
public enum ParentStorageCategory {

    AVATAR("avatar", "parent/avatar", 2 * 1024 * 1024),
    FEEDBACK("feedback", "parent/feedback", 5 * 1024 * 1024),
    CHAT_SNAPSHOT("chat_snapshot", "parent/chat_snapshot", 2 * 1024 * 1024);

    private final String code;
    private final String pathSegment;
    private final long maxBytes;

    ParentStorageCategory(String code, String pathSegment, long maxBytes) {
        this.code = code;
        this.pathSegment = pathSegment;
        this.maxBytes = maxBytes;
    }

    public String getCode() {
        return code;
    }

    public String getPathSegment() {
        return pathSegment;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public static ParentStorageCategory fromCode(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new RenException(ErrorCode.PARENT_STORAGE_CATEGORY_INVALID);
        }
        String c = raw.trim().toLowerCase();
        for (ParentStorageCategory cat : values()) {
            if (cat.code.equals(c)) {
                return cat;
            }
        }
        throw new RenException(ErrorCode.PARENT_STORAGE_CATEGORY_INVALID);
    }
}
