package xiaozhi.modules.parent.controller;

import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dto.ParentFeedbackCreateDTO;
import xiaozhi.modules.parent.service.ParentFeedbackService;
import xiaozhi.modules.parent.storage.ParentStorageCategory;
import xiaozhi.modules.parent.storage.ParentStorageService;
import xiaozhi.modules.parent.storage.vo.ParentStorageUploadVO;
import xiaozhi.modules.parent.vo.ParentFeedbackDetailVO;
import xiaozhi.modules.parent.vo.ParentFeedbackEnabledVO;
import xiaozhi.modules.parent.vo.ParentFeedbackImageUploadVO;
import xiaozhi.modules.parent.vo.ParentFeedbackVO;

@RestController
@RequestMapping("/parent-api/feedback")
@RequiredArgsConstructor
@Tag(name = "家长端-内测反馈")
public class ParentFeedbackController {

    private static final Pattern FEEDBACK_IMAGE_FILE_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|gif|webp)$",
            Pattern.CASE_INSENSITIVE);

    @Value("${xiaozhi.parent.public-base-url:}")
    private String parentPublicBaseUrl;

    private final ParentFeedbackService parentFeedbackService;
    private final ParentStorageService parentStorageService;

    @GetMapping("/entry-status")
    @Operation(summary = "反馈入口是否展示（内测开关 + 是否内测用户）")
    public Result<ParentFeedbackEnabledVO> entryStatus() {
        Long parentUserId = requireParentUserId();
        return new Result<ParentFeedbackEnabledVO>().ok(parentFeedbackService.getEntryStatus(parentUserId));
    }

    @PostMapping
    @Operation(summary = "提交反馈（须登录且为内测用户）")
    public Result<ParentFeedbackVO> create(@RequestBody @Valid ParentFeedbackCreateDTO dto) {
        Long parentUserId = requireParentUserId();
        return new Result<ParentFeedbackVO>().ok(parentFeedbackService.create(parentUserId, dto));
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传反馈截图（等价于 POST /parent-api/storage/upload?category=feedback）")
    public Result<ParentFeedbackImageUploadVO> uploadImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long parentUserId = requireParentUserId();
        parentFeedbackService.assertBetaFeedbackAllowed(parentUserId);
        ParentStorageUploadVO uploaded = parentStorageService.upload(
                ParentStorageCategory.FEEDBACK, parentUserId, file, resolvePublicBaseUrl(request));
        ParentFeedbackImageUploadVO vo = new ParentFeedbackImageUploadVO();
        vo.setObjectKey(uploaded.getObjectKey());
        vo.setImageUrl(uploaded.getAccessUrl());
        return new Result<ParentFeedbackImageUploadVO>().ok(vo);
    }

    @GetMapping("/image/file/{filename:.+}")
    @Operation(summary = "获取反馈截图（匿名，供 image 组件展示）")
    public ResponseEntity<byte[]> getImageFile(@PathVariable("filename") String filename) {
        if (filename == null || !FEEDBACK_IMAGE_FILE_PATTERN.matcher(filename).matches()) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = parentStorageService.readLocalFile(ParentStorageCategory.FEEDBACK, filename);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
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

    @GetMapping("/page")
    @Operation(summary = "我的反馈列表")
    public Result<PageData<ParentFeedbackVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Long parentUserId = requireParentUserId();
        return new Result<PageData<ParentFeedbackVO>>()
                .ok(parentFeedbackService.pageByParent(parentUserId, page, limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "我的反馈详情")
    public Result<ParentFeedbackDetailVO> detail(@PathVariable Long id) {
        Long parentUserId = requireParentUserId();
        return new Result<ParentFeedbackDetailVO>().ok(parentFeedbackService.getByParent(parentUserId, id));
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
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }
}
