package xiaozhi.modules.risk.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.risk.dto.ChildRiskSignalDTO;
import xiaozhi.modules.risk.service.ChildRiskService;
import xiaozhi.modules.risk.vo.ChildRiskAgentRuntimeVO;
import xiaozhi.modules.risk.vo.ChildRiskDomainVO;
import xiaozhi.modules.risk.vo.ChildRiskEvaluatorPublicVO;
import xiaozhi.modules.risk.vo.ChildRiskRulePublicVO;
import xiaozhi.modules.risk.vo.ChildRiskSignalResultVO;

/** zhiban-agent 等调用：Bearer server.secret */
@RestController
@RequestMapping("/config/child/risk")
@RequiredArgsConstructor
@Slf4j
public class ChildRiskInternalController {

    private final ChildRiskService childRiskService;

    /** 智伴拉取：每 N 轮扫描、总开关（与参数页 evalEveryNRounds 一致） */
    @GetMapping("/runtime")
    public Result<ChildRiskAgentRuntimeVO> runtime() {
        return new Result<ChildRiskAgentRuntimeVO>().ok(childRiskService.getAgentRiskRuntime());
    }

    @PostMapping("/signal")
    public Result<ChildRiskSignalResultVO> signal(@RequestBody ChildRiskSignalDTO body) {
        ChildRiskSignalResultVO vo = childRiskService.receiveSignal(body);
        log.info(
                "POST /config/child/risk/signal childId={} riskLevel={} category={} needAlert={} suppressed={} eventId={} reason={}",
                body != null ? body.getChildId() : null,
                body != null ? body.getRiskLevel() : null,
                body != null ? body.getCategory() : null,
                body != null ? body.getNeedAlert() : null,
                vo != null && vo.isSuppressed(),
                vo != null ? vo.getEventId() : null,
                vo != null ? vo.getSuppressedReason() : null);
        return new Result<ChildRiskSignalResultVO>().ok(vo);
    }

    /** 下发启用的文本规则供 zhiban 本地扫描 */
    @GetMapping("/rules")
    public Result<List<ChildRiskRulePublicVO>> rules() {
        return new Result<List<ChildRiskRulePublicVO>>().ok(childRiskService.listEnabledRulesForAgent());
    }

    @GetMapping("/evaluators")
    public Result<List<ChildRiskEvaluatorPublicVO>> evaluators() {
        return new Result<List<ChildRiskEvaluatorPublicVO>>().ok(childRiskService.listEnabledEvaluatorsForAgent());
    }

    @GetMapping("/domains")
    public Result<List<ChildRiskDomainVO>> domains() {
        return new Result<List<ChildRiskDomainVO>>().ok(childRiskService.listRiskDomains());
    }
}
