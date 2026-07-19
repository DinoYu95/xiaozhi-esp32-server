package xiaozhi.modules.parent.controller;

import java.util.Base64;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
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
     */
    @PostMapping("/snapshot/device-upload")
    @Operation(summary = "设备远程看娃快照上传")
    public Result<Void> deviceSnapshotUpload(
            @RequestHeader(value = "X-Snapshot-Token", required = false) String snapshotToken,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String uploadToken,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Integer width,
            @RequestParam(required = false) Integer height,
            @RequestBody(required = false) DeviceSnapshotUploadBody body) {
        String token = StringUtils.isNotBlank(snapshotToken) ? snapshotToken.trim()
                : (body != null && StringUtils.isNotBlank(body.getUploadToken()) ? body.getUploadToken().trim()
                        : StringUtils.trimToNull(uploadToken));
        String rid = body != null && StringUtils.isNotBlank(body.getRequestId()) ? body.getRequestId().trim()
                : StringUtils.trimToNull(requestId);
        if (StringUtils.isBlank(token) || StringUtils.isBlank(rid)) {
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, "requestId 与 uploadToken 必填");
        }
        byte[] bytes = null;
        String mime = "image/jpeg";
        if (file != null && !file.isEmpty()) {
            try {
                bytes = file.getBytes();
                if (StringUtils.isNotBlank(file.getContentType())) {
                    mime = file.getContentType();
                }
            } catch (Exception e) {
                return new Result<Void>().error(ErrorCode.UPLOAD_FILE_ERROR);
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
        }
        if (bytes == null || bytes.length == 0) {
            return new Result<Void>().error(ErrorCode.UPLOAD_FILE_EMPTY);
        }
        try {
            parentSnapshotService.deviceUpload(rid, token, bytes, mime, width, height);
            return new Result<Void>().ok(null);
        } catch (Exception e) {
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, e.getMessage());
        }
    }

    @lombok.Data
    public static class DeviceSnapshotUploadBody {
        private String requestId;
        private String uploadToken;
        private String imageBase64;
        private String mimeType;
        private Integer width;
        private Integer height;
    }
}
