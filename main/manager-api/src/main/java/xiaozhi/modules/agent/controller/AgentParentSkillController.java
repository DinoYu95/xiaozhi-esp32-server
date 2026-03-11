package xiaozhi.modules.agent.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.service.AgentParentSkillService;
import xiaozhi.modules.agent.vo.AdminParentUserSkillVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 后台管理-家长端技能（查看/删除家长在小程序添加的技能）
 */
@Tag(name = "技能管理-家长端技能")
@RequiredArgsConstructor
@RestController
@RequestMapping("/agent/parent-skill")
public class AgentParentSkillController {

    private final AgentParentSkillService agentParentSkillService;

    @GetMapping("/list")
    @Operation(summary = "家长端技能列表（管理员查看）")
    @RequiresPermissions("sys:role:normal")
    public Result<List<AdminParentUserSkillVO>> list() {
        SecurityUser.getUserId();
        List<AdminParentUserSkillVO> list = agentParentSkillService.listAllForAdmin();
        return new Result<List<AdminParentUserSkillVO>>().ok(list);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除家长端技能（管理员）")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> delete(@PathVariable Long id) {
        SecurityUser.getUserId();
        agentParentSkillService.deleteByAdmin(id);
        return new Result<Void>().ok(null);
    }
}
