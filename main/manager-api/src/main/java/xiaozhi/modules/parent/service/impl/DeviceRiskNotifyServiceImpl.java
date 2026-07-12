package xiaozhi.modules.parent.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.dto.DeviceRiskNotifySubscriberItemDTO;
import xiaozhi.modules.parent.dto.DeviceRiskNotifySubscriberUpdateDTO;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.service.DeviceRiskNotifyService;
import xiaozhi.modules.parent.storage.ParentStorageService;
import xiaozhi.modules.parent.util.ParentDeviceAccessHelper;
import xiaozhi.modules.parent.util.ParentDeviceDisplayResolver;
import xiaozhi.modules.parent.util.ParentUserProfileHelper;
import xiaozhi.modules.parent.vo.DeviceMemberItemVO;
import xiaozhi.modules.parent.vo.DeviceRiskNotifyAccessVO;
import xiaozhi.modules.parent.vo.DeviceRiskNotifySubscribersVO;

@Service
@RequiredArgsConstructor
public class DeviceRiskNotifyServiceImpl implements DeviceRiskNotifyService {

    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final ParentUserDao parentUserDao;
    private final DeviceDao deviceDao;
    private final DeviceChildDao deviceChildDao;
    private final ParentStorageService parentStorageService;

    @Override
    public DeviceRiskNotifySubscribersVO getSubscribers(Long parentUserId, String deviceId) {
        ParentDeviceAccessHelper.requireOwner(parentDeviceBindingDao, parentUserId, deviceId);
        String resolvedDeviceId = ParentDeviceAccessHelper.requireActiveBinding(
                parentDeviceBindingDao, parentUserId, deviceId).getDeviceId();
        List<ParentDeviceBindingEntity> bindings =
                ParentDeviceAccessHelper.findActiveBindingsForDevice(parentDeviceBindingDao, resolvedDeviceId);

        DeviceRiskNotifySubscribersVO vo = new DeviceRiskNotifySubscribersVO();
        vo.setDeviceId(ParentDeviceDisplayResolver.canonicalDeviceId(deviceDao, resolvedDeviceId));
        vo.setDeviceName(ParentDeviceDisplayResolver.resolveDeviceName(
                deviceDao, deviceChildDao, parentDeviceBindingDao, resolvedDeviceId));
        vo.setOwnerAlwaysReceive(true);
        vo.setMembers(buildMemberItems(bindings));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSubscribers(
            Long parentUserId, String deviceId, DeviceRiskNotifySubscriberUpdateDTO dto) {
        ParentDeviceAccessHelper.requireOwner(parentDeviceBindingDao, parentUserId, deviceId);
        String resolvedDeviceId = ParentDeviceAccessHelper.requireActiveBinding(
                parentDeviceBindingDao, parentUserId, deviceId).getDeviceId();
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            return;
        }

        List<ParentDeviceBindingEntity> bindings =
                ParentDeviceAccessHelper.findActiveBindingsForDevice(parentDeviceBindingDao, resolvedDeviceId);
        Map<Long, ParentDeviceBindingEntity> byParentId = new HashMap<>();
        for (ParentDeviceBindingEntity b : bindings) {
            if (b.getParentUserId() != null) {
                byParentId.put(b.getParentUserId(), b);
            }
        }

        Date now = new Date();
        for (DeviceRiskNotifySubscriberItemDTO item : dto.getItems()) {
            if (item == null || item.getParentId() == null || item.getReceiveRiskNotify() == null) {
                continue;
            }
            ParentDeviceBindingEntity target = byParentId.get(item.getParentId());
            if (target == null) {
                throw new RenException(ErrorCode.PARENT_RISK_NOTIFY_MEMBER_INVALID);
            }
            if (ParentDeviceAccessHelper.isOwner(target)) {
                continue;
            }
            int flag = Boolean.TRUE.equals(item.getReceiveRiskNotify()) ? 1 : 0;
            if (target.getReceiveRiskNotify() != null && target.getReceiveRiskNotify() == flag) {
                continue;
            }
            ParentDeviceBindingEntity patch = new ParentDeviceBindingEntity();
            patch.setId(target.getId());
            patch.setReceiveRiskNotify(flag);
            patch.setUpdatedAt(now);
            parentDeviceBindingDao.updateById(patch);
        }
    }

    @Override
    public DeviceRiskNotifyAccessVO getAccess(Long parentUserId, String deviceId) {
        ParentDeviceBindingEntity binding =
                ParentDeviceAccessHelper.requireActiveBinding(parentDeviceBindingDao, parentUserId, deviceId);
        String resolvedDeviceId = binding.getDeviceId();
        String role = StringUtils.isNotBlank(binding.getRole())
                ? binding.getRole()
                : ParentDeviceBindingEntity.ROLE_OWNER;
        boolean isOwner = ParentDeviceAccessHelper.isOwner(binding);

        DeviceRiskNotifyAccessVO vo = new DeviceRiskNotifyAccessVO();
        vo.setDeviceId(ParentDeviceDisplayResolver.canonicalDeviceId(deviceDao, resolvedDeviceId));
        vo.setRole(role);
        vo.setReceiveRiskNotify(ParentDeviceAccessHelper.isReceiveRiskNotifyEnabled(binding));
        vo.setCanManageSubscribers(isOwner);
        return vo;
    }

    private List<DeviceMemberItemVO> buildMemberItems(List<ParentDeviceBindingEntity> bindings) {
        List<DeviceMemberItemVO> result = new ArrayList<>();
        for (ParentDeviceBindingEntity b : bindings) {
            DeviceMemberItemVO item = new DeviceMemberItemVO();
            item.setParentId(b.getParentUserId());
            ParentUserEntity user = parentUserDao.selectById(b.getParentUserId());
            item.setNickname(ParentUserProfileHelper.resolveNickname(user));
            item.setAvatarUrl(ParentUserProfileHelper.resolveSharingAvatarUrl(user, parentStorageService));
            item.setRole(b.getRole());
            item.setIsPrimary(b.getIsPrimary() != null && b.getIsPrimary() == 1);
            item.setInvitedBy(b.getInvitedBy());
            item.setJoinedAt(b.getBindTime());
            boolean owner = ParentDeviceAccessHelper.isOwner(b);
            item.setReceiveRiskNotify(ParentDeviceAccessHelper.isReceiveRiskNotifyEnabled(b));
            item.setCanEdit(!owner);
            result.add(item);
        }
        return result;
    }
}
