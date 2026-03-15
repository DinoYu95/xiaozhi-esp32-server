package xiaozhi.modules.agent.service.impl;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;

import xiaozhi.modules.agent.service.VoiceprintAudioConvertService;

/**
 * 使用 ffmpeg 将音频转为 16kHz 单声道 16-bit PCM WAV，供 voiceprint-api 使用
 */
@Service
@Slf4j
public class VoiceprintAudioConvertServiceImpl implements VoiceprintAudioConvertService {

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;

    /** 根据文件头判断输入格式，返回 ffmpeg 可识别的扩展名 */
    private static String guessExtension(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length < 12) {
            return ".audio";
        }
        // Ogg/Opus: 4 bytes "OggS"
        if (audioBytes[0] == 'O' && audioBytes[1] == 'g' && audioBytes[2] == 'g' && audioBytes[3] == 'S') {
            return ".ogg";
        }
        // WAV: 4 bytes "RIFF"
        if (audioBytes[0] == 'R' && audioBytes[1] == 'I' && audioBytes[2] == 'F' && audioBytes[3] == 'F') {
            return ".wav";
        }
        // MP3: ID3 or 0xFF 0xFB
        if ((audioBytes[0] == 'I' && audioBytes[1] == 'D' && audioBytes[2] == '3')
                || (audioBytes[0] == (byte) 0xFF && (audioBytes[1] & 0xE0) == 0xE0)) {
            return ".mp3";
        }
        // M4A/MP4: ftyp
        if (audioBytes.length >= 8 && audioBytes[4] == 'f' && audioBytes[5] == 't' && audioBytes[6] == 'y' && audioBytes[7] == 'p') {
            return ".m4a";
        }
        return ".audio";
    }

    @Override
    public byte[] convertToVoiceprintWav(byte[] audioBytes) {
        if (ArrayUtils.isEmpty(audioBytes)) {
            return null;
        }
        Path inPath = null;
        Path outPath = null;
        try {
            String ext = guessExtension(audioBytes);
            inPath = Files.createTempFile("vp_in_", ext);
            outPath = Files.createTempFile("vp_out_", ".wav");
            Files.write(inPath, audioBytes);

            // -vn 忽略视频, -acodec pcm_s16le 强制 16bit PCM（voiceprint-api 常用格式）
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", inPath.toAbsolutePath().toString(),
                    "-vn",
                    "-ar", String.valueOf(SAMPLE_RATE),
                    "-ac", String.valueOf(CHANNELS),
                    "-acodec", "pcm_s16le",
                    "-f", "wav",
                    outPath.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit != 0) {
                try (InputStream err = p.getInputStream()) {
                    String errOut = new String(err.readAllBytes());
                    log.warn("ffmpeg 转码失败 exit={}, 输入格式推测={}, 输出: {}", exit, ext, errOut);
                }
                return null;
            }
            byte[] result = Files.readAllBytes(outPath);
            log.debug("声纹音频转码成功，输出 {} 字节", result.length);
            return result;
        } catch (Exception e) {
            log.warn("声纹音频转码异常: {}", e.getMessage());
            return null;
        } finally {
            deleteQuietly(inPath);
            deleteQuietly(outPath);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
