# -*- coding: utf-8 -*-
"""
打断意图识别：判断用户文本是否为「暂停/停止/别说了」等明确打断意图。
主孩子轮次内，非主孩子声音需通过此判断才允许打断，否则丢弃该段。
"""
import re
from typing import Optional

# 明确打断意图的关键词/短语（可配置扩展）
INTERRUPT_KEYWORDS = [
    "暂停", "停止", "别说了", "不要说了", "停下", "停一下",
    "换个话题", "换一个", "等一下", "先等等", "等等再说",
    "闭嘴", "别讲了", "不用说了", "打住",
]


def is_interrupt_intent(text: Optional[str]) -> bool:
    """
    判断文本是否为打断意图。基于关键词匹配，后续可改为小模型/意图分类。
    :param text: 用户说的话（纯文本，可含标点）
    :return: True 表示应允许打断当前轮
    """
    if not text or not isinstance(text, str):
        return False
    # 去标点、空白、转小写（仅对拼音/英文），保留中文
    cleaned = re.sub(r"[^\w\u4e00-\u9fff]", "", text.strip())
    if not cleaned:
        return False
    for kw in INTERRUPT_KEYWORDS:
        if kw in text or kw in cleaned:
            return True
    return False
