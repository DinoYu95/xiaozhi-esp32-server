package xiaozhi.modules.mindportrait.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.mindportrait.dao.MpTemplateReleaseDao;
import xiaozhi.modules.mindportrait.dto.TeachingMpPublishDTO;
import xiaozhi.modules.mindportrait.entity.MpTemplateReleaseEntity;
import xiaozhi.modules.mindportrait.service.MindPortraitService;
import xiaozhi.modules.mindportrait.util.MindDefaultTemplateBuilder;

/**
 * 若某 age_band 尚无已发布心绪模板，自动写入默认模板，避免 H5 报「暂无已发布的心绪图谱模板」。
 */
@Component
@Order(25)
@RequiredArgsConstructor
@Slf4j
public class MindPortraitBootstrap implements ApplicationRunner {

    private final MpTemplateReleaseDao releaseDao;
    private final MindPortraitService mindPortraitService;

    @Override
    public void run(ApplicationArguments args) {
        for (String band : MindDefaultTemplateBuilder.ageBands()) {
            long published = releaseDao.selectCount(
                    new LambdaQueryWrapper<MpTemplateReleaseEntity>()
                            .eq(MpTemplateReleaseEntity::getAgeBand, band)
                            .eq(MpTemplateReleaseEntity::getStatus, MpTemplateReleaseEntity.STATUS_PUBLISHED));
            if (published > 0) {
                continue;
            }
            try {
                TeachingMpPublishDTO body = MindDefaultTemplateBuilder.buildPublishBody(band);
                Long releaseId = mindPortraitService.publishFromTeaching(body);
                log.info("MindPortraitBootstrap: seeded published template age_band={} releaseId={}", band, releaseId);
            } catch (Exception e) {
                log.warn("MindPortraitBootstrap: failed to seed age_band={}: {}", band, e.getMessage());
            }
        }
    }
}
