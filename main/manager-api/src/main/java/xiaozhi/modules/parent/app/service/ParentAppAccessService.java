package xiaozhi.modules.parent.app.service;

import xiaozhi.modules.parent.app.vo.ParentWechatAccessDeniedVO;

public interface ParentAppAccessService {

    /**
     * internal_beta 下是否允许为该微信身份发放登录 token。
     */
    boolean mayIssueLoginToken(String openId, String channel, Long parentUserId);

    ParentWechatAccessDeniedVO buildAccessDeniedPayload();

    /** 设备绑定成功后，将对应微信 openid 写入白名单（source=device_bind）。 */
    void ensureAllowlistAfterDeviceBind(Long parentUserId);
}
