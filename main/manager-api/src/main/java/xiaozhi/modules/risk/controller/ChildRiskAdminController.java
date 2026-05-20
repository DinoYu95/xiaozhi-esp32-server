package xiaozhi.modules.risk.controller;

import java.util.List;
import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.risk.dto.ChildRiskConfigSaveDTO;
import xiaozhi.modules.risk.dto.ChildRiskEvaluatorSaveDTO;
import xiaozhi.modules.risk.dto.ChildRiskRuleSaveDTO;
import xiaozhi.modules.risk.service.ChildRiskService;
import xiaozhi.modules.risk.vo.ChildRiskConfigVO;
import xiaozhi.modules.risk.vo.ChildRiskDomainVO;
import xiaozhi.modules.risk.vo.ChildRiskEvaluatorPublicVO;
import xiaozhi.modules.risk.vo.ChildRiskEventAdminVO;
import xiaozhi.modules.risk.vo.ChildRiskRulePublicVO;

@RestController
@RequestMapping("admin/child-risk")
@RequiredArgsConstructor
@Tag(name = "管理端-儿童风险")
public class ChildRiskAdminController {

    private final ChildRiskService childRiskService;

    @GetMapping("/config")
    @Operation(summary = "儿童风险全局配置")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ChildRiskConfigVO> getChildRiskConfig() {
        return new Result<ChildRiskConfigVO>().ok(childRiskService.getAdminChildRiskConfig());
    }

    @PutMapping("/config")
    @Operation(summary = "保存儿童风险全局配置")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("保存儿童风险全局配置")
    public Result<Void> saveChildRiskConfig(@RequestBody @Valid ChildRiskConfigSaveDTO dto) {
        childRiskService.saveAdminChildRiskConfig(dto);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/event/page")
    @Operation(summary = "事件分页（只读）")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("儿童风险事件分页")
    public Result<PageData<ChildRiskEventAdminVO>> eventPage(@Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        long page = 1;
        long limit = 20;
        if (params != null && params.get(Constant.PAGE) != null) {
            try {
                page = Long.parseLong(params.get(Constant.PAGE).toString());
            } catch (Exception ignored) {
            }
        }
        if (params != null && params.get(Constant.LIMIT) != null) {
            try {
                limit = Long.parseLong(params.get(Constant.LIMIT).toString());
            } catch (Exception ignored) {
            }
        }
        return new Result<PageData<ChildRiskEventAdminVO>>()
                .ok(childRiskService.pageEvents((int) page, (int) limit));
    }

    @GetMapping("/rule/list")
    @Operation(summary = "全部规则（管理）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<ChildRiskRulePublicVO>> ruleList() {
        return new Result<List<ChildRiskRulePublicVO>>().ok(childRiskService.listAllRulesForAdmin());
    }

    @PostMapping("/rule")
    @Operation(summary = "新增或保存规则")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("保存儿童风险规则")
    public Result<Void> saveRule(@RequestBody @Valid ChildRiskRuleSaveDTO dto) {
        childRiskService.saveOrUpdateRule(dto);
        return new Result<Void>().ok(null);
    }

    @DeleteMapping("/rule")
    @Operation(summary = "删除规则")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("删除儿童风险规则")
    public Result<Void> deleteRule(@RequestParam Long id) {
        childRiskService.deleteRule(id);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/evaluator/list")
    @Operation(summary = "全部领域判别器")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<ChildRiskEvaluatorPublicVO>> evaluatorList() {
        return new Result<List<ChildRiskEvaluatorPublicVO>>().ok(childRiskService.listAllEvaluatorsForAdmin());
    }

    @GetMapping("/domain/list")
    @Operation(summary = "风险领域目录")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<ChildRiskDomainVO>> domainList() {
        return new Result<List<ChildRiskDomainVO>>().ok(childRiskService.listRiskDomains());
    }

    @PostMapping("/evaluator")
    @Operation(summary = "新增或保存领域判别器")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("保存儿童风险判别器")
    public Result<Void> saveEvaluator(@RequestBody @Valid ChildRiskEvaluatorSaveDTO dto) {
        childRiskService.saveOrUpdateEvaluator(dto);
        return new Result<Void>().ok(null);
    }

    @DeleteMapping("/evaluator")
    @Operation(summary = "删除领域判别器")
    @RequiresPermissions("sys:role:superAdmin")
    @LogOperation("删除儿童风险判别器")
    public Result<Void> deleteEvaluator(@RequestParam Long id) {
        childRiskService.deleteEvaluator(id);
        return new Result<Void>().ok(null);
    }
}
