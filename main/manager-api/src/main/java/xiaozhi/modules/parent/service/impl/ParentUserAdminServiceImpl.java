package xiaozhi.modules.parent.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.AESUtils;
import xiaozhi.modules.parent.dao.ParentAuthDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.entity.ParentAuthEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.service.ParentAuthService;
import xiaozhi.modules.parent.service.ParentUserAdminService;
import xiaozhi.modules.parent.storage.ParentStorageCategory;
import xiaozhi.modules.parent.storage.ParentStorageService;
import xiaozhi.modules.parent.util.ParentBetaAccessHelper;
import xiaozhi.modules.parent.util.ParentUserProfileHelper;
import xiaozhi.modules.parent.vo.AdminParentUserAuthVO;
import xiaozhi.modules.parent.vo.AdminParentUserDetailVO;
import xiaozhi.modules.parent.vo.AdminParentUserDeviceVO;
import xiaozhi.modules.parent.vo.AdminParentUserListItemVO;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@RequiredArgsConstructor
public class ParentUserAdminServiceImpl implements ParentUserAdminService {

    private static final String PARAM_PHONE_ENCRYPT_KEY = "parent.phone_encrypt_key";

    private final ParentUserDao parentUserDao;
    private final ParentAuthDao parentAuthDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final ParentAuthService parentAuthService;
    private final ParentStorageService parentStorageService;
    private final SysParamsService sysParamsService;

    @Value("${parent.phone_encrypt_key:}")
    private String phoneEncryptKeyFromConfig;

    @Override
    public PageData<AdminParentUserListItemVO> adminPage(Map<String, Object> params) {
        int page = parseInt(params.get("page"), 1);
        int limit = parseInt(params.get("limit"), 20);
        String keyword = StringUtils.trimToNull(stringParam(params.get("keyword")));
        String betaTester = stringParam(params.get("betaTester"));

        LambdaQueryWrapper<ParentUserEntity> q = new LambdaQueryWrapper<>();
        if (keyword != null) {
            if (keyword.matches("\\d+")) {
                q.eq(ParentUserEntity::getId, Long.parseLong(keyword));
            } else {
                q.like(ParentUserEntity::getNickname, keyword);
            }
        }
        if ("1".equals(betaTester)) {
            q.eq(ParentUserEntity::getIsBetaTester, 1);
        } else if ("0".equals(betaTester)) {
            q.and(w -> w.isNull(ParentUserEntity::getIsBetaTester).or().eq(ParentUserEntity::getIsBetaTester, 0));
        }
        q.orderByDesc(ParentUserEntity::getCreateTime);

        Page<ParentUserEntity> pg = parentUserDao.selectPage(new Page<>(page, limit), q);
        List<AdminParentUserListItemVO> list = new ArrayList<>();
        for (ParentUserEntity user : pg.getRecords()) {
            list.add(toListItem(user));
        }
        return new PageData<>(list, (int) pg.getTotal());
    }

    @Override
    public AdminParentUserDetailVO adminDetail(Long parentUserId) {
        ParentUserEntity user = parentUserDao.selectById(parentUserId);
        if (user == null) {
            throw new RenException("家长不存在");
        }
        AdminParentUserDetailVO vo = new AdminParentUserDetailVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setDisplayNickname(ParentUserProfileHelper.resolveNicknameOrFallback(user));
        vo.setAvatarUrl(resolveAvatarUrl(user));
        vo.setPhoneMasked(getMaskedPhoneForUser(parentUserId));
        vo.setBetaTester(ParentBetaAccessHelper.isDirectBetaTester(parentUserDao, parentUserId));
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        vo.setAuths(listAuths(parentUserId));
        vo.setDevices(listDevices(parentUserId));
        return vo;
    }

    private AdminParentUserListItemVO toListItem(ParentUserEntity user) {
        AdminParentUserListItemVO vo = new AdminParentUserListItemVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setDisplayNickname(ParentUserProfileHelper.resolveNicknameOrFallback(user));
        vo.setAvatarUrl(resolveAvatarUrl(user));
        vo.setPhoneMasked(getMaskedPhoneForUser(user.getId()));
        vo.setLoginMethods(buildLoginMethods(user.getId()));
        vo.setBetaTester(ParentBetaAccessHelper.isDirectBetaTester(parentUserDao, user.getId()));
        vo.setDeviceCount(countActiveDevices(user.getId()));
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }

    private String resolveAvatarUrl(ParentUserEntity user) {
        if (user == null || StringUtils.isBlank(user.getAvatarUrl())) {
            return null;
        }
        return parentStorageService.resolveAccessUrl(ParentStorageCategory.AVATAR, user.getAvatarUrl());
    }

    private String buildLoginMethods(Long parentUserId) {
        List<ParentAuthEntity> auths = parentAuthDao.selectList(
                new LambdaQueryWrapper<ParentAuthEntity>()
                        .eq(ParentAuthEntity::getParentUserId, parentUserId)
                        .orderByAsc(ParentAuthEntity::getId));
        if (auths.isEmpty()) {
            return "-";
        }
        Set<String> methods = new LinkedHashSet<>();
        for (ParentAuthEntity auth : auths) {
            String type = StringUtils.defaultString(auth.getAuthType(), "?");
            String channel = StringUtils.defaultString(auth.getChannel(), "?");
            methods.add(type + "/" + channel);
        }
        return String.join(", ", methods);
    }

    private int countActiveDevices(Long parentUserId) {
        Long count = parentDeviceBindingDao.selectCount(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE));
        return count != null ? count.intValue() : 0;
    }

    private List<AdminParentUserAuthVO> listAuths(Long parentUserId) {
        List<ParentAuthEntity> auths = parentAuthDao.selectList(
                new LambdaQueryWrapper<ParentAuthEntity>()
                        .eq(ParentAuthEntity::getParentUserId, parentUserId)
                        .orderByAsc(ParentAuthEntity::getId));
        return auths.stream().map(this::toAuthVo).collect(Collectors.toList());
    }

    private AdminParentUserAuthVO toAuthVo(ParentAuthEntity auth) {
        AdminParentUserAuthVO vo = new AdminParentUserAuthVO();
        vo.setId(auth.getId());
        vo.setAuthType(auth.getAuthType());
        vo.setChannel(auth.getChannel());
        vo.setOpenIdMasked(maskToken(auth.getOpenId()));
        vo.setUnionIdMasked(maskToken(auth.getUnionId()));
        vo.setPhoneMasked(maskPhone(decryptPhone(auth.getPhone())));
        vo.setCreateTime(auth.getCreateTime());
        return vo;
    }

    private List<AdminParentUserDeviceVO> listDevices(Long parentUserId) {
        List<ParentDeviceBindingEntity> bindings = parentDeviceBindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .orderByDesc(ParentDeviceBindingEntity::getBindTime));
        List<AdminParentUserDeviceVO> list = new ArrayList<>();
        for (ParentDeviceBindingEntity b : bindings) {
            AdminParentUserDeviceVO vo = new AdminParentUserDeviceVO();
            vo.setBindingId(b.getId());
            vo.setDeviceId(b.getDeviceId());
            vo.setRole(b.getRole());
            vo.setStatus(b.getStatus());
            vo.setBindTime(b.getBindTime());
            list.add(vo);
        }
        return list;
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

    private static String maskToken(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String v = value.trim();
        if (v.length() <= 8) {
            return "****";
        }
        return v.substring(0, 4) + "****" + v.substring(v.length() - 4);
    }

    private static int parseInt(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String stringParam(Object raw) {
        if (raw == null) {
            return null;
        }
        return String.valueOf(raw);
    }
}
