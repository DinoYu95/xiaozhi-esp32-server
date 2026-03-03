package xiaozhi.modules.config.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.common.validator.ValidatorUtils;
import xiaozhi.modules.agent.service.AgentSkillService;
import xiaozhi.modules.agent.vo.AgentSkillVO;
import xiaozhi.modules.config.dto.AgentModelsDTO;
import xiaozhi.modules.config.service.ConfigService;

/**
 * xiaozhi-server 配置获取
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("config")
@Tag(name = "参数管理")
@AllArgsConstructor
public class ConfigController {
    private final ConfigService configService;
    private final AgentSkillService agentSkillService;

    @PostMapping("server-base")
    @Operation(summary = "服务端获取配置接口")
    public Result<Object> getConfig() {
        Object config = configService.getConfig(true);
        return new Result<Object>().ok(config);
    }

    @PostMapping("agent-models")
    @Operation(summary = "获取智能体模型")
    public Result<Object> getAgentModels(@Valid @RequestBody AgentModelsDTO dto) {
        // 效验数据
        ValidatorUtils.validateEntity(dto);
        Object models = configService.getAgentModels(dto.getMacAddress(), dto.getSelectedModule());
        return new Result<Object>().ok(models);
    }

    /**
     * 供 zhiban-agent 等服务端拉取技能 instructions（走 /config/**，使用 server secret 鉴权，无需用户登录）
     */
    @GetMapping("agent/skill/{id}")
    @Operation(summary = "按技能ID获取技能详情（服务端用 server secret 调用）")
    public Result<AgentSkillVO> getSkillById(@PathVariable String id) {
        AgentSkillVO vo = agentSkillService.getById(id);
        return vo == null ? new Result<AgentSkillVO>().error(404, "技能不存在") : new Result<AgentSkillVO>().ok(vo);
    }
}
