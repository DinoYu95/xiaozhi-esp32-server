package xiaozhi.modules.sys.service;

/**
 * 智控台数据归属范围：超级管理员统一按「平台 owner」查看/操作 agent 与设备。
 */
public interface SysUserScopeService {

    /**
     * 平台 owner 的 sys_user.id（家长端绑设备、超管数据范围均归属此账号）
     */
    Long getPlatformOwnerUserId();

    /**
     * 当前请求用于查 agent/设备 的有效 user_id。
     * 超级管理员 → 平台 owner；普通用户 → 当前登录用户。
     */
    Long getDataScopeUserId();

    /**
     * 资源是否在当前数据范围内（按 user_id 判断）
     */
    boolean isInDataScope(Long resourceUserId);
}
