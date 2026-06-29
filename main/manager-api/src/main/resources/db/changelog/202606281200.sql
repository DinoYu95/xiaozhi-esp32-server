-- 绑设备新建智能体时，除 owner_child 官方推荐外，其余说话人的默认技能映射（智控台参数管理可改 skill_id）
DELETE FROM `sys_params` WHERE param_code = 'server.agent_default_skill_mapping';
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'server.agent_default_skill_mapping',
       '{"parent":["skill_parent_assistant"],"other_child":["skill_guest_child"],"other_adult":["skill_guest_adult"],"unknown":["skill_general_chat"]}',
       'string',
       1,
       '绑设备新建 agent 时默认技能：parent/other_child/other_adult/unknown 的 skill_id 列表（须与 ai_skill.id 一致）；owner_child 仍取 is_official_recommended=1 的全部技能';
