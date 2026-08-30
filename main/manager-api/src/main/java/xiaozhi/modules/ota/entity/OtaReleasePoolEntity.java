package xiaozhi.modules.ota.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ota_release_pool")
public class OtaReleasePoolEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long releaseId;
    private Long poolId;
}
