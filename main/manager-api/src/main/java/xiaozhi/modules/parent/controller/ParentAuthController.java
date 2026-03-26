package xiaozhi.modules.parent.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
import xiaozhi.modules.parent.vo.ParentAvatarUploadVO;
import xiaozhi.modules.parent.vo.ParentLoginVO;
import xiaozhi.modules.parent.vo.ParentUserVO;

@RestController
@RequestMapping("/parent-api/auth")
@RequiredArgsConstructor
@Tag(name = "家长端-登录与用户")
public class ParentAuthController {

    private static final Pattern PARENT_AVATAR_FILE_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|gif|webp)$",
            Pattern.CASE_INSENSITIVE);

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

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传家长头像（小程序 multipart 字段名 file）")
    public Result<ParentAvatarUploadVO> uploadAvatar(
            @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        String stored = parentUserService.storeParentAvatar(file);
        ParentAvatarUploadVO vo = new ParentAvatarUploadVO();
        vo.setAvatarUrl(buildApiBaseUrl(request) + "/parent-api/auth/avatar/file/" + stored);
        return new Result<ParentAvatarUploadVO>().ok(vo);
    }

    @GetMapping("/avatar/file/{filename:.+}")
    @Operation(summary = "获取已上传的家长头像（匿名，供小程序 image 等展示）")
    public ResponseEntity<byte[]> getAvatarFile(@PathVariable("filename") String filename) throws IOException {
        if (filename == null || !PARENT_AVATAR_FILE_PATTERN.matcher(filename).matches()) {
            return ResponseEntity.notFound().build();
        }
        Path dirAbs = Paths.get("uploadfile", "parent-avatar").toAbsolutePath().normalize();
        Path file = dirAbs.resolve(filename).normalize();
        if (!file.startsWith(dirAbs) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = Files.readAllBytes(file);
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        MediaType mt = switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(mt)
                .body(bytes);
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

    /** 构造对外访问 API 的根 URL（含 context-path，如 http://host:8002/xiaozhi） */
    private static String buildApiBaseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.isBlank(scheme)) {
            scheme = request.getScheme();
        }
        String hostHeader = request.getHeader("X-Forwarded-Host");
        String hostPart;
        if (StringUtils.isNotBlank(hostHeader)) {
            hostPart = hostHeader.split(",")[0].trim();
        } else {
            hostPart = request.getServerName();
            int port = request.getServerPort();
            boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443);
            if (!defaultPort) {
                hostPart = hostPart + ":" + port;
            }
        }
        String ctx = request.getContextPath();
        if (ctx == null) {
            ctx = "";
        }
        return scheme + "://" + hostPart + ctx;
    }
}
