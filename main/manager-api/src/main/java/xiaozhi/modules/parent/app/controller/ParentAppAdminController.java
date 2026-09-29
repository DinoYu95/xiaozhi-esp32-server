package xiaozhi.modules.parent.app.controller;

import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.annotation.LogOperation;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.app.dto.ParentAppAdminPublicConfigSaveDTO;
import xiaozhi.modules.parent.app.dto.ParentAppAdminSettingsSaveDTO;
import xiaozhi.modules.parent.app.dto.ParentBetaAllowlistSaveDTO;
import xiaozhi.modules.parent.app.service.ParentAppConfigService;
import xiaozhi.modules.parent.app.service.ParentBetaAllowlistService;
import xiaozhi.modules.parent.app.vo.ParentAppAdminSettingsVO;
import xiaozhi.modules.parent.app.vo.ParentBetaAllowlistVO;

@RestController
@RequestMapping("admin/parent-app")
@RequiredArgsConstructor
@Tag(name = "管理端-小程序审核与内测登录")
public class ParentAppAdminController {

    private final ParentAppConfigService parentAppConfigService;
    private final ParentBetaAllowlistService parentBetaAllowlistService;

    @GetMapping("/settings")
    @Operation(summary = "读取访问模式与公开文案")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ParentAppAdminSettingsVO> getSettings() {
        return new Result<ParentAppAdminSettingsVO>().ok(parentAppConfigService.adminGetSettings());
    }

    @PutMapping("/settings")
    @Operation(summary = "保存 accessMode / reviewMode")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("保存小程序访问模式")
    public Result<Void> saveSettings(@RequestBody @Valid ParentAppAdminSettingsSaveDTO dto) {
        parentAppConfigService.adminSaveSettings(dto);
        return new Result<Void>().ok(null);
    }

    @PutMapping("/public-config")
    @Operation(summary = "保存游客 Banner / Tab 文案")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("保存小程序公开文案")
    public Result<Void> savePublicConfig(@RequestBody @Valid ParentAppAdminPublicConfigSaveDTO dto) {
        parentAppConfigService.adminSavePublicConfig(dto);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/allowlist/page")
    @Operation(summary = "内测登录白名单分页")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<ParentBetaAllowlistVO>> allowlistPage(
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        return new Result<PageData<ParentBetaAllowlistVO>>().ok(parentBetaAllowlistService.adminPage(params));
    }

    @PostMapping("/allowlist")
    @Operation(summary = "新增或更新白名单")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("保存小程序登录白名单")
    public Result<Void> saveAllowlist(@RequestBody @Valid ParentBetaAllowlistSaveDTO dto) {
        parentBetaAllowlistService.adminSave(dto);
        return new Result<Void>().ok(null);
    }

    @DeleteMapping("/allowlist/{id}")
    @Operation(summary = "删除白名单")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("删除小程序登录白名单")
    public Result<Void> deleteAllowlist(@PathVariable Long id) {
        parentBetaAllowlistService.adminDelete(id);
        return new Result<Void>().ok(null);
    }
}
