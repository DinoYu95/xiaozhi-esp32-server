package xiaozhi.modules.parent.consent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.consent.dto.ParentConsentAgreeDTO;
import xiaozhi.modules.parent.consent.service.ParentConsentService;
import xiaozhi.modules.parent.consent.vo.ParentConsentDocumentVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentStatusVO;
import xiaozhi.modules.parent.context.ParentContext;

@RestController
@RequestMapping("/parent-api/consent")
@RequiredArgsConstructor
@Tag(name = "家长端-儿童隐私协议")
public class ParentConsentController {

    private final ParentConsentService parentConsentService;

    @GetMapping("/document")
    @Operation(summary = "当前生效协议正文（可不登录）")
    public Result<ParentConsentDocumentVO> document() {
        ParentConsentDocumentVO vo = parentConsentService.getPublishedDocument();
        return new Result<ParentConsentDocumentVO>().ok(vo);
    }

    @GetMapping("/status")
    @Operation(summary = "当前用户签署状态")
    public Result<ParentConsentStatusVO> status() {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return new Result<ParentConsentStatusVO>().ok(parentConsentService.getStatus(parentUserId));
    }

    @PostMapping("/agree")
    @Operation(summary = "同意当前版本协议")
    public Result<ParentConsentStatusVO> agree(
            @RequestBody @Valid ParentConsentAgreeDTO dto, HttpServletRequest request) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        parentConsentService.agree(
                parentUserId,
                dto,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        return new Result<ParentConsentStatusVO>().ok(parentConsentService.getStatus(parentUserId));
    }
}
