package xiaozhi.modules.parent.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dto.DeviceChildSaveDTO;
import xiaozhi.modules.parent.dto.DeviceChildUpdateDTO;
import xiaozhi.modules.parent.service.DeviceChildService;
import xiaozhi.modules.parent.vo.DeviceChildVO;

@RestController
@RequestMapping("/parent-api/device/child")
@RequiredArgsConstructor
@Tag(name = "家长端-设备主孩子")
public class ParentDeviceChildController {

    private final DeviceChildService deviceChildService;

    @PostMapping
    @Operation(summary = "添加或更新设备主孩子")
    public Result<DeviceChildVO> saveOrUpdate(@RequestBody @Valid DeviceChildSaveDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        DeviceChildVO vo = deviceChildService.saveOrUpdate(parentUserId, dto);
        return new Result<DeviceChildVO>().ok(vo);
    }

    @GetMapping
    @Operation(summary = "获取设备主孩子信息")
    public Result<DeviceChildVO> getByDeviceId(
            @Parameter(description = "设备ID", required = true) @RequestParam String deviceId) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        DeviceChildVO vo = deviceChildService.getByDeviceId(parentUserId, deviceId);
        return new Result<DeviceChildVO>().ok(vo);
    }

    @PutMapping
    @Operation(summary = "更新主孩子信息")
    public Result<Void> update(@RequestBody @Valid DeviceChildUpdateDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        deviceChildService.update(parentUserId, dto);
        return new Result<Void>().ok(null);
    }

    @DeleteMapping
    @Operation(summary = "解除设备主孩子")
    public Result<Void> deleteByDeviceId(
            @Parameter(description = "设备ID", required = true) @RequestParam String deviceId) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        deviceChildService.deleteByDeviceId(parentUserId, deviceId);
        return new Result<Void>().ok(null);
    }
}
