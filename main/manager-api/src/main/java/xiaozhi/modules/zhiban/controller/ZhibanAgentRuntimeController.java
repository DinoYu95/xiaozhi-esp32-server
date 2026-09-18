package xiaozhi.modules.zhiban.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.zhiban.service.ZhibanAgentConfigService;
import xiaozhi.modules.zhiban.vo.ZhibanAgentRuntimeVO;

/**
 * 供 zhiban-agent 拉取运行时配置（Bearer server.secret，走 /config/** 鉴权）。
 */
@RestController
@RequestMapping("config/zhiban")
@RequiredArgsConstructor
@Tag(name = "服务端-智伴 Agent 配置")
public class ZhibanAgentRuntimeController {

    private final ZhibanAgentConfigService zhibanAgentConfigService;

    @GetMapping("/runtime")
    @Operation(summary = "zhiban-agent 拉取运行时配置（含 LLM 解析结果）")
    public Result<ZhibanAgentRuntimeVO> runtime() {
        return new Result<ZhibanAgentRuntimeVO>().ok(zhibanAgentConfigService.getRuntimeConfig());
    }
}
