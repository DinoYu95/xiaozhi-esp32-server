package xiaozhi.modules.learning.service;

import java.io.InputStream;

import xiaozhi.modules.learning.dto.TeachingKgPublishDTO;
import xiaozhi.modules.learning.entity.KgGraphReleaseEntity;
import xiaozhi.modules.learning.vo.KgReleaseVO;

public interface LearningKgService {

    Long createDraftRelease(String versionLabel, String subject, int gradeMin, int gradeMax);

    void importNodesCsv(Long releaseId, InputStream csv);

    void importEdgesCsv(Long releaseId, InputStream csv);

    void validateRelease(Long releaseId);

    void publishRelease(Long releaseId);

    KgReleaseVO getActivePublishedRelease(String subject);

    Long requireActiveReleaseId(String subject);

    /**
     * 按学科 + 省 + 教材 + 图谱年级匹配已发布 release（教研多维发版）
     */
    Long requireActiveReleaseId(String subject, String provinceCode, String textbookEdition, int graphGrade);

    KgGraphReleaseEntity findActivePublishedRelease(
            String subject, String provinceCode, String textbookEdition, int graphGrade);

    /**
     * 教研审批通过后：导入 JSON 并发布到 kg_*（单年级 + 省 + 教材维度）
     */
    Long publishFromTeaching(TeachingKgPublishDTO dto);
}
