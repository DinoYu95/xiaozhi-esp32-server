# -*- coding: utf-8 -*-
"""作业辅导模式单元测试。"""
import importlib.util
import sys
import time
import types
import unittest
from pathlib import Path
from types import SimpleNamespace

_util = types.ModuleType("core.utils.util")


def _mock_remove_punctuation_and_length(text):
    filtered = "".join(
        ch
        for ch in (text or "")
        if ch.isalnum() or ("\u4e00" <= ch <= "\u9fff")
    )
    return len(filtered), filtered


_util.remove_punctuation_and_length = _mock_remove_punctuation_and_length
sys.modules.setdefault("core", types.ModuleType("core"))
sys.modules.setdefault("core.utils", types.ModuleType("core.utils"))
sys.modules["core.utils.util"] = _util

_spec = importlib.util.spec_from_file_location(
    "homework_tutor_mode",
    Path(__file__).resolve().parent
    / "core"
    / "zhibanAgent"
    / "homework_tutor_mode.py",
)
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)

match_enter = _mod.match_enter
match_exit = _mod.match_exit
try_resolve_mode_phrase = _mod.try_resolve_mode_phrase
try_resolve_homework_photo_phrase = _mod.try_resolve_homework_photo_phrase
apply_zhiban_homework_meta = _mod.apply_zhiban_homework_meta
enter_mode = _mod.enter_mode
exit_mode = _mod.exit_mode
MODE_ID = _mod.MODE_ID
DEFAULT_IDLE_TIMEOUT_SEC = _mod.DEFAULT_IDLE_TIMEOUT_SEC


class HomeworkTutorModeTest(unittest.TestCase):
    def _conn(self, **kwargs):
        base = {
            "active_mode": None,
            "homework_mode_entered_at": None,
            "homework_mode_just_expired": False,
            "homework_photo_pending": False,
            "homework_photo_capture_now": False,
            "config": {},
        }
        base.update(kwargs)
        return SimpleNamespace(**base)

    def test_match_enter(self):
        self.assertTrue(match_enter("进入作业辅导"))
        self.assertTrue(match_enter("我想进入作业辅导模式"))
        self.assertFalse(match_enter("今天天气怎么样"))

    def test_match_exit(self):
        self.assertTrue(match_exit("退出作业辅导"))
        self.assertFalse(match_exit("进入作业辅导"))

    def test_enter_mode(self):
        conn = self._conn()
        handled, reply = try_resolve_mode_phrase(conn, "进入作业辅导")
        self.assertTrue(handled)
        self.assertIn("进入作业辅导模式", reply)
        self.assertEqual(conn.active_mode, MODE_ID)

    def test_exit_mode(self):
        conn = self._conn()
        enter_mode(conn)
        handled, reply = try_resolve_mode_phrase(conn, "退出作业辅导")
        self.assertTrue(handled)
        self.assertIn("结束", reply)
        self.assertIsNone(conn.active_mode)

    def test_exit_when_not_in_mode(self):
        conn = self._conn()
        handled, reply = try_resolve_mode_phrase(conn, "退出作业辅导")
        self.assertTrue(handled)
        self.assertIn("不在作业辅导模式", reply)

    def test_timeout_expire(self):
        conn = self._conn(
            active_mode=MODE_ID,
            homework_mode_entered_at=time.time() - DEFAULT_IDLE_TIMEOUT_SEC - 5,
        )
        handled, reply = try_resolve_mode_phrase(conn, "这道题怎么做")
        self.assertTrue(handled)
        self.assertIn("退出辅导模式", reply)
        self.assertIsNone(conn.active_mode)

    def test_zhiban_meta_sets_photo_pending(self):
        conn = self._conn(active_mode=MODE_ID)
        apply_zhiban_homework_meta(conn, {"homework_action": "photo_guide"})
        self.assertTrue(conn.homework_photo_pending)

    def test_photo_ready_after_pending(self):
        conn = self._conn(active_mode=MODE_ID, homework_photo_pending=True)
        handled, reply = try_resolve_homework_photo_phrase(conn, "好了")
        self.assertFalse(handled)
        self.assertIsNone(reply)
        self.assertFalse(conn.homework_photo_pending)
        self.assertTrue(conn.homework_photo_capture_now)

    def test_question_does_not_local_guide(self):
        """摆拍引导改由 zhiban knowledge_qa 意图下发，xiaozhi 不再靠关键词拦截。"""
        conn = self._conn(active_mode=MODE_ID)
        handled, reply = try_resolve_homework_photo_phrase(conn, "这道题怎么做")
        self.assertFalse(handled)
        self.assertIsNone(reply)
        self.assertFalse(conn.homework_photo_pending)


if __name__ == "__main__":
    unittest.main()
