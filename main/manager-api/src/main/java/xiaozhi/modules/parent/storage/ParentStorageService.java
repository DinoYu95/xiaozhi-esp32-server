package xiaozhi.modules.parent.storage;

import org.springframework.web.multipart.MultipartFile;

import xiaozhi.modules.parent.storage.vo.ParentStorageUploadVO;

public interface ParentStorageService {

    boolean isOssEnabled();

    ParentStorageUploadVO upload(ParentStorageCategory category, Long parentUserId, MultipartFile file,
            String publicBaseUrl);

    /**
     * 将库中存的 objectKey 或历史 URL 解析为可访问地址（私有桶时重新签名）。
     */
    String resolveAccessUrl(String storedReference);

    String resolveAccessUrl(ParentStorageCategory category, String storedReference);

    /**
     * 是否为家长在本系统上传/资料页更新的头像引用（非微信 CDN 等第三方 URL）。
     */
    boolean isManagedAvatarReference(String storedReference);

    /**
     * 家庭共享成员列表等场景：未主动更换头像时返回 null，否则返回可访问 URL。
     */
    String resolveSharingAvatarUrl(String storedReference);

    /**
     * 校验引用属于当前家长及类别，返回建议入库的值（OSS 模式为 objectKey，本地模式为 filename）。
     */
    String normalizeAndValidate(Long parentUserId, ParentStorageCategory category, String objectKeyOrUrl);

    byte[] readLocalFile(ParentStorageCategory category, String filename);
}
