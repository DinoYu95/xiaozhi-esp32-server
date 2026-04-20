package xiaozhi.modules.parent.controller;

import java.util.List;

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
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dto.ParentShadowMissionCancelAllDTO;
import xiaozhi.modules.parent.dto.ParentShadowMissionCreateDTO;
import xiaozhi.modules.parent.dto.ParentShadowMissionUpdateDTO;
import xiaozhi.modules.parent.service.ParentShadowMissionService;
import xiaozhi.modules.parent.vo.ParentShadowMissionActiveVO;
import xiaozhi.modules.parent.vo.ParentShadowMissionDetailVO;
import xiaozhi.modules.parent.vo.ParentShadowMissionPageVO;
import xiaozhi.modules.parent.vo.ParentShadowMissionUpsertResultVO;

/**
 * 家长小程序：布置给孩子的「影子任务」管理（与 /config/parent 内部接口区分，本组走家长 Token）。
 */
@RestController
@RequestMapping("/parent-api/shadow-mission")
@RequiredArgsConstructor
@Tag(name = "家长端-影子任务")
public class ParentShadowMissionController {

    private final ParentShadowMissionService parentShadowMissionService;

    private static Long requireParentUserId() {
        Long id = ParentContext.getParentUserId();
        if (id == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return id;
    }

    @GetMapping("/active")
    @Operation(summary = "进行中任务列表（不含分页，最多 5 条）")
    public Result<List<ParentShadowMissionActiveVO>> listActive(
            @Parameter(description = "孩子主键 device_child.id", required = true) @RequestParam Long childId) {
        Long parentUserId = requireParentUserId();
        List<ParentShadowMissionActiveVO> list = parentShadowMissionService.listActiveForParent(parentUserId, childId);
        return new Result<List<ParentShadowMissionActiveVO>>().ok(list);
    }

    @GetMapping
    @Operation(summary = "分页查询任务（可按状态筛选）")
    public Result<ParentShadowMissionPageVO> page(
            @Parameter(description = "孩子主键 device_child.id", required = true) @RequestParam Long childId,
            @Parameter(description = "状态：active/cancelled/expired/completed，不传则全部") @RequestParam(required = false) String status,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数，最大 100") @RequestParam(defaultValue = "20") int pageSize) {
        Long parentUserId = requireParentUserId();
        ParentShadowMissionPageVO vo =
                parentShadowMissionService.pageForParent(parentUserId, childId, status, page, pageSize);
        return new Result<ParentShadowMissionPageVO>().ok(vo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "任务详情")
    public Result<ParentShadowMissionDetailVO> detail(@PathVariable("id") Long id) {
        Long parentUserId = requireParentUserId();
        ParentShadowMissionDetailVO vo = parentShadowMissionService.getDetailForParent(parentUserId, id);
        return new Result<ParentShadowMissionDetailVO>().ok(vo);
    }

    @PostMapping
    @Operation(summary = "新建任务")
    public Result<ParentShadowMissionUpsertResultVO> create(@RequestBody @Valid ParentShadowMissionCreateDTO dto) {
        Long parentUserId = requireParentUserId();
        ParentShadowMissionUpsertResultVO vo = parentShadowMissionService.createForParent(parentUserId, dto);
        return new Result<ParentShadowMissionUpsertResultVO>().ok(vo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新进行中任务")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody @Valid ParentShadowMissionUpdateDTO dto) {
        Long parentUserId = requireParentUserId();
        parentShadowMissionService.updateForParent(parentUserId, id, dto);
        return new Result<Void>().ok(null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "取消单条进行中任务")
    public Result<Void> cancelOne(@PathVariable("id") Long id) {
        Long parentUserId = requireParentUserId();
        parentShadowMissionService.cancelOneForParent(parentUserId, id);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/cancel-all")
    @Operation(summary = "取消该孩子全部进行中任务")
    public Result<Void> cancelAll(@RequestBody @Valid ParentShadowMissionCancelAllDTO dto) {
        Long parentUserId = requireParentUserId();
        parentShadowMissionService.cancelAllForParent(parentUserId, dto.getChildId());
        return new Result<Void>().ok(null);
    }
}
