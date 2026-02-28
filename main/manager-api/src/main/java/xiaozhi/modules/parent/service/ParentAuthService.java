package xiaozhi.modules.parent.service;

import xiaozhi.modules.parent.entity.ParentAuthEntity;

/**
 * 家长端登录身份：按微信/手机+渠道查找或创建 parent_user，支持一人多登录方式
 */
public interface ParentAuthService {

    /**
     * 按微信 (channel, open_id) 查找家长用户 id，不存在返回 null
     */
    Long findParentUserIdByWechat(String openId, String channel);

    /**
     * 按手机号（加密）查找家长用户 id（任意渠道），不存在返回 null
     */
    Long findParentUserIdByPhone(String phoneEncrypted);

    /**
     * 按手机号+渠道查找家长用户 id，不存在返回 null
     */
    Long findParentUserIdByPhoneAndChannel(String phoneEncrypted, String channel);

    /**
     * 创建微信登录身份：先创建 parent_user，再插入 parent_auth
     *
     * @return 新建的 parent_user.id
     */
    long createWechatAuth(String openId, String unionId, String channel);

    /**
     * 为已有家长用户新增微信登录身份（同人多端）
     */
    void addWechatAuth(Long parentUserId, String openId, String unionId, String channel);

    /**
     * 创建手机登录身份：先创建 parent_user，再插入 parent_auth
     *
     * @return 新建的 parent_user.id
     */
    long createPhoneAuth(String phoneEncrypted, String channel);

    /**
     * 为已有家长用户新增手机登录身份（同人多端）
     *
     * @return 是否新建了 auth（true=新建，false=已存在该 channel+phone）
     */
    boolean addPhoneAuth(Long parentUserId, String phoneEncrypted, String channel);

    /**
     * 取该家长任意一条手机号类型的 auth（用于资料展示/更新）
     */
    ParentAuthEntity getAnyPhoneAuth(Long parentUserId);

    /**
     * 更新该家长某条手机号 auth 的 phone（用于资料修改）；若无则新增一条
     */
    void setPhoneAuth(Long parentUserId, String phoneEncrypted, String channel);
}
