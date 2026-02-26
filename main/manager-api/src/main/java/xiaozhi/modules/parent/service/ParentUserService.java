package xiaozhi.modules.parent.service;

import xiaozhi.modules.parent.dto.ParentPhoneCodeDTO;
import xiaozhi.modules.parent.dto.ParentPhoneLoginDTO;
import xiaozhi.modules.parent.dto.ParentProfileDTO;
import xiaozhi.modules.parent.dto.ParentWechatLoginDTO;
import xiaozhi.modules.parent.vo.ParentLoginVO;
import xiaozhi.modules.parent.vo.ParentUserVO;

/**
 * 家长端用户服务
 */
public interface ParentUserService {

    /**
     * 微信 code 登录，有则查无则建用户，返回 token + 用户信息
     */
    ParentLoginVO wechatLogin(ParentWechatLoginDTO dto);

    /**
     * 发送手机验证码（限流 1 分钟 1 次）
     */
    void sendPhoneCode(ParentPhoneCodeDTO dto);

    /**
     * 手机号+验证码登录，返回 token + 用户信息
     */
    ParentLoginVO phoneLogin(ParentPhoneLoginDTO dto);

    /**
     * 当前家长信息（需鉴权）
     */
    ParentUserVO getInfo(Long parentUserId);

    /**
     * 更新当前家长个人信息（昵称、头像、手机号，手机号加密存储）
     */
    void updateProfile(Long parentUserId, ParentProfileDTO dto);

    /**
     * 登出，使当前 token 失效
     */
    void logout(String token);
}
