package xiaozhi.modules.parent.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dto.ParentChatSendDTO;
import xiaozhi.modules.parent.service.ParentChatService;
import xiaozhi.modules.parent.service.ParentSnapshotService;
import xiaozhi.modules.parent.storage.ParentStorageCategory;
import xiaozhi.modules.parent.storage.ParentStorageService;
import xiaozhi.modules.parent.vo.ParentChatHistoryPageVO;
import xiaozhi.modules.parent.vo.ParentChatMessageVO;

/**
 * 家长端聊天（与宝宝的专属小助手对话）
 */
@RestController
@RequestMapping("/parent-api/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "家长端-聊天")
public class ParentChatController {

    private final ParentChatService parentChatService;
    private final ParentStorageService parentStorageService;
    private final ParentSnapshotService parentSnapshotService;

    @PostMapping("/upload-audio")
    @Operation(summary = "上传语音消息音频，返回 audioId 供发送时引用")
    public Result<String> uploadAudio(
            @Parameter(description = "孩子ID", required = true) @RequestParam Long childId,
            @Parameter(description = "音频文件", required = true) @RequestParam MultipartFile file) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        String audioId = parentChatService.uploadAudio(parentUserId, childId, file);
        return new Result<String>().ok(audioId);
    }

    @PostMapping("/send")
    @Operation(summary = "发送消息并获取助手回复")
    public Result<ParentChatMessageVO> send(@RequestBody @Valid ParentChatSendDTO dto) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        ParentChatMessageVO vo = parentChatService.send(parentUserId, dto);
        return new Result<ParentChatMessageVO>().ok(vo);
    }

    @GetMapping("/history")
    @Operation(summary = "获取聊天记录（分页）")
    public Result<ParentChatHistoryPageVO> getHistory(
            @Parameter(description = "孩子ID", required = true) @RequestParam Long childId,
            @Parameter(description = "页码，默认1（最新在前）；未传则走旧逻辑返回全部") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页条数，默认20；未传则走旧逻辑返回全部") @RequestParam(required = false) Integer pageSize) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        // 兼容：page、pageSize 都未传 → 走旧逻辑返回全部，hasMore=false
        if (page == null && pageSize == null) {
            var list = parentChatService.getHistory(parentUserId, childId);
            return new Result<ParentChatHistoryPageVO>().ok(new ParentChatHistoryPageVO(list, false));
        }
        int p = (page != null && page > 0) ? page : 1;
        int size = (pageSize != null && pageSize > 0) ? pageSize : 20;
        ParentChatHistoryPageVO data = parentChatService.getHistoryPage(parentUserId, childId, p, size);
        return new Result<ParentChatHistoryPageVO>().ok(data);
    }

    @PostMapping("/play-token")
    @Operation(summary = "获取语音播放 token")
    public Result<String> getPlayToken(
            @Parameter(description = "音频ID", required = true) @RequestParam String audioId) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        String token = parentChatService.getPlayToken(parentUserId, audioId);
        return new Result<String>().ok(token);
    }

    @GetMapping("/play/{token}")
    @Operation(summary = "按 token 播放音频（一次性使用，免鉴权）")
    public ResponseEntity<byte[]> play(@PathVariable String token) {
        byte[] audio = parentChatService.getAudioByPlayToken(token);
        if (audio == null || audio.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"chat-audio.wav\"")
                .body(audio);
    }

    @GetMapping("/snapshot/file/{filename}")
    @Operation(summary = "本地模式：读取远程看娃快照文件")
    public ResponseEntity<byte[]> snapshotFile(@PathVariable String filename) {
        byte[] bytes = parentStorageService.readLocalFile(ParentStorageCategory.CHAT_SNAPSHOT, filename);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(bytes);
    }

    /**
     * 远程看娃 Phase B：设备 HTTP 回传快照（uploadToken 鉴权，无需家长登录）。
     * 支持 application/json、multipart/form-data、以及 ESP32 常用的 raw image/jpeg 二进制 body。
     */
    @PostMapping("/snapshot/device-upload")
    @Operation(summary = "设备远程看娃快照上传")
    public Result<Void> deviceSnapshotUpload(
            HttpServletRequest request,
            @RequestHeader(value = "X-Snapshot-Token", required = false) String snapshotToken,
            @RequestHeader(value = "X-Request-Id", required = false) String requestIdHeader) {
        try {
            String contentType = StringUtils.defaultString(request.getContentType()).toLowerCase(Locale.ROOT);
            DeviceSnapshotUploadBody body = null;
            MultipartFile file = null;
            String requestIdParam = request.getParameter("requestId");
            String uploadTokenParam = request.getParameter("uploadToken");
            Integer width = parseIntegerParam(request.getParameter("width"));
            Integer height = parseIntegerParam(request.getParameter("height"));
            String taskTypeParam = StringUtils.trimToNull(request.getParameter("taskType"));

            if (request instanceof MultipartHttpServletRequest multipartRequest) {
                file = multipartRequest.getFile("file");
                if (StringUtils.isBlank(requestIdParam)) {
                    requestIdParam = multipartRequest.getParameter("requestId");
                }
                if (StringUtils.isBlank(uploadTokenParam)) {
                    uploadTokenParam = multipartRequest.getParameter("uploadToken");
                }
                if (width == null) {
                    width = parseIntegerParam(multipartRequest.getParameter("width"));
                }
                if (height == null) {
                    height = parseIntegerParam(multipartRequest.getParameter("height"));
                }
                if (taskTypeParam == null) {
                    taskTypeParam = StringUtils.trimToNull(multipartRequest.getParameter("taskType"));
                }
            } else if (contentType.contains("application/json")) {
                byte[] raw = readRequestBody(request);
                if (raw.length > 0) {
                    body = JsonUtils.parseObject(raw, DeviceSnapshotUploadBody.class);
                }
            }

            String token = firstNonBlank(
                    snapshotToken,
                    uploadTokenParam,
                    body != null ? body.getUploadToken() : null);
            String rid = firstNonBlank(
                    requestIdParam,
                    requestIdHeader,
                    body != null ? body.getRequestId() : null);
            if (StringUtils.isBlank(token) || StringUtils.isBlank(rid)) {
                return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, "requestId 与 uploadToken 必填");
            }

            byte[] bytes = null;
            String mime = "image/jpeg";
            if (file != null && !file.isEmpty()) {
                bytes = file.getBytes();
                if (StringUtils.isNotBlank(file.getContentType())) {
                    mime = file.getContentType();
                }
            } else if (body != null && StringUtils.isNotBlank(body.getImageBase64())) {
                String b64 = body.getImageBase64().trim();
                if (b64.contains(",")) {
                    b64 = b64.substring(b64.indexOf(',') + 1);
                }
                try {
                    bytes = Base64.getDecoder().decode(b64);
                } catch (IllegalArgumentException e) {
                    return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, "图片数据无效");
                }
                if (StringUtils.isNotBlank(body.getMimeType())) {
                    mime = body.getMimeType();
                }
                if (body.getWidth() != null) {
                    width = body.getWidth();
                }
                if (body.getHeight() != null) {
                    height = body.getHeight();
                }
            } else if (!contentType.contains("application/json") && !contentType.contains("multipart/form-data")) {
                bytes = readRequestBody(request);
                if (contentType.startsWith("image/")) {
                    mime = contentType.split(";")[0].trim();
                }
            }

            if (bytes == null || bytes.length == 0) {
                return new Result<Void>().error(ErrorCode.UPLOAD_FILE_EMPTY);
            }

            String taskType = taskTypeParam;
            if (StringUtils.isBlank(taskType) && body != null && StringUtils.isNotBlank(body.getTaskType())) {
                taskType = body.getTaskType().trim();
            }

            parentSnapshotService.deviceUpload(rid.trim(), token.trim(), bytes, mime, width, height, taskType);
            log.info("设备远程看娃上传成功 requestId={} bytes={} mime={}", rid, bytes.length, mime);
            return new Result<Void>().ok(null);
        } catch (RenException e) {
            log.warn("设备远程看娃上传失败 requestId={} code={} msg={}",
                    request.getParameter("requestId"), e.getCode(), e.getMsg());
            return new Result<Void>().error(e.getCode(), e.getMsg());
        } catch (Exception e) {
            log.error("设备远程看娃上传异常 contentType={} requestId={}",
                    request.getContentType(), request.getParameter("requestId"), e);
            return new Result<Void>().error(ErrorCode.UPLOAD_FILE_ERROR, e.getMessage());
        }
    }

    private static byte[] readRequestBody(HttpServletRequest request) throws IOException {
        try (InputStream in = request.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static Integer parseIntegerParam(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    @lombok.Data
    public static class DeviceSnapshotUploadBody {
        private String requestId;
        private String uploadToken;
        private String imageBase64;
        private String mimeType;
        private Integer width;
        private Integer height;
        /** 与 prepare 一致，如 parent_snapshot */
        private String taskType;
    }
}
