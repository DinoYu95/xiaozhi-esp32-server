from abc import ABC, abstractmethod
from config.logger import setup_logging

TAG = __name__
logger = setup_logging()


class MemoryProviderBase(ABC):
    def __init__(self, config):
        self.config = config
        self.role_id = None

    def set_llm(self, llm):
        self.llm = llm

    @abstractmethod
    async def save_memory(self, msgs, session_id=None):
        """Save a new memory for specific role and return memory ID"""
        print("this is base func", msgs)

    @abstractmethod
    async def query_memory(self, query: str) -> str:
        """Query memories for specific role based on similarity"""
        return "please implement query method"

    def init_memory(self, role_id, llm, **kwargs):
        self.role_id = role_id
        self.llm = llm

    def switch_role(self, role_id):
        """多角色：切换当前记忆维度（每轮对话前按当前说话人调用）。"""
        self.role_id = role_id
