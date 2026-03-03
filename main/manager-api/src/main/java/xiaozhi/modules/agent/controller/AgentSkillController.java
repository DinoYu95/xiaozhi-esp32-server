package xiaozhi.modules.agent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.dto.AgentSkillSaveDTO;
import xiaozhi.modules.agent.service.AgentSkillService;
import xiaozhi.modules.agent.vo.AgentSkillVO;
import xiaozhi.modules.security.user.SecurityUser;

@Tag(name = "技能管理（多角色智伴）")
@AllArgsConstructor
@RestController
@RequestMapping("/agent/skill")
public class AgentSkillController {

    private final AgentSkillService agentSkillService;

    @GetMapping("/list")
    @Operation(summary = "技能列表")
    @RequiresPermissions("sys:role:normal")
    public Result<List<AgentSkillVO>> list() {
        SecurityUser.getUserId();
        List<AgentSkillVO> list = agentSkillService.listAll();
        return new Result<List<AgentSkillVO>>().ok(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "技能详情")
    @RequiresPermissions("sys:role:normal")
    public Result<AgentSkillVO> get(@PathVariable String id) {
        SecurityUser.getUserId();
        AgentSkillVO vo = agentSkillService.getById(id);
        return vo == null ? new Result<AgentSkillVO>().error(404, "技能不存在") : new Result<AgentSkillVO>().ok(vo);
    }

    @PostMapping
    @Operation(summary = "创建技能")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> save(@RequestBody @Valid AgentSkillSaveDTO dto) {
        SecurityUser.getUserId();
        boolean ok = agentSkillService.saveSkill(dto);
        return ok ? new Result<Void>() : new Result<Void>().error(500, "创建失败或id已存在");
    }

    @PutMapping
    @Operation(summary = "更新技能")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> update(@RequestBody @Valid AgentSkillSaveDTO dto) {
        SecurityUser.getUserId();
        boolean ok = agentSkillService.updateSkill(dto);
        return ok ? new Result<Void>() : new Result<Void>().error(404, "技能不存在");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除技能")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> delete(@PathVariable String id) {
        SecurityUser.getUserId();
        boolean ok = agentSkillService.removeById(id);
        return ok ? new Result<Void>() : new Result<Void>().error(404, "技能不存在");
    }
}
