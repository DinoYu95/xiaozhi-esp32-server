package xiaozhi.modules.parent.controller;

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
}
