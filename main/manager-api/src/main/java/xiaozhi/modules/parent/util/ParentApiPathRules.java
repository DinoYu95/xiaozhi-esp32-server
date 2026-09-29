package xiaozhi.modules.parent.util;

/**
 * parent-api 路径规则：游客匿名、协议门禁豁免等。
 */
public final class ParentApiPathRules {

    private ParentApiPathRules() {
    }

    /** 无需 Bearer token（ParentTokenFilter / Shiro anon） */
    public static boolean isTokenAnonymous(String uri) {
        if (uri == null || !uri.contains("/parent-api/")) {
            return false;
        }
        return uri.contains("/parent-api/auth/wechat")
                || uri.contains("/parent-api/auth/phone/code")
                || uri.contains("/parent-api/auth/phone/login")
                || uri.contains("/parent-api/auth/avatar/file/")
                || uri.contains("/parent-api/feedback/image/file/")
                || uri.contains("/parent-api/device/child/voiceprint/play/")
                || uri.contains("/parent-api/chat/play/")
                || uri.contains("/parent-api/chat/snapshot/device-upload")
                || uri.contains("/parent-api/consent/document")
                || uri.contains("/parent-api/beta-confidentiality/document")
                || uri.contains("/parent-api/app/public-config")
                || uri.contains("/parent-api/app/guest-preview");
    }

    /** 儿童隐私协议门禁豁免（已登录用户未签协议时仍可访问） */
    public static boolean isConsentGateExempt(String uri) {
        if (uri == null || !uri.contains("/parent-api/")) {
            return false;
        }
        return isTokenAnonymous(uri)
                || uri.contains("/parent-api/auth/info")
                || uri.contains("/parent-api/consent/status")
                || uri.contains("/parent-api/consent/agree")
                || uri.contains("/parent-api/beta-confidentiality/status")
                || uri.contains("/parent-api/beta-confidentiality/agree");
    }

    /** 内测保密协议门禁豁免 */
    public static boolean isBetaConfGateExempt(String uri) {
        return isConsentGateExempt(uri);
    }
}
