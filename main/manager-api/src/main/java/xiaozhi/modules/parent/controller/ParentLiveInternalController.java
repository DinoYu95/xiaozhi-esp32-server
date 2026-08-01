package xiaozhi.modules.parent.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.service.ParentLiveService;
import xiaozhi.modules.parent.vo.ParentLiveStatusVO;

/**
 * 远程监控内部接口：腾讯云回调、xiaozhi 查询状态。
 */
@RestController
@RequestMapping("/config/parent/live")
@RequiredArgsConstructor
@Slf4j
public class ParentLiveInternalController {

    private final ParentLiveService parentLiveService;

    @PostMapping("/tencent-callback")
    public Result<Void> tencentCallback(@RequestBody Map<String, Object> body) {
        log.info("腾讯云 live 事件回调 body={}", body);
        parentLiveService.handleTencentStreamEvent(body);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/status")
    public Result<ParentLiveStatusVO> internalStatus(@RequestParam Long sessionId) {
        return new Result<ParentLiveStatusVO>().ok(parentLiveService.getInternalStatus(sessionId));
    }
}
