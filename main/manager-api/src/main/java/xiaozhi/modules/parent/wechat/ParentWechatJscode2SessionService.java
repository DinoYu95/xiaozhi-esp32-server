package xiaozhi.modules.parent.wechat;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.sys.service.SysParamsService;

/**
 * 小程序 wx.login code → openid（jscode2session）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentWechatJscode2SessionService {

    private static final String PARAM_WECHAT_APP_ID = "parent.wechat.app_id";
    private static final String PARAM_WECHAT_SECRET = "parent.wechat.secret";

    private final SysParamsService sysParamsService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SessionResult exchange(String jsCode) {
        if (StringUtils.isBlank(jsCode)) {
            throw wechatFail(ErrorCode.PARENT_WECHAT_CODE_INVALID, null);
        }
        String appId = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_WECHAT_APP_ID, true));
        String secret = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_WECHAT_SECRET, true));
        if (StringUtils.isAnyBlank(appId, secret)) {
            log.error("parent wechat login: missing sys_params {} or {}", PARAM_WECHAT_APP_ID, PARAM_WECHAT_SECRET);
            throw wechatFail(ErrorCode.PARENT_WECHAT_CREDENTIALS_MISSING, null);
        }
        URI uri = UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", appId)
                .queryParam("secret", secret)
                .queryParam("js_code", jsCode)
                .queryParam("grant_type", "authorization_code")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        String body;
        try {
            body = restTemplate.getForObject(uri, String.class);
        } catch (RestClientException e) {
            log.error("parent wechat jscode2session network error appId={} err={}", maskAppId(appId), e.toString());
            throw wechatFail(ErrorCode.PARENT_WECHAT_API_UNAVAILABLE, e);
        }
        if (StringUtils.isBlank(body)) {
            log.warn("parent wechat jscode2session empty body appId={}", maskAppId(appId));
            throw wechatFail(ErrorCode.PARENT_WECHAT_CODE_INVALID, null);
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                int errcode = node.get("errcode").asInt();
                String errmsg = node.has("errmsg") ? node.get("errmsg").asText() : "";
                log.warn("parent wechat jscode2session rejected appId={} errcode={} errmsg={}",
                        maskAppId(appId), errcode, errmsg);
                throw mapWechatErrcode(errcode, errmsg);
            }
            String openId = node.has("openid") ? node.get("openid").asText() : null;
            if (StringUtils.isBlank(openId)) {
                log.warn("parent wechat jscode2session no openid appId={} body={}", maskAppId(appId), truncate(body));
                throw wechatFail(ErrorCode.PARENT_WECHAT_CODE_INVALID, null);
            }
            String unionId = node.has("unionid") ? node.get("unionid").asText() : null;
            return new SessionResult(openId, unionId);
        } catch (RenException e) {
            throw e;
        } catch (Exception e) {
            log.warn("parent wechat jscode2session parse error appId={} body={}", maskAppId(appId), truncate(body), e);
            throw wechatFail(ErrorCode.PARENT_WECHAT_CODE_INVALID, e);
        }
    }

    /** 智控台展示：是否已配置 AppID + Secret */
    public WechatCredentialStatus credentialStatus() {
        String appId = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_WECHAT_APP_ID, true));
        String secret = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_WECHAT_SECRET, true));
        WechatCredentialStatus s = new WechatCredentialStatus();
        s.setConfigured(StringUtils.isNoneBlank(appId, secret));
        s.setAppIdMasked(maskAppId(appId));
        return s;
    }

    private RenException mapWechatErrcode(int errcode, String errmsg) {
        // https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
        return switch (errcode) {
            case 40029, 40163 -> wechatFail(ErrorCode.PARENT_WECHAT_CODE_EXPIRED, null);
            case 40013, 40125, 40212 -> wechatFail(ErrorCode.PARENT_WECHAT_CREDENTIALS_INVALID, null);
            case -1 -> wechatFail(ErrorCode.PARENT_WECHAT_API_UNAVAILABLE, null);
            default -> {
                log.warn("parent wechat unmapped errcode={} errmsg={}", errcode, errmsg);
                yield wechatFail(ErrorCode.PARENT_WECHAT_CODE_INVALID, null);
            }
        };
    }

    private static RenException wechatFail(int code, Throwable cause) {
        if (cause != null) {
            return new RenException(code, cause);
        }
        return new RenException(code);
    }

    private static String maskAppId(String appId) {
        if (StringUtils.isBlank(appId)) {
            return "(未配置)";
        }
        if (appId.length() <= 8) {
            return appId.charAt(0) + "***";
        }
        return appId.substring(0, 4) + "****" + appId.substring(appId.length() - 4);
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    public record SessionResult(String openId, String unionId) {
    }

    public static class WechatCredentialStatus {
        private boolean configured;
        private String appIdMasked;

        public boolean isConfigured() {
            return configured;
        }

        public void setConfigured(boolean configured) {
            this.configured = configured;
        }

        public String getAppIdMasked() {
            return appIdMasked;
        }

        public void setAppIdMasked(String appIdMasked) {
            this.appIdMasked = appIdMasked;
        }
    }
}
