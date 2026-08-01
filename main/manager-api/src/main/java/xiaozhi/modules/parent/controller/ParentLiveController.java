package xiaozhi.modules.parent.controller;

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
import xiaozhi.modules.parent.dto.ParentLiveSessionIdDTO;
import xiaozhi.modules.parent.dto.ParentLiveStartDTO;
import xiaozhi.modules.parent.service.ParentLiveService;
import xiaozhi.modules.parent.vo.ParentLiveStartVO;
import xiaozhi.modules.parent.vo.ParentLiveStatusVO;

@RestController
@RequestMapping("/parent-api/live")
@RequiredArgsConstructor
@Tag(name = "家长端-远程实时监控")
public class ParentLiveController {

    private final ParentLiveService parentLiveService;

    @PostMapping("/start")
    @Operation(summary = "开始远程查看")
    public Result<ParentLiveStartVO> start(@RequestBody @Valid ParentLiveStartDTO dto) {
        Long parentUserId = requireParentUserId();
        return new Result<ParentLiveStartVO>().ok(parentLiveService.start(parentUserId, dto));
    }

    @PostMapping("/stop")
    @Operation(summary = "停止远程查看")
    public Result<ParentLiveStatusVO> stop(@RequestBody @Valid ParentLiveSessionIdDTO dto) {
        Long parentUserId = requireParentUserId();
        return new Result<ParentLiveStatusVO>().ok(parentLiveService.stop(parentUserId, dto.getSessionId()));
    }

    @PostMapping("/heartbeat")
    @Operation(summary = "远程查看心跳")
    public Result<ParentLiveStatusVO> heartbeat(@RequestBody @Valid ParentLiveSessionIdDTO dto) {
        Long parentUserId = requireParentUserId();
        return new Result<ParentLiveStatusVO>().ok(parentLiveService.heartbeat(parentUserId, dto.getSessionId()));
    }

    @GetMapping("/status")
    @Operation(summary = "查询远程查看状态")
    public Result<ParentLiveStatusVO> status(@RequestParam Long sessionId) {
        Long parentUserId = requireParentUserId();
        return new Result<ParentLiveStatusVO>().ok(parentLiveService.getStatus(parentUserId, sessionId));
    }

    @GetMapping("/active")
    @Operation(summary = "查询设备当前活跃会话（本人发起）")
    public Result<ParentLiveStatusVO> active(@RequestParam String deviceId) {
        Long parentUserId = requireParentUserId();
        ParentLiveStatusVO vo = parentLiveService.getActiveForDevice(parentUserId, deviceId);
        return new Result<ParentLiveStatusVO>().ok(vo);
    }

    private static Long requireParentUserId() {
        Long id = ParentContext.getParentUserId();
        if (id == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return id;
    }
}
