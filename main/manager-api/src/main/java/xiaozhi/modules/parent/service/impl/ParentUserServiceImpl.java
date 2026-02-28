package xiaozhi.modules.parent.service.impl;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.AESUtils;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.dto.ParentPhoneCodeDTO;
import xiaozhi.modules.parent.dto.ParentPhoneLoginDTO;
import xiaozhi.modules.parent.dto.ParentProfileDTO;
import xiaozhi.modules.parent.dto.ParentWechatLoginDTO;
import xiaozhi.modules.parent.entity.ParentAuthEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.service.ParentAuthService;
import xiaozhi.modules.parent.service.ParentUserService;
import xiaozhi.modules.parent.service.ParentUserTokenService;
import xiaozhi.modules.parent.vo.ParentLoginVO;
import xiaozhi.modules.parent.vo.ParentUserVO;
import xiaozhi.modules.security.service.CaptchaService;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@RequiredArgsConstructor
public class ParentUserServiceImpl implements ParentUserService {

    private static final String WECHAT_URL = "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";
    private static final String PARAM_PHONE_ENCRYPT_KEY = "parent.phone_encrypt_key";
    private static final String PARAM_WECHAT_APP_ID = "parent.wechat.app_id";
    private static final String PARAM_WECHAT_SECRET = "parent.wechat.secret";

    private final ParentUserDao parentUserDao;
    private final ParentAuthService parentAuthService;
    private final ParentUserTokenService parentUserTokenService;
    private final SysParamsService sysParamsService;
    private final CaptchaService captchaService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${parent.phone_encrypt_key:}")
    private String phoneEncryptKeyFromConfig;

    @Override
    public ParentLoginVO wechatLogin(ParentWechatLoginDTO dto) {
        if (StringUtils.isBlank(dto.getCode())) {
            throw new RenException(ErrorCode.PARENT_WECHAT_CODE_INVALID);
        }
        String appId = sysParamsService.getValue(PARAM_WECHAT_APP_ID, true);
        String secret = sysParamsService.getValue(PARAM_WECHAT_SECRET, true);
        if (StringUtils.isAnyBlank(appId, secret)) {
            throw new RenException(ErrorCode.PARENT_WECHAT_CODE_INVALID);
        }
        String url = WECHAT_URL.replace("{appid}", appId).replace("{secret}", secret).replace("{code}", dto.getCode());
        String body = restTemplate.getForObject(url, String.class);
        if (StringUtils.isBlank(body)) {
            throw new RenException(ErrorCode.PARENT_WECHAT_CODE_INVALID);
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                throw new RenException(ErrorCode.PARENT_WECHAT_CODE_INVALID);
            }
            String openId = node.has("openid") ? node.get("openid").asText() : null;
            String unionId = node.has("unionid") ? node.get("unionid").asText() : null;
            if (StringUtils.isBlank(openId)) {
                throw new RenException(ErrorCode.PARENT_WECHAT_CODE_INVALID);
            }
            String channel = StringUtils.isNotBlank(dto.getChannel()) ? dto.getChannel() : "mini_program";
            Long parentUserId = parentAuthService.findParentUserIdByWechat(openId, channel);
            if (parentUserId == null) {
                parentUserId = parentAuthService.createWechatAuth(openId, unionId, channel);
            }
            ParentUserEntity user = parentUserDao.selectById(parentUserId);
            if (user != null && (StringUtils.isNotBlank(dto.getNickname()) || StringUtils.isNotBlank(dto.getAvatarUrl()))) {
                if (StringUtils.isNotBlank(dto.getNickname())) {
                    user.setNickname(dto.getNickname());
                }
                if (StringUtils.isNotBlank(dto.getAvatarUrl())) {
                    user.setAvatarUrl(dto.getAvatarUrl());
                }
                user.setUpdateTime(new Date());
                parentUserDao.updateById(user);
            }
            ParentUserTokenService.TokenResult tr = parentUserTokenService.createToken(user.getId(), channel);
            return buildLoginVO(tr.token(), tr.expireTime(), user);
        } catch (RenException e) {
            throw e;
        } catch (Exception e) {
            throw new RenException(ErrorCode.PARENT_WECHAT_CODE_INVALID, e);
        }
    }

    @Override
    public void sendPhoneCode(ParentPhoneCodeDTO dto) {
        if (StringUtils.isBlank(dto.getPhone())) {
            throw new RenException(ErrorCode.PHONE_FORMAT_ERROR);
        }
        captchaService.sendSMSValidateCode(dto.getPhone());
    }

    @Override
    public ParentLoginVO phoneLogin(ParentPhoneLoginDTO dto) {
        if (StringUtils.isAnyBlank(dto.getPhone(), dto.getCode())) {
            throw new RenException(ErrorCode.PARENT_PHONE_CODE_INVALID);
        }
        boolean valid = captchaService.validateSMSValidateCode(dto.getPhone(), dto.getCode(), true);
        if (!valid) {
            throw new RenException(ErrorCode.PARENT_PHONE_CODE_INVALID);
        }
        String phoneEncrypted = encryptPhone(dto.getPhone());
        String channel = StringUtils.isNotBlank(dto.getChannel()) ? dto.getChannel() : "app";
        Long parentUserId = parentAuthService.findParentUserIdByPhone(phoneEncrypted);
        if (parentUserId == null) {
            parentUserId = parentAuthService.createPhoneAuth(phoneEncrypted, channel);
        } else {
            parentAuthService.addPhoneAuth(parentUserId, phoneEncrypted, channel);
        }
        ParentUserEntity user = parentUserDao.selectById(parentUserId);
        ParentUserTokenService.TokenResult tr = parentUserTokenService.createToken(user.getId(), channel);
        return buildLoginVO(tr.token(), tr.expireTime(), user);
    }

    @Override
    public ParentUserVO getInfo(Long parentUserId) {
        ParentUserEntity user = parentUserDao.selectById(parentUserId);
        if (user == null) {
            return null;
        }
        ParentUserVO vo = new ParentUserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(getMaskedPhoneForUser(parentUserId));
        return vo;
    }

    @Override
    public void updateProfile(Long parentUserId, ParentProfileDTO dto) {
        ParentUserEntity user = parentUserDao.selectById(parentUserId);
        if (user == null) {
            return;
        }
        if (StringUtils.isNotBlank(dto.getNickname())) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl());
        }
        if (StringUtils.isNotBlank(dto.getPhone())) {
            parentAuthService.setPhoneAuth(parentUserId, encryptPhone(dto.getPhone()), "app");
        }
        user.setUpdateTime(new Date());
        parentUserDao.updateById(user);
    }

    @Override
    public void logout(String token) {
        parentUserTokenService.invalidateToken(token);
    }

    private ParentLoginVO buildLoginVO(String token, Date expireTime, ParentUserEntity user) {
        ParentLoginVO vo = new ParentLoginVO();
        vo.setToken(token);
        vo.setExpireAt(expireTime != null ? expireTime.getTime() : null);
        ParentUserVO u = new ParentUserVO();
        u.setId(user.getId());
        u.setNickname(user.getNickname());
        u.setAvatarUrl(user.getAvatarUrl());
        u.setPhone(getMaskedPhoneForUser(user.getId()));
        vo.setUser(u);
        return vo;
    }

    private String getMaskedPhoneForUser(Long parentUserId) {
        ParentAuthEntity phoneAuth = parentAuthService.getAnyPhoneAuth(parentUserId);
        if (phoneAuth == null || StringUtils.isBlank(phoneAuth.getPhone())) {
            return null;
        }
        return maskPhone(decryptPhone(phoneAuth.getPhone()));
    }

    private String getPhoneEncryptKey() {
        String key = sysParamsService.getValue(PARAM_PHONE_ENCRYPT_KEY, true);
        if (StringUtils.isBlank(key)) {
            key = phoneEncryptKeyFromConfig;
        }
        return key;
    }

    private String encryptPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return null;
        }
        String key = getPhoneEncryptKey();
        if (StringUtils.isBlank(key)) {
            return phone; // 未配置密钥时不加密，便于开发
        }
        return AESUtils.encrypt(key, phone);
    }

    private String decryptPhone(String encrypted) {
        if (StringUtils.isBlank(encrypted)) {
            return null;
        }
        String key = getPhoneEncryptKey();
        if (StringUtils.isBlank(key)) {
            return encrypted;
        }
        try {
            return AESUtils.decrypt(key, encrypted);
        } catch (Exception e) {
            return null;
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
