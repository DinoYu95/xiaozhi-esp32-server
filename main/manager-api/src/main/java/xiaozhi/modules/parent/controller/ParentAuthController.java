package xiaozhi.modules.parent.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dto.ParentPhoneCodeDTO;
import xiaozhi.modules.parent.dto.ParentPhoneLoginDTO;
import xiaozhi.modules.parent.dto.ParentProfileDTO;
import xiaozhi.modules.parent.dto.ParentWechatLoginDTO;
import xiaozhi.modules.parent.service.ParentUserService;
import xiaozhi.modules.parent.vo.ParentLoginVO;
import xiaozhi.modules.parent.vo.ParentUserVO;

@RestController
@RequestMapping("/parent-api/auth")
@RequiredArgsConstructor
@Tag(name = "家长端-登录与用户")
public class ParentAuthController {

    private final ParentUserService parentUserService;

    @PostMapping("/wechat")
    @Operation(summary = "微信 code 登录")
    public Result<ParentLoginVO> wechatLogin(@RequestBody ParentWechatLoginDTO dto) {
        ParentLoginVO vo = parentUserService.wechatLogin(dto);
        return new Result<ParentLoginVO>().ok(vo);
    }

    @PostMapping("/phone/code")
    @Operation(summary = "发送手机验证码")
    public Result<Void> sendPhoneCode(@RequestBody ParentPhoneCodeDTO dto) {
        parentUserService.sendPhoneCode(dto);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/phone/login")
    @Operation(summary = "手机号+验证码登录")
    public Result<ParentLoginVO> phoneLogin(@RequestBody ParentPhoneLoginDTO dto) {
        ParentLoginVO vo = parentUserService.phoneLogin(dto);
        return new Result<ParentLoginVO>().ok(vo);
    }

    @GetMapping("/info")
    @Operation(summary = "当前家长信息")
    public Result<ParentUserVO> info() {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        ParentUserVO vo = parentUserService.getInfo(parentUserId);
        return new Result<ParentUserVO>().ok(vo);
    }

    @PutMapping("/profile")
    @Operation(summary = "更新个人信息")
    public Result<Void> updateProfile(@RequestBody ParentProfileDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        parentUserService.updateProfile(parentUserId, dto);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/logout")
    @Operation(summary = "登出")
    public Result<Void> logout(HttpServletRequest request) {
        String token = getRequestToken(request);
        if (StringUtils.isNotBlank(token)) {
            parentUserService.logout(token);
        }
        return new Result<Void>().ok(null);
    }

    private static String getRequestToken(HttpServletRequest request) {
        String authorization = request.getHeader(Constant.AUTHORIZATION);
        if (StringUtils.isNotBlank(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
