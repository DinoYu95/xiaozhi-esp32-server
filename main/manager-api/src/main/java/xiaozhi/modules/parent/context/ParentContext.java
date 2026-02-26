package xiaozhi.modules.parent.context;

import xiaozhi.common.constant.Constant;
import xiaozhi.common.utils.HttpContextUtils;

/**
 * 从当前请求获取家长用户 id（由 ParentTokenFilter 写入 request 属性）。
 */
public final class ParentContext {

    private ParentContext() {
    }

    /**
     * 获取当前请求的家长用户 id，需在 /parent-api 且已鉴权后调用。
     *
     * @return parent_user_id，未鉴权时为 null
     */
    public static Long getParentUserId() {
        var request = HttpContextUtils.getHttpServletRequest();
        if (request == null) {
            return null;
        }
        Object v = request.getAttribute(Constant.PARENT_USER_KEY);
        return v instanceof Long ? (Long) v : null;
    }
}
