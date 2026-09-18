package xiaozhi.modules.zhiban.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.annotation.LogOperation;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.zhiban.dto.ZhibanAgentConfigDTO;
import xiaozhi.modules.zhiban.service.ZhibanAgentConfigService;

@RestController
@RequestMapping("admin/zhiban-agent")
@RequiredArgsConstructor
@Tag(name = "管理端-智伴 Agent 配置")
public class ZhibanAgentAdminController {

    private final ZhibanAgentConfigService zhibanAgentConfigService;

    @GetMapping("/config")
    @Operation(summary = "获取智伴 Agent 配置")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ZhibanAgentConfigDTO> getConfig() {
        return new Result<ZhibanAgentConfigDTO>().ok(zhibanAgentConfigService.getAdminConfig());
    }

    @PutMapping("/config")
    @Operation(summary = "保存智伴 Agent 配置")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("保存智伴 Agent 配置")
    public Result<Void> saveConfig(@RequestBody ZhibanAgentConfigDTO dto) {
        zhibanAgentConfigService.saveAdminConfig(dto);
        return new Result<Void>().ok(null);
    }
}
