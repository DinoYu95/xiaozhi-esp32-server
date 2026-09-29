package xiaozhi.modules.parent.app.service.impl;

import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.modules.parent.app.dao.ParentBetaAllowlistDao;
import xiaozhi.modules.parent.app.entity.ParentBetaAllowlistEntity;
import xiaozhi.modules.parent.app.service.ParentAppAccessService;
import xiaozhi.modules.parent.app.service.ParentAppConfigService;
import xiaozhi.modules.parent.app.vo.ParentWechatAccessDeniedVO;
import xiaozhi.modules.parent.dao.ParentAuthDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.entity.ParentAuthEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.util.ParentBetaAccessHelper;

@Service
@RequiredArgsConstructor
public class ParentAppAccessServiceImpl implements ParentAppAccessService {

    private static final String AUTH_TYPE_WECHAT = "wechat";
    private static final String DEFAULT_CHANNEL = "mini_program";
    private static final String SOURCE_DEVICE_BIND = "device_bind";

    private final ParentAppConfigService parentAppConfigService;
    private final ParentBetaAllowlistDao parentBetaAllowlistDao;
    private final ParentAuthDao parentAuthDao;
    private final ParentUserDao parentUserDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;

    @Override
    public boolean mayIssueLoginToken(String openId, String channel, Long parentUserId) {
        if ("open".equalsIgnoreCase(parentAppConfigService.getAccessMode())) {
            return true;
        }
        String ch = normalizeChannel(channel);
        if (isReviewerPass(openId, ch)) {
            return true;
        }
        if (hasEnabledAllowlist(openId, ch, false)) {
            return true;
        }
        if (parentUserId == null) {
            return false;
        }
        if (ParentBetaAccessHelper.isDirectBetaTester(parentUserDao, parentUserId)) {
            return true;
        }
        if (ParentBetaAccessHelper.hasBetaAccess(parentUserDao, parentDeviceBindingDao, parentUserId)) {
            return true;
        }
        if (hasActiveDeviceBinding(parentUserId)) {
            return true;
        }
        return false;
    }

    @Override
    public ParentWechatAccessDeniedVO buildAccessDeniedPayload() {
        ParentWechatAccessDeniedVO vo = new ParentWechatAccessDeniedVO();
        vo.setAccessMode(parentAppConfigService.getAccessMode());
        vo.setSupportHint("请确认使用购买/受邀时登记的微信，或联系内测客服");
        return vo;
    }

    @Override
    public void ensureAllowlistAfterDeviceBind(Long parentUserId) {
        if (parentUserId == null) {
            return;
        }
        ParentAuthEntity auth = parentAuthDao.selectOne(
                new LambdaQueryWrapper<ParentAuthEntity>()
                        .eq(ParentAuthEntity::getParentUserId, parentUserId)
                        .eq(ParentAuthEntity::getAuthType, AUTH_TYPE_WECHAT)
                        .last("LIMIT 1"));
        if (auth == null || StringUtils.isBlank(auth.getOpenId())) {
            return;
        }
        upsertAllowlist(auth.getOpenId(), auth.getChannel(), null, SOURCE_DEVICE_BIND, true, false, null);
    }

    private boolean isReviewerPass(String openId, String channel) {
        if (!parentAppConfigService.isReviewMode()) {
            return false;
        }
        return hasEnabledAllowlist(openId, channel, true);
    }

    private boolean hasEnabledAllowlist(String openId, String channel, boolean reviewerOnly) {
        if (StringUtils.isBlank(openId)) {
            return false;
        }
        LambdaQueryWrapper<ParentBetaAllowlistEntity> q = new LambdaQueryWrapper<ParentBetaAllowlistEntity>()
                .eq(ParentBetaAllowlistEntity::getWechatOpenid, openId)
                .eq(ParentBetaAllowlistEntity::getChannel, channel)
                .eq(ParentBetaAllowlistEntity::getEnabled, 1);
        if (reviewerOnly) {
            q.eq(ParentBetaAllowlistEntity::getReviewerTest, 1);
        }
        return parentBetaAllowlistDao.selectCount(q) > 0;
    }

    private boolean hasActiveDeviceBinding(Long parentUserId) {
        Long count = parentDeviceBindingDao.selectCount(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE));
        return count != null && count > 0;
    }

    private void upsertAllowlist(String openId, String channel, String phone, String source, boolean enabled,
            boolean reviewerTest, String remark) {
        String ch = normalizeChannel(channel);
        ParentBetaAllowlistEntity existing = parentBetaAllowlistDao.selectOne(
                new LambdaQueryWrapper<ParentBetaAllowlistEntity>()
                        .eq(ParentBetaAllowlistEntity::getWechatOpenid, openId)
                        .eq(ParentBetaAllowlistEntity::getChannel, ch));
        Date now = new Date();
        if (existing == null) {
            ParentBetaAllowlistEntity e = new ParentBetaAllowlistEntity();
            e.setWechatOpenid(openId);
            e.setChannel(ch);
            e.setPhone(phone);
            e.setSource(StringUtils.defaultIfBlank(source, "manual"));
            e.setEnabled(enabled ? 1 : 0);
            e.setReviewerTest(reviewerTest ? 1 : 0);
            e.setRemark(remark);
            e.setCreateTime(now);
            e.setUpdateTime(now);
            parentBetaAllowlistDao.insert(e);
            return;
        }
        if (StringUtils.isNotBlank(phone)) {
            existing.setPhone(phone);
        }
        if (StringUtils.isNotBlank(source)) {
            existing.setSource(source);
        }
        existing.setEnabled(enabled ? 1 : 0);
        if (reviewerTest) {
            existing.setReviewerTest(1);
        }
        if (remark != null) {
            existing.setRemark(remark);
        }
        existing.setUpdateTime(now);
        parentBetaAllowlistDao.updateById(existing);
    }

    private static String normalizeChannel(String channel) {
        return StringUtils.isNotBlank(channel) ? channel.trim() : DEFAULT_CHANNEL;
    }
}
