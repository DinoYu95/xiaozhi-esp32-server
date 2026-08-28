package xiaozhi.modules.mindportrait.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.mindportrait.dto.MindEvidenceIngestDTO;
import xiaozhi.modules.mindportrait.dto.MindEvidenceSessionDTO;
import xiaozhi.modules.mindportrait.service.MindPortraitService;

@RestController
@RequestMapping("/config/mind-portrait")
@RequiredArgsConstructor
@Tag(name = "设备端-心绪图谱")
public class MindPortraitInternalController {

    private final MindPortraitService mindPortraitService;

    @PostMapping("/evidence")
    @Operation(summary = "写入成长观测证据（对话/任务结束回调）")
    public Result<Void> ingestEvidence(@RequestBody MindEvidenceIngestDTO body) {
        mindPortraitService.ingestEvidence(body);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/evidence/session")
    @Operation(summary = "会话结束后 batch 分析 transcript 并写入证据（LLM 漏斗）")
    public Result<Void> ingestSession(@RequestBody MindEvidenceSessionDTO body) {
        mindPortraitService.ingestSession(body);
        return new Result<Void>().ok(null);
    }
}
