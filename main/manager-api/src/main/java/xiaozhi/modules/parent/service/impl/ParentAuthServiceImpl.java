package xiaozhi.modules.parent.service.impl;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.modules.parent.dao.ParentAuthDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.entity.ParentAuthEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.service.ParentAuthService;

@Service
@RequiredArgsConstructor
public class ParentAuthServiceImpl implements ParentAuthService {

    private static final String AUTH_TYPE_WECHAT = "wechat";
    private static final String AUTH_TYPE_PHONE = "phone";

    private final ParentAuthDao parentAuthDao;
    private final ParentUserDao parentUserDao;

    @Override
    public Long findParentUserIdByWechat(String openId, String channel) {
        if (StringUtils.isBlank(openId) || StringUtils.isBlank(channel)) {
            return null;
        }
        ParentAuthEntity auth = parentAuthDao.selectOne(
                new LambdaQueryWrapper<ParentAuthEntity>()
                        .eq(ParentAuthEntity::getAuthType, AUTH_TYPE_WECHAT)
                        .eq(ParentAuthEntity::getChannel, channel)
                        .eq(ParentAuthEntity::getOpenId, openId));
        return auth != null ? auth.getParentUserId() : null;
    }

    @Override
    public Long findParentUserIdByPhone(String phoneEncrypted) {
        if (StringUtils.isBlank(phoneEncrypted)) {
            return null;
        }
        ParentAuthEntity auth = parentAuthDao.selectOne(
                new LambdaQueryWrapper<ParentAuthEntity>()
                        .eq(ParentAuthEntity::getAuthType, AUTH_TYPE_PHONE)
                        .eq(ParentAuthEntity::getPhone, phoneEncrypted)
                        .last("LIMIT 1"));
        return auth != null ? auth.getParentUserId() : null;
    }

    @Override
    public Long findParentUserIdByPhoneAndChannel(String phoneEncrypted, String channel) {
        if (StringUtils.isBlank(phoneEncrypted) || StringUtils.isBlank(channel)) {
            return null;
        }
        ParentAuthEntity auth = parentAuthDao.selectOne(
                new LambdaQueryWrapper<ParentAuthEntity>()
                        .eq(ParentAuthEntity::getAuthType, AUTH_TYPE_PHONE)
                        .eq(ParentAuthEntity::getChannel, channel)
                        .eq(ParentAuthEntity::getPhone, phoneEncrypted));
        return auth != null ? auth.getParentUserId() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long createWechatAuth(String openId, String unionId, String channel) {
        ParentUserEntity user = new ParentUserEntity();
        user.setCreateTime(new Date());
        user.setUpdateTime(user.getCreateTime());
        parentUserDao.insert(user);
        ParentAuthEntity auth = new ParentAuthEntity();
        auth.setParentUserId(user.getId());
        auth.setAuthType(AUTH_TYPE_WECHAT);
        auth.setChannel(channel);
        auth.setOpenId(openId);
        auth.setUnionId(unionId);
        auth.setCreateTime(new Date());
        auth.setUpdateTime(auth.getCreateTime());
        parentAuthDao.insert(auth);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addWechatAuth(Long parentUserId, String openId, String unionId, String channel) {
        ParentAuthEntity auth = new ParentAuthEntity();
        auth.setParentUserId(parentUserId);
        auth.setAuthType(AUTH_TYPE_WECHAT);
        auth.setChannel(channel);
        auth.setOpenId(openId);
        auth.setUnionId(unionId);
        auth.setCreateTime(new Date());
        auth.setUpdateTime(auth.getCreateTime());
        parentAuthDao.insert(auth);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long createPhoneAuth(String phoneEncrypted, String channel) {
        ParentUserEntity user = new ParentUserEntity();
        user.setCreateTime(new Date());
        user.setUpdateTime(user.getCreateTime());
        parentUserDao.insert(user);
        ParentAuthEntity auth = new ParentAuthEntity();
        auth.setParentUserId(user.getId());
        auth.setAuthType(AUTH_TYPE_PHONE);
        auth.setChannel(channel);
        auth.setPhone(phoneEncrypted);
        auth.setCreateTime(new Date());
        auth.setUpdateTime(auth.getCreateTime());
        parentAuthDao.insert(auth);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addPhoneAuth(Long parentUserId, String phoneEncrypted, String channel) {
        ParentAuthEntity existing = parentAuthDao.selectOne(
                new LambdaQueryWrapper<ParentAuthEntity>()
                        .eq(ParentAuthEntity::getParentUserId, parentUserId)
                        .eq(ParentAuthEntity::getAuthType, AUTH_TYPE_PHONE)
                        .eq(ParentAuthEntity::getChannel, channel));
        if (existing != null) {
            return false;
        }
        ParentAuthEntity auth = new ParentAuthEntity();
        auth.setParentUserId(parentUserId);
        auth.setAuthType(AUTH_TYPE_PHONE);
        auth.setChannel(channel);
        auth.setPhone(phoneEncrypted);
        auth.setCreateTime(new Date());
        auth.setUpdateTime(auth.getCreateTime());
        parentAuthDao.insert(auth);
        return true;
    }

    @Override
    public ParentAuthEntity getAnyPhoneAuth(Long parentUserId) {
        return parentAuthDao.selectOne(
                new LambdaQueryWrapper<ParentAuthEntity>()
                        .eq(ParentAuthEntity::getParentUserId, parentUserId)
                        .eq(ParentAuthEntity::getAuthType, AUTH_TYPE_PHONE)
                        .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPhoneAuth(Long parentUserId, String phoneEncrypted, String channel) {
        ParentAuthEntity existing = parentAuthDao.selectOne(
                new LambdaQueryWrapper<ParentAuthEntity>()
                        .eq(ParentAuthEntity::getParentUserId, parentUserId)
                        .eq(ParentAuthEntity::getAuthType, AUTH_TYPE_PHONE)
                        .eq(ParentAuthEntity::getChannel, channel));
        if (existing != null) {
            existing.setPhone(phoneEncrypted);
            existing.setUpdateTime(new Date());
            parentAuthDao.updateById(existing);
        } else {
            addPhoneAuth(parentUserId, phoneEncrypted, channel);
        }
    }
}
