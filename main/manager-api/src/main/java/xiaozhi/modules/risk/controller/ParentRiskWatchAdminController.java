package xiaozhi.modules.risk.controller;

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
import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.dto.ParentRiskWatchAuditDTO;
import xiaozhi.modules.parent.service.ParentRiskWatchService;
import xiaozhi.modules.parent.vo.ParentRiskWatchVO;

@RestController
@RequestMapping("admin/parent-risk-watch")
@RequiredArgsConstructor
@Tag(name = "管理端-家长风险观察审核")
public class ParentRiskWatchAdminController {

    private final ParentRiskWatchService parentRiskWatchService;

    @GetMapping("/page")
    @Operation(summary = "家长观察分页")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<ParentRiskWatchVO>> page(@Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        int page = 1;
        int limit = 20;
        if (params != null && params.get(Constant.PAGE) != null) {
            try {
                page = Integer.parseInt(params.get(Constant.PAGE).toString());
            } catch (Exception ignored) {
            }
        }
        if (params != null && params.get(Constant.LIMIT) != null) {
            try {
                limit = Integer.parseInt(params.get(Constant.LIMIT).toString());
            } catch (Exception ignored) {
            }
        }
        String status = params != null && params.get("status") != null
                ? params.get("status").toString()
                : "pending";
        return new Result<PageData<ParentRiskWatchVO>>()
                .ok(parentRiskWatchService.adminPage(status, page, limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "观察详情")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ParentRiskWatchVO> get(@PathVariable Long id) {
        return new Result<ParentRiskWatchVO>().ok(parentRiskWatchService.adminGetDetail(id));
    }

    @PutMapping("/{id}/audit")
    @Operation(summary = "审核通过/拒绝")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("审核家长风险观察")
    public Result<Void> audit(@PathVariable Long id, @RequestBody @Valid ParentRiskWatchAuditDTO dto) {
        parentRiskWatchService.adminAudit(id, dto);
        return new Result<Void>().ok(null);
    }
}
