package xiaozhi.modules.ota.service;

import java.io.InputStream;

import xiaozhi.modules.ota.util.SwuFilenameParser;

public interface OtaPackageStorageService {

    StoredObject upload(SwuFilenameParser.ParsedSwu parsed, InputStream in, long size);

    String resolveAccessUrl(String ossKey);

    byte[] readLocalFile(String ossKey);

    record StoredObject(String ossKey, boolean oss) {
    }
}
