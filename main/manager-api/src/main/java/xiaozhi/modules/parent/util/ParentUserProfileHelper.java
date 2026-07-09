package xiaozhi.modules.parent.util;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.storage.ParentStorageService;

public final class ParentUserProfileHelper {

    private ParentUserProfileHelper() {
    }

    /** 展示昵称：有则用 parent_user.nickname，无则返回 null（前端可显示「微信用户」） */
    public static String resolveNickname(ParentUserEntity user) {
        if (user == null || StringUtils.isBlank(user.getNickname())) {
            return null;
        }
        return user.getNickname().trim();
    }

    /**
     * 家庭共享成员列表：被邀请人通过资料页/上传更换过头像时返回 URL，否则 null（前端不展示头像）。
     * 微信登录同步的第三方头像 URL 不计入。
     */
    public static String resolveSharingAvatarUrl(ParentUserEntity user, ParentStorageService storage) {
        if (user == null || storage == null) {
            return null;
        }
        return storage.resolveSharingAvatarUrl(user.getAvatarUrl());
    }

    /** 列表等场景兜底展示名 */
    public static String resolveNicknameOrFallback(ParentUserEntity user) {
        String nickname = resolveNickname(user);
        if (nickname != null) {
            return nickname;
        }
        if (user != null && user.getId() != null) {
            return "微信用户";
        }
        return "微信用户";
    }
}
