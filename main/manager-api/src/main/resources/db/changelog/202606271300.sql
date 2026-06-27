-- 智控台平台 owner：家长端绑设备与超级管理员数据范围共用此 sys_user.id
DELETE FROM `sys_params` WHERE param_code = 'server.platform_owner_user_id';
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'server.platform_owner_user_id',
       '2019681905515061249',
       'number',
       1,
       '智控台平台owner用户ID：家长端绑定设备/agent归属账号；超级管理员登录后按此账号查看智能体与设备';
