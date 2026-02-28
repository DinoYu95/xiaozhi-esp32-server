package xiaozhi.modules.parent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.vo.AgentVoicePrintVO;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dto.ChildVoicePrintSaveDTO;
import xiaozhi.modules.parent.service.ParentDeviceChildVoicePrintService;

@RestController
@RequestMapping("/parent-api/device/child/voiceprint")
@RequiredArgsConstructor
@Tag(name = "家长端-设备主孩子声纹")
public class ParentDeviceChildVoicePrintController {

    private final ParentDeviceChildVoicePrintService parentDeviceChildVoicePrintService;

    @PostMapping("/upload")
    @Operation(summary = "上传孩子声纹音频（WAV），返回 audioId 供保存声纹使用")
    public Result<String> upload(
            @Parameter(description = "设备ID", required = true) @RequestParam String deviceId,
            @Parameter(description = "WAV 音频文件", required = true) @RequestParam MultipartFile file) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        String audioId = parentDeviceChildVoicePrintService.uploadAudio(parentUserId, deviceId, file);
        return new Result<String>().ok(audioId);
    }

    @PostMapping
    @Operation(summary = "添加或更新主孩子声纹（一孩一声纹）")
    public Result<Void> save(@RequestBody @Valid ChildVoicePrintSaveDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        parentDeviceChildVoicePrintService.saveVoicePrint(parentUserId, dto);
        return new Result<Void>().ok(null);
    }

    @GetMapping
    @Operation(summary = "查询该设备主孩子的声纹列表（0 或 1 条）")
    public Result<List<AgentVoicePrintVO>> list(
            @Parameter(description = "设备ID", required = true) @RequestParam String deviceId) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        List<AgentVoicePrintVO> list = parentDeviceChildVoicePrintService.listVoicePrint(parentUserId, deviceId);
        return new Result<List<AgentVoicePrintVO>>().ok(list);
    }

    @DeleteMapping
    @Operation(summary = "删除主孩子声纹")
    public Result<Void> delete(
            @Parameter(description = "声纹ID", required = true) @RequestParam String voicePrintId) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        parentDeviceChildVoicePrintService.deleteVoicePrint(parentUserId, voicePrintId);
        return new Result<Void>().ok(null);
    }
}
