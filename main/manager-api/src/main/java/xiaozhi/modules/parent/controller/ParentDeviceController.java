package xiaozhi.modules.parent.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import xiaozhi.modules.parent.dto.ParentDeviceBindDTO;
import xiaozhi.modules.parent.dto.ParentDeviceNameUpdateDTO;
import xiaozhi.modules.parent.dto.ParentDeviceSkillBindDTO;
import xiaozhi.modules.parent.dto.ParentDeviceUnbindDTO;
import xiaozhi.modules.parent.dto.ParentDeviceRuleAddDTO;
import xiaozhi.modules.parent.service.ParentDeviceRuleService;
import xiaozhi.modules.parent.service.ParentDeviceService;
import xiaozhi.modules.parent.vo.ParentDeviceItemVO;
import xiaozhi.modules.parent.vo.ParentDeviceRuleVO;
import xiaozhi.modules.parent.vo.ParentDeviceSkillVO;

@RestController
@RequestMapping("/parent-api/device")
@RequiredArgsConstructor
@Tag(name = "家长端-设备绑定")
public class ParentDeviceController {

    private final ParentDeviceService parentDeviceService;
    private final ParentDeviceRuleService parentDeviceRuleService;

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

    @PutMapping(value = { "/{deviceId:.+}/name", "/name" })
    @Operation(summary = "修改设备名称（对话自称与列表展示均使用此名称，设备需重连后生效）")
    public Result<Void> updateDeviceName(
            @PathVariable(value = "deviceId", required = false) String pathDeviceId,
            @RequestParam(value = "deviceId", required = false) String queryDeviceId,
            @RequestBody @Valid ParentDeviceNameUpdateDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        String deviceId = pathDeviceId != null ? pathDeviceId : queryDeviceId;
        if (StringUtils.isBlank(deviceId)) {
            deviceId = dto.getDeviceId();
        }
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        deviceId = decodeDeviceId(deviceId);
        parentDeviceService.updateDeviceName(parentUserId, deviceId, dto);
        return new Result<Void>().ok(null);
    }

    @GetMapping(value = { "/{deviceId:.+}/skills", "/skills" })
    @Operation(summary = "获取设备下所有技能信息（path 或 query 传 deviceId，可含冒号如 B6:C8:35:D6:10:48）")
    public Result<List<ParentDeviceSkillVO>> listSkills(
            @PathVariable(value = "deviceId", required = false) String pathDeviceId,
            @RequestParam(value = "deviceId", required = false) String queryDeviceId) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        String deviceId = pathDeviceId != null ? pathDeviceId : queryDeviceId;
        if (pathDeviceId == null && queryDeviceId == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        deviceId = decodeDeviceId(deviceId);
        List<ParentDeviceSkillVO> list = parentDeviceService.listSkills(parentUserId, deviceId);
        return new Result<List<ParentDeviceSkillVO>>().ok(list);
    }

    @PostMapping(value = { "/{deviceId:.+}/skill/bind", "/skill/bind" })
    @Operation(summary = "绑定技能到设备（按 speaker 添加，支持官方/家长技能）")
    public Result<Void> bindSkill(
            @PathVariable(value = "deviceId", required = false) String pathDeviceId,
            @RequestParam(value = "deviceId", required = false) String queryDeviceId,
            @RequestBody @Valid ParentDeviceSkillBindDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        String deviceId = pathDeviceId != null ? pathDeviceId : queryDeviceId;
        if (StringUtils.isBlank(deviceId)) throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        deviceId = decodeDeviceId(deviceId);
        parentDeviceService.bindSkill(parentUserId, deviceId, dto);
        return new Result<Void>().ok(null);
    }

    @DeleteMapping(value = { "/{deviceId:.+}/skill/bind", "/skill/bind" })
    @Operation(summary = "解绑设备的某个技能（需指定 speakerType）")
    public Result<Void> unbindSkill(
            @PathVariable(value = "deviceId", required = false) String pathDeviceId,
            @RequestParam(value = "deviceId", required = false) String queryDeviceId,
            @RequestParam String skillSource,
            @RequestParam Object skillId,
            @RequestParam String speakerType) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        String deviceId = pathDeviceId != null ? pathDeviceId : queryDeviceId;
        if (StringUtils.isBlank(deviceId)) throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        deviceId = decodeDeviceId(deviceId);
        parentDeviceService.unbindSkill(parentUserId, deviceId, skillSource, skillId, speakerType);
        return new Result<Void>().ok(null);
    }

    @GetMapping(value = { "/{deviceId:.+}/rules", "/rules" })
    @Operation(summary = "获取设备的家长规则列表")
    public Result<List<ParentDeviceRuleVO>> listRules(
            @PathVariable(value = "deviceId", required = false) String pathDeviceId,
            @RequestParam(value = "deviceId", required = false) String queryDeviceId) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        String deviceId = pathDeviceId != null ? pathDeviceId : queryDeviceId;
        if (StringUtils.isBlank(deviceId)) throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        deviceId = decodeDeviceId(deviceId);
        List<ParentDeviceRuleVO> list = parentDeviceRuleService.listByDevice(parentUserId, deviceId);
        return new Result<List<ParentDeviceRuleVO>>().ok(list);
    }

    @PostMapping(value = { "/{deviceId:.+}/rules", "/rules" })
    @Operation(summary = "为设备添加一条家长规则")
    public Result<ParentDeviceRuleVO> addRule(
            @PathVariable(value = "deviceId", required = false) String pathDeviceId,
            @RequestParam(value = "deviceId", required = false) String queryDeviceId,
            @RequestBody @Valid ParentDeviceRuleAddDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        String deviceId = pathDeviceId != null ? pathDeviceId : queryDeviceId;
        if (StringUtils.isBlank(deviceId)) deviceId = dto.getDeviceId();
        if (StringUtils.isBlank(deviceId)) throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        deviceId = decodeDeviceId(deviceId);
        dto.setDeviceId(deviceId);
        ParentDeviceRuleVO vo = parentDeviceRuleService.add(parentUserId, dto);
        return new Result<ParentDeviceRuleVO>().ok(vo);
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除一条家长规则")
    public Result<Void> deleteRule(@PathVariable Long id) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        parentDeviceRuleService.delete(parentUserId, id);
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
