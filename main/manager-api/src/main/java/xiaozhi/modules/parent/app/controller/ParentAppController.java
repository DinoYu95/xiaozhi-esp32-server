package xiaozhi.modules.parent.app.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.app.service.ParentAppConfigService;
import xiaozhi.modules.parent.app.vo.ParentAppGuestPreviewVO;
import xiaozhi.modules.parent.app.vo.ParentAppPublicConfigVO;

@RestController
@RequestMapping("/parent-api/app")
@RequiredArgsConstructor
@Tag(name = "家长端-小程序公开配置")
public class ParentAppController {

    private final ParentAppConfigService parentAppConfigService;

    @GetMapping("/public-config")
    @Operation(summary = "游客公开配置（无需登录）")
    public Result<ParentAppPublicConfigVO> publicConfig() {
        return new Result<ParentAppPublicConfigVO>().ok(parentAppConfigService.getPublicConfig());
    }

    @GetMapping("/guest-preview")
    @Operation(summary = "游客技能预览（无需登录，可选）")
    public Result<ParentAppGuestPreviewVO> guestPreview() {
        return new Result<ParentAppGuestPreviewVO>().ok(parentAppConfigService.getGuestPreview());
    }
}
