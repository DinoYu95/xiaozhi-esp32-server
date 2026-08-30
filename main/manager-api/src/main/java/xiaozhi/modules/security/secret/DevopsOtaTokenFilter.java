package xiaozhi.modules.security.secret;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.utils.HttpContextUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.sys.service.SysParamsService;

/**
 * DevOps 硬件 OTA：校验 Header X-DevOps-Token。
 */
@Slf4j
public class DevopsOtaTokenFilter extends AuthenticatingFilter {

    public static final String HEADER = "X-DevOps-Token";
    public static final String PARAM_CODE = "devops.ota.service_token";

    private final SysParamsService sysParamsService;
    private final String yamlToken;

    public DevopsOtaTokenFilter(SysParamsService sysParamsService, String yamlToken) {
        this.sysParamsService = sysParamsService;
        this.yamlToken = yamlToken;
    }

    @Override
    protected ServerSecretToken createToken(ServletRequest request, ServletResponse response) {
        String token = getRequestToken((HttpServletRequest) request);
        if (StringUtils.isBlank(token)) {
            return null;
        }
        return new ServerSecretToken(token);
    }

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        return ((HttpServletRequest) request).getMethod().equals(RequestMethod.OPTIONS.name());
    }

    @Override
    protected boolean onAccessDenied(ServletRequest servletRequest, ServletResponse servletResponse) {
        String token = getRequestToken((HttpServletRequest) servletRequest);
        if (StringUtils.isBlank(token)) {
            sendUnauthorized((HttpServletResponse) servletResponse, "X-DevOps-Token 不能为空");
            return false;
        }
        String expected = resolveExpectedToken();
        if (StringUtils.isBlank(expected)) {
            sendUnauthorized((HttpServletResponse) servletResponse, "服务端未配置 devops.ota.service_token");
            return false;
        }
        if (!expected.equals(token)) {
            sendUnauthorized((HttpServletResponse) servletResponse, "无效的 DevOps Token");
            return false;
        }
        return true;
    }

    private String resolveExpectedToken() {
        String fromParams = sysParamsService == null ? null : sysParamsService.getValue(PARAM_CODE, true);
        if (StringUtils.isNotBlank(fromParams)) {
            return fromParams.trim();
        }
        return StringUtils.trimToEmpty(yamlToken);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) {
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", HttpContextUtils.getOrigin());
        try {
            String json = JsonUtils.toJsonString(new Result<Void>().error(ErrorCode.UNAUTHORIZED, message));
            response.getWriter().print(json);
        } catch (IOException e) {
            log.error("DevOps OTA 鉴权响应失败", e);
        }
    }

    private String getRequestToken(HttpServletRequest request) {
        return StringUtils.trimToEmpty(request.getHeader(HEADER));
    }
}
