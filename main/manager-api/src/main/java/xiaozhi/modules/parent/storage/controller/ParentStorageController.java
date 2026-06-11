package xiaozhi.modules.parent.storage.controller;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.storage.ParentStorageCategory;
import xiaozhi.modules.parent.storage.ParentStorageService;
import xiaozhi.modules.parent.storage.vo.ParentStorageUploadVO;

@RestController
@RequestMapping("/parent-api/storage")
@RequiredArgsConstructor
@Tag(name = "家长端-文件存储")
public class ParentStorageController {

    @Value("${xiaozhi.parent.public-base-url:}")
    private String parentPublicBaseUrl;

    private final ParentStorageService parentStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "统一上传图片（category=avatar|feedback），返回 objectKey 与 accessUrl")
    public Result<ParentStorageUploadVO> upload(
            @RequestParam("category") String category,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long parentUserId = requireParentUserId();
        ParentStorageCategory cat = ParentStorageCategory.fromCode(category);
        ParentStorageUploadVO vo = parentStorageService.upload(
                cat, parentUserId, file, resolvePublicBaseUrl(request));
        return new Result<ParentStorageUploadVO>().ok(vo);
    }

    @GetMapping("/access")
    @Operation(summary = "按 objectKey 获取可访问地址（私有桶重签；本地文件直出字节流）")
    public ResponseEntity<?> access(
            @RequestParam("key") String objectKey,
            @RequestParam(value = "category", required = false) String categoryCode) {
        Long parentUserId = requireParentUserId();
        if (StringUtils.isBlank(objectKey)) {
            throw new RenException(ErrorCode.PARENT_STORAGE_OBJECT_INVALID);
        }
        ParentStorageCategory category = StringUtils.isNotBlank(categoryCode)
                ? ParentStorageCategory.fromCode(categoryCode)
                : inferCategory(objectKey);
        String normalized = parentStorageService.normalizeAndValidate(parentUserId, category, objectKey.trim());
        if (parentStorageService.isOssEnabled()) {
            String url = parentStorageService.resolveAccessUrl(normalized);
            if (StringUtils.isBlank(url)) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, url)
                    .build();
        }
        byte[] bytes = parentStorageService.readLocalFile(category, normalized);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        String ext = normalized.substring(normalized.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        MediaType mt = mediaTypeForExt(ext);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(mt)
                .body(bytes);
    }

    private static ParentStorageCategory inferCategory(String objectKey) {
        if (objectKey.contains("/parent/feedback/") || objectKey.contains("parent-feedback")) {
            return ParentStorageCategory.FEEDBACK;
        }
        return ParentStorageCategory.AVATAR;
    }

    private static MediaType mediaTypeForExt(String ext) {
        return switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
    }

    private static Long requireParentUserId() {
        Long id = ParentContext.getParentUserId();
        if (id == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return id;
    }

    private String resolvePublicBaseUrl(HttpServletRequest request) {
        if (StringUtils.isNotBlank(parentPublicBaseUrl)) {
            String base = parentPublicBaseUrl.trim();
            return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String ctx = request.getContextPath() != null ? request.getContextPath() : "";
        String hostPart = defaultPort ? host : host + ":" + port;
        return scheme + "://" + hostPart + ctx;
    }
}
