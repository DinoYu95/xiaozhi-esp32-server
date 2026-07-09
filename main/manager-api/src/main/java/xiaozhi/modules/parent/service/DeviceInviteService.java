package xiaozhi.modules.parent.service;

import java.util.List;

import xiaozhi.modules.parent.dto.DeviceInviteAcceptDTO;
import xiaozhi.modules.parent.dto.DeviceInviteCreateDTO;
import xiaozhi.modules.parent.dto.DeviceInviteRevokeDTO;
import xiaozhi.modules.parent.dto.DeviceMemberLeaveDTO;
import xiaozhi.modules.parent.vo.DeviceInviteAcceptVO;
import xiaozhi.modules.parent.vo.DeviceInviteCreateVO;
import xiaozhi.modules.parent.vo.DeviceInviteItemVO;
import xiaozhi.modules.parent.vo.DeviceInvitePreviewVO;
import xiaozhi.modules.parent.vo.DeviceMemberItemVO;

public interface DeviceInviteService {

    int INVITE_EXPIRE_DAYS = 3;
    int INVITE_MAX_USES = 1;
    int INVITE_RATE_LIMIT_PER_HOUR = 10;

    DeviceInviteCreateVO createInvite(Long parentUserId, DeviceInviteCreateDTO dto);

    DeviceInvitePreviewVO preview(Long parentUserId, String inviteToken);

    DeviceInviteAcceptVO accept(Long parentUserId, DeviceInviteAcceptDTO dto);

    List<DeviceMemberItemVO> listMembers(Long parentUserId, String deviceId);

    /**
     * 「我的」Tab 家庭共享入口：排除本人、跨设备按 parentId 去重，最多 3 个有头像的成员 URL。
     */
    List<String> listSharingMemberAvatars(Long parentUserId);

    void removeMember(Long ownerParentUserId, String deviceId, Long targetParentId);

    void leave(Long parentUserId, DeviceMemberLeaveDTO dto);

    void revoke(Long parentUserId, DeviceInviteRevokeDTO dto);

    List<DeviceInviteItemVO> listInvites(Long parentUserId, String deviceId, String status);

    void revokeActiveInvitesForDevice(String deviceId);
}
