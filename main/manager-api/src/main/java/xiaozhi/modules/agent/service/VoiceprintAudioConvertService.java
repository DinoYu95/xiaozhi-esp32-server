package xiaozhi.modules.agent.service;

/**
 * 将任意音频转成 voiceprint-api 可识别的 16kHz 单声道 PCM WAV
 */
public interface VoiceprintAudioConvertService {

    /**
     * 转码为 16kHz 单声道 PCM WAV，供声纹 API 使用。
     * 转码失败时返回 null，调用方应降级使用原数据。
     *
     * @param audioBytes 原始音频字节
     * @return 转码后的 WAV 字节，失败返回 null
     */
    byte[] convertToVoiceprintWav(byte[] audioBytes);
}
