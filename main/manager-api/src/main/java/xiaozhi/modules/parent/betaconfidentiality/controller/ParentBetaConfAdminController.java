package xiaozhi.modules.parent.betaconfidentiality.controller;

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
import xiaozhi.modules.parent.betaconfidentiality.dto.ParentBetaConfAdminPublishDTO;
import xiaozhi.modules.parent.betaconfidentiality.dto.ParentBetaConfAdminSettingsDTO;
import xiaozhi.modules.parent.betaconfidentiality.service.ParentBetaConfService;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfAdminOverviewVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfHistoryItemVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfPendingUserVO;

@RestController
@RequestMapping("admin/parent-beta-confidentiality")
@RequiredArgsConstructor
@Tag(name = "管理端-内测保密协议")
public class ParentBetaConfAdminController {

    private final ParentBetaConfService parentBetaConfService;

    @GetMapping("/overview")
    @Operation(summary = "概览（当前协议 + 开关 + 统计）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ParentBetaConfAdminOverviewVO> overview() {
        return new Result<ParentBetaConfAdminOverviewVO>().ok(parentBetaConfService.adminOverview());
    }

    @PutMapping("/settings")
    @Operation(summary = "保存开关")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("保存内测保密协议设置")
    public Result<Void> saveSettings(@RequestBody @Valid ParentBetaConfAdminSettingsDTO dto) {
        parentBetaConfService.adminSaveSettings(dto);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/publish")
    @Operation(summary = "发布新版本内测保密协议")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("发布内测保密协议新版本")
    public Result<ParentBetaConfAdminOverviewVO> publish(@RequestBody @Valid ParentBetaConfAdminPublishDTO dto) {
        parentBetaConfService.adminPublish(dto);
        return new Result<ParentBetaConfAdminOverviewVO>().ok(parentBetaConfService.adminOverview());
    }

    @GetMapping("/history")
    @Operation(summary = "历史版本")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<ParentBetaConfHistoryItemVO>> history() {
        return new Result<List<ParentBetaConfHistoryItemVO>>().ok(parentBetaConfService.adminHistory());
    }

    @GetMapping("/pending-users")
    @Operation(summary = "未签署当前版本家长分页")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<ParentBetaConfPendingUserVO>> pendingUsers(
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        return new Result<PageData<ParentBetaConfPendingUserVO>>().ok(parentBetaConfService.adminPendingUsers(params));
    }
}
