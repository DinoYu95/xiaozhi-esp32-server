package xiaozhi.modules.parent.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dto.DeviceInviteAcceptDTO;
import xiaozhi.modules.parent.dto.DeviceInviteCreateDTO;
import xiaozhi.modules.parent.dto.DeviceInviteRevokeDTO;
import xiaozhi.modules.parent.dto.DeviceMemberLeaveDTO;
import xiaozhi.modules.parent.service.DeviceInviteService;
import xiaozhi.modules.parent.vo.DeviceInviteAcceptVO;
import xiaozhi.modules.parent.vo.DeviceInviteCreateVO;
import xiaozhi.modules.parent.vo.DeviceInviteItemVO;
import xiaozhi.modules.parent.vo.DeviceInvitePreviewVO;
import xiaozhi.modules.parent.vo.DeviceMemberItemVO;

@RestController
@RequestMapping("/parent-api/device")
@RequiredArgsConstructor
@Tag(name = "家长端-设备邀请与成员")
public class ParentDeviceInviteController {

    private final DeviceInviteService deviceInviteService;

    @PostMapping("/invite")
    @Operation(summary = "生成设备邀请（仅 Owner）")
    public Result<DeviceInviteCreateVO> createInvite(@RequestBody @Valid DeviceInviteCreateDTO dto) {
        Long parentUserId = requireParentUserId();
        if (StringUtils.isNotBlank(dto.getDeviceId())) {
            dto.setDeviceId(decodeDeviceId(dto.getDeviceId()));
        }
        return new Result<DeviceInviteCreateVO>().ok(deviceInviteService.createInvite(parentUserId, dto));
    }

    @GetMapping("/invite/preview")
    @Operation(summary = "预览邀请（接受前，需登录）")
    public Result<DeviceInvitePreviewVO> preview(@RequestParam("token") String token) {
        Long parentUserId = requireParentUserId();
        return new Result<DeviceInvitePreviewVO>().ok(deviceInviteService.preview(parentUserId, token));
    }

    @PostMapping("/invite/accept")
    @Operation(summary = "接受设备邀请")
    public Result<DeviceInviteAcceptVO> accept(@RequestBody @Valid DeviceInviteAcceptDTO dto) {
        Long parentUserId = requireParentUserId();
        return new Result<DeviceInviteAcceptVO>().ok(deviceInviteService.accept(parentUserId, dto));
    }

    @PostMapping("/invite/revoke")
    @Operation(summary = "撤销邀请（仅 Owner）")
    public Result<Void> revoke(@RequestBody DeviceInviteRevokeDTO dto) {
        Long parentUserId = requireParentUserId();
        deviceInviteService.revoke(parentUserId, dto);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/invites")
    @Operation(summary = "邀请单列表（仅 Owner）")
    public Result<List<DeviceInviteItemVO>> listInvites(
            @RequestParam("deviceId") String deviceId,
            @RequestParam(value = "status", required = false) String status) {
        Long parentUserId = requireParentUserId();
        return new Result<List<DeviceInviteItemVO>>().ok(
                deviceInviteService.listInvites(parentUserId, decodeDeviceId(deviceId), status));
    }

    @GetMapping("/members")
    @Operation(summary = "设备成员列表（仅 Owner）")
    public Result<List<DeviceMemberItemVO>> listMembers(@RequestParam("deviceId") String deviceId) {
        Long parentUserId = requireParentUserId();
        return new Result<List<DeviceMemberItemVO>>().ok(
                deviceInviteService.listMembers(parentUserId, decodeDeviceId(deviceId)));
    }

    @GetMapping("/sharing/member-avatars")
    @Operation(summary = "「我的」家庭共享入口成员头像（排除本人，最多 3 个）")
    public Result<List<String>> listSharingMemberAvatars() {
        Long parentUserId = requireParentUserId();
        return new Result<List<String>>().ok(deviceInviteService.listSharingMemberAvatars(parentUserId));
    }

    @DeleteMapping("/members")
    @Operation(summary = "移除成员（仅 Owner）")
    public Result<Void> removeMember(
            @RequestParam("deviceId") String deviceId,
            @RequestParam("parentId") Long parentId) {
        Long parentUserId = requireParentUserId();
        deviceInviteService.removeMember(parentUserId, decodeDeviceId(deviceId), parentId);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/members/leave")
    @Operation(summary = "Member 主动退出设备共享")
    public Result<Void> leave(@RequestBody @Valid DeviceMemberLeaveDTO dto) {
        Long parentUserId = requireParentUserId();
        if (StringUtils.isNotBlank(dto.getDeviceId())) {
            dto.setDeviceId(decodeDeviceId(dto.getDeviceId()));
        }
        deviceInviteService.leave(parentUserId, dto);
        return new Result<Void>().ok(null);
    }

    private static Long requireParentUserId() {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return parentUserId;
    }

    private static String decodeDeviceId(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return deviceId;
        }
        try {
            String prev = deviceId;
            for (int i = 0; i < 3; i++) {
                String decoded = URLDecoder.decode(prev, StandardCharsets.UTF_8);
                if (decoded.equals(prev)) {
                    break;
                }
                prev = decoded;
            }
            return prev;
        } catch (IllegalArgumentException e) {
            return deviceId;
        }
    }
}
