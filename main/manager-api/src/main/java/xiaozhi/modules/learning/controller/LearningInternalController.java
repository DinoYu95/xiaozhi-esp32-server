package xiaozhi.modules.learning.controller;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.learning.dto.LearningSessionEndDTO;
import xiaozhi.modules.learning.dto.LearningSessionPhotoDTO;
import xiaozhi.modules.learning.dto.LearningSessionStartDTO;
import xiaozhi.modules.learning.dto.LearningSessionTurnDTO;
import xiaozhi.modules.learning.service.LearningSessionService;

/**
 * xiaozhi-server 调用，鉴权 Bearer server.secret（/config/**）。
 */
@RestController
@RequestMapping("/config/learning")
@RequiredArgsConstructor
public class LearningInternalController {

    private final LearningSessionService learningSessionService;

    @PostMapping("/session/start")
    public Result<Map<String, Object>> start(@RequestBody LearningSessionStartDTO body) {
        if (body == null || StringUtils.isBlank(body.getSessionUuid()) || body.getChildId() == null) {
            return new Result<Map<String, Object>>().error("sessionUuid、childId 必填");
        }
        return new Result<Map<String, Object>>().ok(learningSessionService.startSession(body));
    }

    @PostMapping("/session/turn")
    public Result<Void> turn(@RequestBody LearningSessionTurnDTO body) {
        if (body == null || StringUtils.isBlank(body.getSessionUuid())) {
            return new Result<Void>().error("sessionUuid 必填");
        }
        learningSessionService.recordTurn(body);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/session/photo")
    public Result<Void> photo(@RequestBody LearningSessionPhotoDTO body) {
        if (body == null || StringUtils.isBlank(body.getSessionUuid())) {
            return new Result<Void>().error("sessionUuid 必填");
        }
        learningSessionService.recordPhoto(body);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/session/end")
    public Result<Map<String, Object>> end(@RequestBody LearningSessionEndDTO body) {
        if (body == null || StringUtils.isBlank(body.getSessionUuid())) {
            return new Result<Map<String, Object>>().error("sessionUuid 必填");
        }
        return new Result<Map<String, Object>>().ok(learningSessionService.endSession(body));
    }

    @GetMapping("/child/context")
    public Result<Map<String, Object>> childContext(@RequestParam Long childId) {
        if (childId == null) {
            return new Result<Map<String, Object>>().error("childId 必填");
        }
        return new Result<Map<String, Object>>().ok(learningSessionService.getChildLearningContext(childId));
    }
}
