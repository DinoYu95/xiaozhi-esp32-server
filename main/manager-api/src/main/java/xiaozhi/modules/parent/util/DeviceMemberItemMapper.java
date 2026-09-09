package xiaozhi.modules.parent.util;

import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.storage.ParentStorageService;
import xiaozhi.modules.parent.vo.DeviceMemberItemVO;

public final class DeviceMemberItemMapper {

    private DeviceMemberItemMapper() {
    }

    public static DeviceMemberItemVO toItem(
            ParentDeviceBindingEntity binding,
            ParentUserEntity user,
            Long viewerParentUserId,
            boolean viewerIsOwner,
            ParentStorageService storage) {
        DeviceMemberItemVO item = new DeviceMemberItemVO();
        item.setParentId(binding.getParentUserId());
        item.setNickname(ParentUserProfileHelper.resolveNickname(user));
        item.setAvatarUrl(ParentUserProfileHelper.resolveSharingAvatarUrl(user, storage));
        item.setRole(binding.getRole());
        item.setIsPrimary(binding.getIsPrimary() != null && binding.getIsPrimary() == 1);
        item.setInvitedBy(binding.getInvitedBy());
        item.setJoinedAt(binding.getBindTime());
        item.setFamilyRole(binding.getFamilyRole());
        item.setFamilyRoleLabel(DeviceFamilyRole.resolveLabel(binding.getFamilyRole()));
        boolean owner = ParentDeviceAccessHelper.isOwner(binding);
        item.setReceiveRiskNotify(ParentDeviceAccessHelper.isReceiveRiskNotifyEnabled(binding));
        item.setCanEdit(!owner);
        boolean isSelf = viewerParentUserId != null && viewerParentUserId.equals(binding.getParentUserId());
        item.setCanEditFamilyRole(viewerIsOwner || isSelf);
        return item;
    }
}
