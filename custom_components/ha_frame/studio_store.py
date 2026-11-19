"""Per-frame Studio snapshots and reusable layout profiles; no credentials stored."""
from __future__ import annotations

import asyncio
from copy import deepcopy
from datetime import datetime, timezone
import uuid

from homeassistant.helpers.storage import Store

KEY = "ha_frame_studio_v2"
MAX_PROFILES = 20
MAX_SNAPSHOTS = 8


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _state(hass):
    return hass.data.setdefault("_ha_frame_studio_store", {
        "store": Store(hass, 1, KEY, private=True),
        "lock": asyncio.Lock(),
    })


async def _load(storage) -> dict:
    data = await storage["store"].async_load()
    if not isinstance(data, dict):
        return {"profiles": [], "snapshots": {}}
    return {
        "profiles": data.get("profiles", []),
        "snapshots": data.get("snapshots", {}),
    }


async def list_profiles(hass) -> list[dict]:
    state = _state(hass)
    async with state["lock"]:
        data = await _load(state)
        return [{"id": p["id"], "name": p["name"], "created": p["created"]}
                for p in data["profiles"]]


async def get_profile(hass, profile_id: str) -> dict | None:
    state = _state(hass)
    async with state["lock"]:
        data = await _load(state)
        for item in data["profiles"]:
            if item["id"] == profile_id:
                return deepcopy(item["config"])
        return None


async def save_profile(hass, name: str, config: dict) -> str:
    if not isinstance(name, str) or not 1 <= len(name.strip()) <= 48:
        raise ValueError("Profile name must be 1–48 characters")
    state = _state(hass)
    async with state["lock"]:
        data = await _load(state)
        if len(data["profiles"]) >= MAX_PROFILES:
            raise ValueError("Maximum 20 saved profiles")
        profile_id = uuid.uuid4().hex
        data["profiles"].append({
            "id": profile_id, "name": name.strip(), "created": _now(),
            "config": deepcopy(config),
        })
        await state["store"].async_save(data)
        return profile_id


async def delete_profile(hass, profile_id: str) -> bool:
    state = _state(hass)
    async with state["lock"]:
        data = await _load(state)
        previous = len(data["profiles"])
        data["profiles"] = [p for p in data["profiles"] if p["id"] != profile_id]
        if previous == len(data["profiles"]):
            return False
        await state["store"].async_save(data)
        return True


async def capture_snapshot(hass, frame_id: str, revision: str, config: dict) -> None:
    """Record the config before a successful layout update, never bearer tokens."""
    state = _state(hass)
    async with state["lock"]:
        data = await _load(state)
        history = data["snapshots"].setdefault(frame_id, [])
        if not history or history[-1]["revision"] != revision:
            history.append({
                "revision": revision, "created": _now(),
                "config": deepcopy(config),
            })
            data["snapshots"][frame_id] = history[-MAX_SNAPSHOTS:]
            await state["store"].async_save(data)


async def list_snapshots(hass, frame_id: str) -> list[dict]:
    state = _state(hass)
    async with state["lock"]:
        data = await _load(state)
        return [{"revision": p["revision"], "created": p["created"]}
                for p in data["snapshots"].get(frame_id, [])]


async def get_snapshot(hass, frame_id: str, revision: str) -> dict | None:
    state = _state(hass)
    async with state["lock"]:
        data = await _load(state)
        for item in data["snapshots"].get(frame_id, []):
            if item["revision"] == revision:
                return deepcopy(item["config"])
        return None
