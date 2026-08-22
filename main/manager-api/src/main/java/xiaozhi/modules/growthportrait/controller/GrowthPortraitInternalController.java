package xiaozhi.modules.growthportrait.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.growthportrait.dto.GrowthEvidenceIngestDTO;
import xiaozhi.modules.growthportrait.dto.GrowthEvidenceSessionDTO;
import xiaozhi.modules.growthportrait.service.GrowthPortraitService;

@RestController
@RequestMapping("/config/growth-portrait")
@RequiredArgsConstructor
@Tag(name = "设备端-成长星图")
public class GrowthPortraitInternalController {

    private final GrowthPortraitService growthPortraitService;

    @PostMapping("/evidence")
    @Operation(summary = "写入成长观测证据（对话/任务结束回调）")
    public Result<Void> ingestEvidence(@RequestBody GrowthEvidenceIngestDTO body) {
        growthPortraitService.ingestEvidence(body);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/evidence/session")
    @Operation(summary = "会话结束后 batch 分析 transcript 并写入证据（LLM 漏斗）")
    public Result<Void> ingestSession(@RequestBody GrowthEvidenceSessionDTO body) {
        growthPortraitService.ingestSession(body);
        return new Result<Void>().ok(null);
    }
}
