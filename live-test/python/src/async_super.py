"""Async def override across ABC + impl."""
from abc import ABC, abstractmethod


class AsyncFetcher(ABC):
    @abstractmethod
    async def fetch(self) -> str:
        ...


class AsyncImpl(AsyncFetcher):
    async def fetch(self) -> str:
        return "fetched"
