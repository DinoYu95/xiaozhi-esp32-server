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
import xiaozhi.modules.risk.vo.ChildRiskRulePublicVO;
import xiaozhi.modules.risk.vo.ChildRiskSignalResultVO;

/** zhiban-agent 等调用：Bearer server.secret */
@RestController
@RequestMapping("/config/child/risk")
@RequiredArgsConstructor
@Slf4j
public class ChildRiskInternalController {

    private final ChildRiskService childRiskService;

    @PostMapping("/signal")
    public Result<ChildRiskSignalResultVO> signal(@RequestBody ChildRiskSignalDTO body) {
        ChildRiskSignalResultVO vo = childRiskService.receiveSignal(body);
        return new Result<ChildRiskSignalResultVO>().ok(vo);
    }

    /** 下发启用的文本规则供 zhiban 本地扫描 */
    @GetMapping("/rules")
    public Result<List<ChildRiskRulePublicVO>> rules() {
        return new Result<List<ChildRiskRulePublicVO>>().ok(childRiskService.listEnabledRulesForAgent());
    }
}
