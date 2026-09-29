package xiaozhi.modules.parent.betaconfidentiality.filter;

import java.io.IOException;

import org.springframework.stereotype.Component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.common.utils.HttpContextUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.betaconfidentiality.service.ParentBetaConfService;
import xiaozhi.modules.parent.util.ParentApiPathRules;

/**
 * 内测保密协议门禁：已登录且已通过儿童隐私协议后，仍未签署内测保密协议时拦截 parent-api。
 */
@Component
@RequiredArgsConstructor
public class ParentBetaConfFilter extends jakarta.servlet.http.HttpFilter {

    private final ParentBetaConfService parentBetaConfService;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.contains("/parent-api/")) {
            chain.doFilter(request, response);
            return;
        }
        if (ParentApiPathRules.isBetaConfGateExempt(uri)) {
            chain.doFilter(request, response);
            return;
        }
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!parentBetaConfService.isBetaConfRequired(parentUserId)) {
            chain.doFilter(request, response);
            return;
        }
        writeBetaConfRequired(response);
    }

    private static void writeBetaConfRequired(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", HttpContextUtils.getOrigin());
        response.getWriter().print(JsonUtils.toJsonString(new Result<Void>().error(ErrorCode.PARENT_BETA_CONF_REQUIRED)));
    }
}
