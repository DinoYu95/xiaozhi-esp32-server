package xiaozhi.modules.sys.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.user.UserDetail;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.sys.enums.SuperAdminEnum;
import xiaozhi.modules.sys.service.SysParamsService;
import xiaozhi.modules.sys.service.SysUserScopeService;

@Service
@RequiredArgsConstructor
public class SysUserScopeServiceImpl implements SysUserScopeService {

    /** 与家长端绑设备默认 owner 一致，参数未配置时使用 */
    private static final long DEFAULT_PLATFORM_OWNER_USER_ID = 2019681905515061249L;

    private final SysParamsService sysParamsService;

    @Override
    public Long getPlatformOwnerUserId() {
        String value = sysParamsService.getValue(Constant.SERVER_PLATFORM_OWNER_USER_ID, true);
        if (StringUtils.isBlank(value) || "null".equalsIgnoreCase(value)) {
            return DEFAULT_PLATFORM_OWNER_USER_ID;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PLATFORM_OWNER_USER_ID;
        }
    }

    @Override
    public Long getDataScopeUserId() {
        UserDetail user = SecurityUser.getUser();
        if (user == null || user.getId() == null) {
            return null;
        }
        if (user.getSuperAdmin() != null && user.getSuperAdmin() == SuperAdminEnum.YES.value()) {
            return getPlatformOwnerUserId();
        }
        return user.getId();
    }

    @Override
    public boolean isInDataScope(Long resourceUserId) {
        if (resourceUserId == null) {
            return false;
        }
        Long scopeUserId = getDataScopeUserId();
        return scopeUserId != null && scopeUserId.equals(resourceUserId);
    }
}
