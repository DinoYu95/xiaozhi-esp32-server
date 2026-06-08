package xiaozhi.modules.parent.beta.controller;

import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.annotation.LogOperation;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.beta.dto.BetaMissionAdminConfigSaveDTO;
import xiaozhi.modules.parent.beta.service.BetaMissionService;
import xiaozhi.modules.parent.beta.vo.BetaMissionAdminConfigVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionFunnelVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionUserDetailVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionUserProgressVO;

@RestController
@RequestMapping("admin/beta-mission")
@RequiredArgsConstructor
@Tag(name = "管理端-内测体验任务")
public class BetaMissionAdminController {

    private final BetaMissionService betaMissionService;

    @GetMapping("/config")
    @Operation(summary = "运行配置")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<BetaMissionAdminConfigVO> getConfig() {
        return new Result<BetaMissionAdminConfigVO>().ok(betaMissionService.adminGetConfig());
    }

    @PutMapping("/config")
    @Operation(summary = "保存总开关")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("保存内测任务开关")
    public Result<Void> saveConfig(@RequestBody @Valid BetaMissionAdminConfigSaveDTO dto) {
        betaMissionService.adminSaveConfig(dto);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/funnel")
    @Operation(summary = "漏斗统计")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<BetaMissionFunnelVO> funnel() {
        return new Result<BetaMissionFunnelVO>().ok(betaMissionService.adminFunnel());
    }

    @GetMapping("/users")
    @Operation(summary = "用户进度分页")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<BetaMissionUserProgressVO>> users(
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        return new Result<PageData<BetaMissionUserProgressVO>>().ok(betaMissionService.adminUsers(params));
    }

    @GetMapping("/users/{parentUserId}")
    @Operation(summary = "用户进度详情")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<BetaMissionUserDetailVO> userDetail(@PathVariable Long parentUserId) {
        return new Result<BetaMissionUserDetailVO>().ok(betaMissionService.adminUserDetail(parentUserId));
    }
}
