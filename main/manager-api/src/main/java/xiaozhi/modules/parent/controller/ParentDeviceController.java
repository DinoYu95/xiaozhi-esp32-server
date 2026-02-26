package xiaozhi.modules.parent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dto.ParentDeviceBindDTO;
import xiaozhi.modules.parent.dto.ParentDeviceUnbindDTO;
import xiaozhi.modules.parent.service.ParentDeviceService;
import xiaozhi.modules.parent.vo.ParentDeviceItemVO;

@RestController
@RequestMapping("/parent-api/device")
@RequiredArgsConstructor
@Tag(name = "家长端-设备绑定")
public class ParentDeviceController {

    private final ParentDeviceService parentDeviceService;

    @PostMapping("/bind")
    @Operation(summary = "通过绑定码绑定设备")
    public Result<ParentDeviceService.BindResult> bind(@RequestBody ParentDeviceBindDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        ParentDeviceService.BindResult result = parentDeviceService.bind(parentUserId, dto);
        return new Result<ParentDeviceService.BindResult>().ok(result);
    }

    @PostMapping("/unbind")
    @Operation(summary = "解绑设备")
    public Result<Void> unbind(@RequestBody ParentDeviceUnbindDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        parentDeviceService.unbind(parentUserId, dto);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/list")
    @Operation(summary = "已绑定设备列表")
    public Result<List<ParentDeviceItemVO>> list() {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        List<ParentDeviceItemVO> list = parentDeviceService.list(parentUserId);
        return new Result<List<ParentDeviceItemVO>>().ok(list);
    }
}
