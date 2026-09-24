package xiaozhi.modules.parent.betaconfidentiality.controller;

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
import xiaozhi.modules.parent.betaconfidentiality.dto.ParentBetaConfAgreeDTO;
import xiaozhi.modules.parent.betaconfidentiality.service.ParentBetaConfService;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfDocumentVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfStatusVO;
import xiaozhi.modules.parent.context.ParentContext;

@RestController
@RequestMapping("/parent-api/beta-confidentiality")
@RequiredArgsConstructor
@Tag(name = "家长端-内测保密协议")
public class ParentBetaConfController {

    private final ParentBetaConfService parentBetaConfService;

    @GetMapping("/document")
    @Operation(summary = "当前生效内测保密协议（可不登录）")
    public Result<ParentBetaConfDocumentVO> document() {
        return new Result<ParentBetaConfDocumentVO>().ok(parentBetaConfService.getPublishedDocument());
    }

    @GetMapping("/status")
    @Operation(summary = "当前用户内测保密协议签署状态")
    public Result<ParentBetaConfStatusVO> status() {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return new Result<ParentBetaConfStatusVO>().ok(parentBetaConfService.getStatus(parentUserId));
    }

    @PostMapping("/agree")
    @Operation(summary = "同意当前版本内测保密协议")
    public Result<ParentBetaConfStatusVO> agree(
            @RequestBody @Valid ParentBetaConfAgreeDTO dto, HttpServletRequest request) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        parentBetaConfService.agree(
                parentUserId,
                dto,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        ParentBetaConfStatusVO status = parentBetaConfService.getStatus(parentUserId);
        if (Boolean.TRUE.equals(status.getBetaConfEnabled()) && Boolean.TRUE.equals(status.getBlocking())) {
            throw new RenException(ErrorCode.PARENT_BETA_CONF_VERSION_INVALID);
        }
        return new Result<ParentBetaConfStatusVO>().ok(status);
    }
}
