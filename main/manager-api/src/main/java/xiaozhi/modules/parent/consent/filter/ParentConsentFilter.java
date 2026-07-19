package xiaozhi.modules.parent.consent.filter;

import java.io.IOException;

import org.springframework.stereotype.Component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.utils.HttpContextUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.consent.service.ParentConsentService;

/**
 * 家长端协议门禁：已登录但未同意当前版本时，拦截除白名单外的 parent-api。
 */
@Component
@RequiredArgsConstructor
public class ParentConsentFilter extends jakarta.servlet.http.HttpFilter {

    private final ParentConsentService parentConsentService;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.contains("/parent-api/")) {
            chain.doFilter(request, response);
            return;
        }
        if (isConsentExemptPath(uri)) {
            chain.doFilter(request, response);
            return;
        }
        Long parentUserId = (Long) request.getAttribute(Constant.PARENT_USER_KEY);
        if (parentUserId == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!parentConsentService.isConsentRequired(parentUserId)) {
            chain.doFilter(request, response);
            return;
        }
        writeConsentRequired(response);
    }

    private static boolean isConsentExemptPath(String uri) {
        return uri.contains("/parent-api/auth/wechat")
                || uri.contains("/parent-api/auth/phone/code")
                || uri.contains("/parent-api/auth/phone/login")
                || uri.contains("/parent-api/auth/avatar/file/")
                || uri.contains("/parent-api/auth/info")
                || uri.contains("/parent-api/consent/document")
                || uri.contains("/parent-api/consent/status")
                || uri.contains("/parent-api/consent/agree")
                || uri.contains("/parent-api/chat/snapshot/device-upload");
    }

    private static void writeConsentRequired(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", HttpContextUtils.getOrigin());
        response.getWriter().print(JsonUtils.toJsonString(new Result<Void>().error(ErrorCode.PARENT_CONSENT_REQUIRED)));
    }
}
