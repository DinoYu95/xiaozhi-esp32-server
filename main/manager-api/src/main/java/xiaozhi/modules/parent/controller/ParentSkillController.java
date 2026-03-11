package xiaozhi.modules.parent.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.service.AgentSkillService;
import xiaozhi.modules.agent.vo.AgentSkillVO;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.service.ParentDeviceService;
import xiaozhi.modules.parent.dto.ParentUserSkillSaveDTO;
import xiaozhi.modules.parent.service.ParentUserSkillService;
import xiaozhi.modules.parent.vo.ParentSkillSearchVO;
import xiaozhi.modules.parent.vo.ParentUserSkillVO;

@RestController
@RequestMapping("/parent-api/skill")
@RequiredArgsConstructor
@Tag(name = "家长端-技能")
public class ParentSkillController {

    private final AgentSkillService agentSkillService;
    private final ParentUserSkillService parentUserSkillService;
    private final ParentDeviceService parentDeviceService;

    @GetMapping("/list")
    @Operation(summary = "官方推荐的技能列表（管理员在后台添加且标记推荐的）")
    public Result<List<AgentSkillVO>> listRecommended() {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        List<AgentSkillVO> list = agentSkillService.listOfficialRecommended();
        return new Result<List<AgentSkillVO>>().ok(list);
    }

    @GetMapping("/search")
    @Operation(summary = "按关键词模糊搜索技能（家长自定义 + 官方推荐，结构分开；传 deviceId 时排除已绑定到该设备的）")
    public Result<ParentSkillSearchVO> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deviceId) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        List<Object> boundIds = null;
        if (StringUtils.isNotBlank(deviceId)) {
            String decodedDeviceId = decodeDeviceId(deviceId);
            boundIds = parentDeviceService.listBoundSkillIds(parentUserId, decodedDeviceId);
        }
        List<ParentUserSkillVO> parentSkills = parentUserSkillService.searchByParentUserId(parentUserId, keyword);
        List<AgentSkillVO> recommendedSkills = agentSkillService.searchOfficialRecommended(keyword);
        if (boundIds != null && !boundIds.isEmpty()) {
            final List<Object> excluded = boundIds;
            parentSkills = parentSkills == null ? List.of() : parentSkills.stream()
                    .filter(s -> s.getId() != null && !excluded.contains(s.getId()))
                    .collect(Collectors.toList());
            recommendedSkills = recommendedSkills == null ? List.of() : recommendedSkills.stream()
                    .filter(s -> s.getId() != null && !excluded.contains(s.getId()))
                    .collect(Collectors.toList());
        }
        ParentSkillSearchVO vo = new ParentSkillSearchVO();
        vo.setParentSkills(parentSkills);
        vo.setRecommendedSkills(recommendedSkills);
        return new Result<ParentSkillSearchVO>().ok(vo);
    }

    @GetMapping("/my-list")
    @Operation(summary = "当前家长自己添加的技能列表")
    public Result<List<ParentUserSkillVO>> listMySkills() {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        List<ParentUserSkillVO> list = parentUserSkillService.listByParentUserId(parentUserId);
        return new Result<List<ParentUserSkillVO>>().ok(list);
    }

    @PostMapping
    @Operation(summary = "创建技能（家长自定义）")
    public Result<ParentUserSkillVO> create(@RequestBody @Valid ParentUserSkillSaveDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        ParentUserSkillVO vo = parentUserSkillService.create(parentUserId, dto);
        return new Result<ParentUserSkillVO>().ok(vo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新技能（家长自定义，校验归属）")
    public Result<ParentUserSkillVO> update(@PathVariable Long id, @RequestBody @Valid ParentUserSkillSaveDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        ParentUserSkillVO vo = parentUserSkillService.update(parentUserId, id, dto);
        return new Result<ParentUserSkillVO>().ok(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除技能（家长自定义，校验归属）")
    public Result<Void> delete(@PathVariable Long id) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        parentUserSkillService.delete(parentUserId, id);
        return new Result<Void>().ok(null);
    }

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
