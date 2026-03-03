package xiaozhi.modules.agent.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.dto.AgentSkillMappingItemDTO;
import xiaozhi.modules.agent.service.AgentSkillMappingService;
import xiaozhi.modules.agent.vo.AgentSkillMappingVO;
import xiaozhi.modules.security.user.SecurityUser;

@Tag(name = "智能体说话人→技能映射（多角色智伴）")
@AllArgsConstructor
@RestController
@RequestMapping("/agent")
public class AgentSkillMappingController {

    private final AgentSkillMappingService agentSkillMappingService;

    @GetMapping("/{agentId}/skill-mapping")
    @Operation(summary = "获取智能体的说话人类型→技能映射")
    @RequiresPermissions("sys:role:normal")
    public Result<List<AgentSkillMappingVO>> getMapping(@PathVariable String agentId) {
        SecurityUser.getUserId();
        List<AgentSkillMappingVO> list = agentSkillMappingService.listByAgentId(agentId);
        return new Result<List<AgentSkillMappingVO>>().ok(list);
    }

    @PutMapping("/{agentId}/skill-mapping")
    @Operation(summary = "保存智能体的说话人类型→技能映射")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> saveMapping(@PathVariable String agentId, @RequestBody @Valid List<AgentSkillMappingItemDTO> items) {
        SecurityUser.getUserId();
        agentSkillMappingService.saveMapping(agentId, items);
        return new Result<>();
    }
}
