package xiaozhi.modules.parent.util;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.modules.parent.entity.ParentUserEntity;

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
