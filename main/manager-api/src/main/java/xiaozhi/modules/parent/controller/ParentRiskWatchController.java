package xiaozhi.modules.parent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dto.ParentRiskPreferenceSaveDTO;
import xiaozhi.modules.parent.dto.ParentRiskWatchCreateDTO;
import xiaozhi.modules.parent.dto.ParentRiskWatchFromIntentDTO;
import xiaozhi.modules.parent.service.ParentRiskWatchService;
import xiaozhi.modules.parent.vo.ParentRiskPreferenceVO;
import xiaozhi.modules.parent.vo.ParentRiskWatchDraftVO;
import xiaozhi.modules.parent.vo.ParentRiskWatchOverviewVO;
import xiaozhi.modules.parent.vo.ParentRiskWatchVO;
import xiaozhi.modules.risk.service.ChildRiskService;
import xiaozhi.modules.risk.vo.ChildRiskDomainVO;

import java.util.List;

@RestController
@RequestMapping("/parent-api/risk-watch")
@RequiredArgsConstructor
@Tag(name = "家长端-风险观察")
public class ParentRiskWatchController {

    private final ParentRiskWatchService parentRiskWatchService;
    private final ChildRiskService childRiskService;

    @GetMapping("/domains")
    @Operation(summary = "风险领域目录（只读）")
    public Result<List<ChildRiskDomainVO>> domains() {
        return new Result<List<ChildRiskDomainVO>>().ok(childRiskService.listRiskDomains());
    }

    @GetMapping("/overview")
    @Operation(summary = "某孩子的关注侧重与我的观察列表")
    public Result<ParentRiskWatchOverviewVO> overview(@RequestParam Long childId) {
        return new Result<ParentRiskWatchOverviewVO>()
                .ok(parentRiskWatchService.getOverview(requireParent(), childId));
    }

    @PutMapping("/preference")
    @Operation(summary = "保存关注侧重（多选领域）")
    public Result<ParentRiskPreferenceVO> savePreference(@RequestBody @Valid ParentRiskPreferenceSaveDTO dto) {
        return new Result<ParentRiskPreferenceVO>()
                .ok(parentRiskWatchService.savePreference(requireParent(), dto));
    }

    @PostMapping("/draft-from-intent")
    @Operation(summary = "AI 生成观察草稿")
    public Result<ParentRiskWatchDraftVO> draftFromIntent(@RequestBody @Valid ParentRiskWatchFromIntentDTO dto) {
        return new Result<ParentRiskWatchDraftVO>()
                .ok(parentRiskWatchService.draftFromIntent(requireParent(), dto));
    }

    @PostMapping
    @Operation(summary = "提交观察（进入待审核）")
    public Result<ParentRiskWatchVO> create(@RequestBody @Valid ParentRiskWatchCreateDTO dto) {
        return new Result<ParentRiskWatchVO>().ok(parentRiskWatchService.create(requireParent(), dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "观察详情")
    public Result<ParentRiskWatchVO> detail(@PathVariable Long id) {
        return new Result<ParentRiskWatchVO>().ok(parentRiskWatchService.getDetail(requireParent(), id));
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "停用或撤回待审核观察")
    public Result<Void> disable(@PathVariable Long id) {
        parentRiskWatchService.disable(requireParent(), id);
        return new Result<Void>().ok(null);
    }

    private static Long requireParent() {
        Long id = ParentContext.getParentUserId();
        if (id == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return id;
    }
}
