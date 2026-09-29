package xiaozhi.modules.parent.app.vo;

import lombok.Getter;
import xiaozhi.modules.parent.vo.ParentLoginVO;

@Getter
public class ParentWechatLoginOutcome {

    private final ParentLoginVO login;
    private final int deniedCode;
    private final String deniedMsg;
    private final ParentWechatAccessDeniedVO deniedData;

    private ParentWechatLoginOutcome(ParentLoginVO login, int deniedCode, String deniedMsg,
            ParentWechatAccessDeniedVO deniedData) {
        this.login = login;
        this.deniedCode = deniedCode;
        this.deniedMsg = deniedMsg;
        this.deniedData = deniedData;
    }

    public static ParentWechatLoginOutcome ok(ParentLoginVO login) {
        return new ParentWechatLoginOutcome(login, 0, null, null);
    }

    public static ParentWechatLoginOutcome denied(int code, String msg, ParentWechatAccessDeniedVO data) {
        return new ParentWechatLoginOutcome(null, code, msg, data);
    }

    public boolean isDenied() {
        return login == null;
    }
}
