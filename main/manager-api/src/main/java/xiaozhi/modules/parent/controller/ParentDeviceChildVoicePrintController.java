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
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.vo.ParentDeviceVoicePrintVO;
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
        deviceId = decodeDeviceId(deviceId);
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
        if (StringUtils.isNotBlank(dto.getDeviceId())) {
            dto.setDeviceId(decodeDeviceId(dto.getDeviceId()));
        }
        parentDeviceChildVoicePrintService.saveVoicePrint(parentUserId, dto);
        return new Result<Void>().ok(null);
    }

    @GetMapping
    @Operation(summary = "查询该设备全部声纹（主孩子+后台），已录入几人=列表长度，canManage=true 的可编辑/删除")
    public Result<List<ParentDeviceVoicePrintVO>> list(
            @Parameter(description = "设备ID", required = true) @RequestParam String deviceId) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        deviceId = decodeDeviceId(deviceId);
        List<ParentDeviceVoicePrintVO> list = parentDeviceChildVoicePrintService.listVoicePrint(parentUserId, deviceId);
        return new Result<List<ParentDeviceVoicePrintVO>>().ok(list);
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

    /** 解码 deviceId（处理小程序端可能的 URL 双重编码，如 B6%3AC8%3A35 或 B6%253AC8%253A35 转为 B6:C8:35） */
    private static String decodeDeviceId(String deviceId) {
        if (StringUtils.isBlank(deviceId)) return deviceId;
        try {
            String prev = deviceId;
            for (int i = 0; i < 3; i++) {
                String decoded = URLDecoder.decode(prev, StandardCharsets.UTF_8);
                if (decoded.equals(prev)) break;
                prev = decoded;
            }
            return prev;
        } catch (IllegalArgumentException e) {
            return deviceId;
        }
    }
}
