package xiaozhi.modules.parent.controller;

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
import xiaozhi.modules.parent.dto.ParentFeedbackAdminNoteDTO;
import xiaozhi.modules.parent.dto.ParentFeedbackAdminStatusDTO;
import xiaozhi.modules.parent.dto.ParentFeedbackBetaTesterDTO;
import xiaozhi.modules.parent.service.ParentFeedbackService;
import xiaozhi.modules.parent.vo.ParentFeedbackAdminVO;

@RestController
@RequestMapping("admin/feedback")
@RequiredArgsConstructor
@Tag(name = "管理端-内测反馈")
public class ParentFeedbackAdminController {

    private final ParentFeedbackService parentFeedbackService;

    @GetMapping("/page")
    @Operation(summary = "反馈分页列表")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("内测反馈分页")
    public Result<PageData<ParentFeedbackAdminVO>> page(
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        return new Result<PageData<ParentFeedbackAdminVO>>().ok(parentFeedbackService.adminPage(params));
    }

    @GetMapping("/{id}")
    @Operation(summary = "反馈详情")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ParentFeedbackAdminVO> get(@PathVariable Long id) {
        return new Result<ParentFeedbackAdminVO>().ok(parentFeedbackService.adminGet(id));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新状态与备注")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("更新内测反馈状态")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestBody @Valid ParentFeedbackAdminStatusDTO dto) {
        parentFeedbackService.adminUpdateStatus(id, dto);
        return new Result<Void>().ok(null);
    }

    @PutMapping("/{id}/note")
    @Operation(summary = "仅更新内部备注")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("更新内测反馈备注")
    public Result<Void> updateNote(
            @PathVariable Long id,
            @RequestBody @Valid ParentFeedbackAdminNoteDTO dto) {
        parentFeedbackService.adminUpdateNote(id, dto);
        return new Result<Void>().ok(null);
    }

    @PutMapping("/beta-tester")
    @Operation(summary = "设置家长是否为内测用户")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("设置内测用户")
    public Result<Void> setBetaTester(@RequestBody @Valid ParentFeedbackBetaTesterDTO dto) {
        parentFeedbackService.adminSetBetaTester(dto.getParentUserId(), Boolean.TRUE.equals(dto.getBetaTester()));
        return new Result<Void>().ok(null);
    }
}
