"""Secure, opt-in import of images from Home Assistant's configured media_dirs."""
from __future__ import annotations

import asyncio
import base64
import hashlib
from pathlib import Path

MAX_PHOTOS = 100
MAX_SOURCE_BYTES = 6 * 1024 * 1024
CHUNK = 60000
EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def _root(hass, source: str) -> Path:
    dirs = hass.config.media_dirs or {}
    if source not in dirs:
        raise ValueError("Unknown Home Assistant media directory")
    root = Path(dirs[source]).resolve(strict=True)
    if not root.is_dir():
        raise ValueError("Media directory unavailable")
    return root


def _folder(hass, source: str, name: str) -> Path:
    if not isinstance(name, str) or len(name) > 160 or name.startswith("/") or (
        "\\" in name or "\x00" in name or any(p in {"..", "."} for p in name.split("/"))
    ):
        raise ValueError("Invalid album folder")
    root = _root(hass, source)
    folder = (root / name).resolve(strict=True)
    if not folder.is_relative_to(root) or not folder.is_dir():
        raise ValueError("Album is outside configured media folder")
    return folder


def _browse(hass, source: str, folder: str) -> dict:
    directory = _folder(hass, source, folder)
    child_folders: list[str] = []
    photo_count = 0
    for child in sorted(directory.iterdir(), key=lambda p: p.name.casefold())[:300]:
        if child.is_symlink():
            continue
        if child.is_dir() and len(child_folders) < 100:
            child_folders.append(child.name)
        elif child.is_file() and child.suffix.casefold() in EXTENSIONS:
            photo_count += 1
    return {"folders": child_folders, "photos": photo_count}


def _photos(hass, source: str, folder: str) -> list[Path]:
    base = _folder(hass, source, folder)
    result: list[Path] = []
    for path in sorted(base.rglob("*"), key=lambda p: str(p).casefold()):
        if len(result) >= MAX_PHOTOS:
            break
        if path.is_symlink() or not path.is_file() or path.suffix.casefold() not in EXTENSIONS:
            continue
        if not path.resolve().is_relative_to(base):
            continue
        if 20 <= path.stat().st_size <= MAX_SOURCE_BYTES:
            result.append(path)
    return result


def _read_photo(path: Path) -> tuple[bytes, str]:
    data = path.read_bytes()
    if not 20 <= len(data) <= MAX_SOURCE_BYTES:
        raise ValueError("Photo size changed during sync")
    return data, hashlib.sha256(data).hexdigest()


async def browse(hass, source: str, folder: str) -> dict:
    return await hass.async_add_executor_job(_browse, hass, source, folder)


async def sync(hass, coordinator, source: str, folder: str) -> dict:
    """Transfer only missing files; the device validates the hash and decodes safely."""
    locks = hass.data.setdefault("_ha_frame_album_locks", {})
    lock = locks.setdefault(coordinator.entry.entry_id, asyncio.Lock())
    if lock.locked():
        raise ValueError("An album sync is already active for this frame")
    async with lock:
        files = await hass.async_add_executor_job(_photos, hass, source, folder)
        existing = {
            item["id"] for item in await coordinator.api.photos()
            if isinstance(item, dict) and isinstance(item.get("id"), str)
        }
        transferred = 0
        skipped = 0
        for file in files:
            data, sha = await hass.async_add_executor_job(_read_photo, file)
            ident = f"photo_{sha[:16]}.jpg"
            if ident in existing:
                skipped += 1
                continue
            if len(existing) >= MAX_PHOTOS:
                break
            session = await coordinator.api.media_begin(len(data), sha)
            for index, offset in enumerate(range(0, len(data), CHUNK)):
                encoded = base64.b64encode(data[offset:offset+CHUNK]).decode("ascii")
                await coordinator.api.media_chunk(session, index, encoded)
            image_id = await coordinator.api.media_finish(session)
            existing.add(image_id)
            transferred += 1
        return {
            "transferred": transferred, "unchanged": skipped,
            "total_checked": len(files), "library_size": len(existing),
        }
