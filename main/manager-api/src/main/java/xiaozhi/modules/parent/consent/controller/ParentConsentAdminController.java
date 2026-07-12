package xiaozhi.modules.parent.consent.controller;

import java.util.List;
import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
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
import xiaozhi.modules.parent.consent.dto.ParentConsentAdminPublishDTO;
import xiaozhi.modules.parent.consent.dto.ParentConsentAdminSettingsDTO;
import xiaozhi.modules.parent.consent.service.ParentConsentService;
import xiaozhi.modules.parent.consent.vo.ParentConsentAdminOverviewVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentHistoryItemVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentPendingUserVO;

@RestController
@RequestMapping("admin/parent-consent")
@RequiredArgsConstructor
@Tag(name = "管理端-儿童隐私协议")
public class ParentConsentAdminController {

    private final ParentConsentService parentConsentService;

    @GetMapping("/overview")
    @Operation(summary = "概览（当前协议 + 开关 + 统计）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ParentConsentAdminOverviewVO> overview() {
        return new Result<ParentConsentAdminOverviewVO>().ok(parentConsentService.adminOverview());
    }

    @PutMapping("/settings")
    @Operation(summary = "保存开关与设备阻断配置")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("保存儿童隐私协议设置")
    public Result<Void> saveSettings(@RequestBody @Valid ParentConsentAdminSettingsDTO dto) {
        parentConsentService.adminSaveSettings(dto);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/publish")
    @Operation(summary = "发布新版本协议")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("发布儿童隐私协议新版本")
    public Result<ParentConsentAdminOverviewVO> publish(@RequestBody @Valid ParentConsentAdminPublishDTO dto) {
        parentConsentService.adminPublish(dto);
        return new Result<ParentConsentAdminOverviewVO>().ok(parentConsentService.adminOverview());
    }

    @GetMapping("/history")
    @Operation(summary = "历史版本")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<ParentConsentHistoryItemVO>> history() {
        return new Result<List<ParentConsentHistoryItemVO>>().ok(parentConsentService.adminHistory());
    }

    @GetMapping("/pending-users")
    @Operation(summary = "未签署当前版本家长分页")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<ParentConsentPendingUserVO>> pendingUsers(
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        return new Result<PageData<ParentConsentPendingUserVO>>().ok(parentConsentService.adminPendingUsers(params));
    }
}
