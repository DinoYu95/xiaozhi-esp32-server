package xiaozhi.modules.sms.service.imp;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.sms.service.SmsService;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@AllArgsConstructor
@Slf4j
public class ALiYunSmsService implements SmsService {
    private static final String API_TYPE_DYSMSAPI = "dysmsapi";
    private static final String API_TYPE_DYPNSAPI = "dypnsapi";

    private final SysParamsService sysParamsService;
    private final RedisUtils redisUtils;

    @Override
    public void sendVerificationCodeSms(String phone, String VerificationCode) {
        String apiType = getParamOptional(Constant.SysMSMParam.ALIYUN_SMS_API_TYPE.getValue(), API_TYPE_DYSMSAPI);
        if (API_TYPE_DYPNSAPI.equalsIgnoreCase(apiType)) {
            sendViaDypnsapi(phone, VerificationCode);
        } else {
            sendViaDysmsapi(phone, VerificationCode);
        }
    }

    /** 国内短信 SendSms（需在「国内短信」控制台申请签名和模板） */
    private void sendViaDysmsapi(String phone, String VerificationCode) {
        Client client = createDysmsapiClient();
        String signName = sysParamsService.getValue(Constant.SysMSMParam.ALIYUN_SMS_SIGN_NAME.getValue(), true);
        String templateCode = sysParamsService.getValue(Constant.SysMSMParam.ALIYUN_SMS_SMS_CODE_TEMPLATE_CODE.getValue(), true);
        try {
            SendSmsRequest req = new SendSmsRequest()
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setPhoneNumbers(phone)
                    .setTemplateParam(String.format("{\"code\":\"%s\"}", VerificationCode));
            SendSmsResponse resp = client.sendSmsWithOptions(req, new RuntimeOptions());
            log.info("发送短信响应的message: {}, code: {}", resp.getBody().getMessage(), resp.getBody().getCode());
        } catch (Exception e) {
            rollbackTodayCount(phone);
            log.error(e.getMessage());
            throw new RenException(ErrorCode.SMS_SEND_FAILED);
        }
    }

    /** 号码认证 SendSmsVerifyCode（国内 2017-05-25；ap-southeast-1 国际端点需 2017-07-25，当前仅支持国内 region） */
    private void sendViaDypnsapi(String phone, String VerificationCode) {
        String regionId = getParamOptional(Constant.SysMSMParam.ALIYUN_SMS_DYPNSAPI_REGION_ID.getValue(), "cn-hangzhou");
        if ("ap-southeast-1".equals(regionId)) {
            throw new RenException("国际站点(ap-southeast-1)暂不支持通过本接口发送验证码，请将系统参数 aliyun.sms.dypnsapi_region_id 设为 cn-hangzhou 使用国内号码认证，或通过阿里云控制台/API 直接调用 2017-07-25 接口");
        }
        com.aliyun.dypnsapi20170525.Client client = createDypnsapiClient();
        String signName = sysParamsService.getValue(Constant.SysMSMParam.ALIYUN_SMS_SIGN_NAME.getValue(), true);
        String templateCode = sysParamsService.getValue(Constant.SysMSMParam.ALIYUN_SMS_SMS_CODE_TEMPLATE_CODE.getValue(), true);
        try {
            String templateParam = String.format("{\"code\":\"%s\",\"min\":\"5\"}", VerificationCode);
            SendSmsVerifyCodeRequest req = new SendSmsVerifyCodeRequest()
                    .setPhoneNumber(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam(templateParam);
            SendSmsVerifyCodeResponse resp = client.sendSmsVerifyCodeWithOptions(req, new RuntimeOptions());
            log.info("发送短信(Dypnsapi) message: {}, code: {}", resp.getBody().getMessage(), resp.getBody().getCode());
        } catch (Exception e) {
            rollbackTodayCount(phone);
            log.error(e.getMessage());
            throw new RenException(ErrorCode.SMS_SEND_FAILED);
        }
    }

    private void rollbackTodayCount(String phone) {
        String todayCountKey = RedisKeys.getSMSTodayCountKey(phone);
        redisUtils.delete(todayCountKey);
    }

    private String getParamOptional(String paramCode, String defaultValue) {
        try {
            String v = sysParamsService.getValue(paramCode, false);
            return StringUtils.isNotBlank(v) ? v.trim() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Client createDysmsapiClient() {
        String accessKeyId = sysParamsService.getValue(Constant.SysMSMParam.ALIYUN_SMS_ACCESS_KEY_ID.getValue(), true);
        String accessKeySecret = sysParamsService.getValue(Constant.SysMSMParam.ALIYUN_SMS_ACCESS_KEY_SECRET.getValue(), true);
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret);
            config.endpoint = "dysmsapi.aliyuncs.com";
            return new Client(config);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RenException(ErrorCode.SMS_CONNECTION_FAILED);
        }
    }

    private com.aliyun.dypnsapi20170525.Client createDypnsapiClient() {
        String accessKeyId = sysParamsService.getValue(Constant.SysMSMParam.ALIYUN_SMS_ACCESS_KEY_ID.getValue(), true);
        String accessKeySecret = sysParamsService.getValue(Constant.SysMSMParam.ALIYUN_SMS_ACCESS_KEY_SECRET.getValue(), true);
        String regionId = getParamOptional(Constant.SysMSMParam.ALIYUN_SMS_DYPNSAPI_REGION_ID.getValue(), "cn-hangzhou");
        String endpoint = getParamOptional(Constant.SysMSMParam.ALIYUN_SMS_DYPNSAPI_ENDPOINT.getValue(), null);
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setRegionId(regionId);
            if (StringUtils.isNotBlank(endpoint)) {
                config.endpoint = endpoint;
            }
            return new com.aliyun.dypnsapi20170525.Client(config);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RenException(ErrorCode.SMS_CONNECTION_FAILED);
        }
    }

}
