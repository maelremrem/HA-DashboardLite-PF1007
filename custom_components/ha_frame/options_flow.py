"""Home Assistant native editor for the PF1007 Canvas dashboard."""
from __future__ import annotations

from copy import deepcopy

import voluptuous as vol
from homeassistant import config_entries
from homeassistant.helpers import selector
from homeassistant.helpers.aiohttp_client import async_get_clientsession

from .api import FrameApi, FrameApiError
from .dashboard_editor import PRESETS, move, new_card, validate_pages


class FrameOptionsFlow(config_entries.OptionsFlow):
    """Edit a frame directly from Settings > Devices & services > Configure."""

    def __init__(self) -> None:
        self.snapshot: dict = {}
        self.page_index = 0
        self.card_index = 0

    def _api(self) -> FrameApi:
        entry = self.config_entry
        return FrameApi(async_get_clientsession(self.hass), entry.data["host"], entry.data["token"])

    @property
    def pages(self) -> list[dict]:
        return self.snapshot["pages"]

    async def _save(self, payload: dict) -> str | None:
        try:
            if "pages" in payload:
                validate_pages(payload["pages"])
            await self._api().update(payload)
        except ValueError:
            return "invalid_config"
        except FrameApiError as exc:
            return "invalid_config" if "Invalid request" in str(exc) else "cannot_connect"
        self.snapshot.update(deepcopy(payload))
        return None

    @staticmethod
    def _select(values: list[tuple[str, str]]) -> selector.SelectSelector:
        return selector.SelectSelector(selector.SelectSelectorConfig(
            options=[{"value": value, "label": label} for value, label in values]
        ))

    def _page_options(self) -> list[tuple[str, str]]:
        return [(str(i), f"{i + 1}. {p['title']} ({len(p['cards'])} tiles)")
                for i, p in enumerate(self.pages)]

    def _tile_options(self) -> list[tuple[str, str]]:
        cards = self.pages[self.page_index]["cards"]
        return [(str(i), f"{i + 1}. {c.get('label') or c.get('entity_id') or c['type']}")
                for i, c in enumerate(cards)]

    def _menu(self, step_id: str, actions: list[str]):
        return self.async_show_menu(step_id=step_id, menu_options=actions)

    async def async_step_init(self, user_input=None):
        try:
            self.snapshot = await self._api().config()
            validate_pages(self.snapshot["pages"])
        except (FrameApiError, KeyError, ValueError, TypeError):
            return self.async_show_form(
                step_id="init", data_schema=vol.Schema({}), errors={"base": "cannot_connect"}
            )
        self.page_index = min(self.page_index, len(self.pages) - 1)
        return self._menu("init", ["appearance", "css", "pages", "tiles_page", "done"])

    async def async_step_done(self, user_input=None):
        return self.async_create_entry(title="", data={})

    async def async_step_appearance(self, user_input=None):
        errors = {}
        if user_input is not None:
            title = user_input["dashboard_title"].strip()
            theme = user_input["theme"]
            if not 1 <= len(title) <= 28:
                errors["base"] = "invalid_config"
            else:
                values = {"dashboard_title": title}
                if theme != "keep":
                    values["custom_css"] = PRESETS[theme]
                error = await self._save(values)
                if error is None:
                    return await self.async_step_init()
                errors["base"] = error
        return self.async_show_form(step_id="appearance", errors=errors,
            data_schema=vol.Schema({
                vol.Required("dashboard_title", default=self.snapshot.get("dashboard_title", "HA FRAME")):
                    selector.TextSelector(),
                vol.Required("theme", default="keep"): self._select([
                    ("keep", "Keep current CSS"), ("original", "Original"),
                    ("ocean", "Ocean"), ("amber", "Amber"), ("light", "Light")
                ]),
            }))

    async def async_step_css(self, user_input=None):
        errors = {}
        if user_input is not None:
            css = user_input.get("custom_css", "")
            if len(css) > 12000:
                errors["base"] = "invalid_config"
            else:
                error = await self._save({"custom_css": css})
                if error is None:
                    return await self.async_step_init()
                errors["base"] = error
        return self.async_show_form(step_id="css", errors=errors, data_schema=vol.Schema({
            vol.Optional("custom_css", default=self.snapshot.get("custom_css", "")):
                selector.TextSelector(selector.TextSelectorConfig(multiline=True))
        }))

    async def async_step_pages(self, user_input=None):
        return self._menu("pages", ["page_add", "page_select", "page_move", "page_remove", "init"])

    async def async_step_page_add(self, user_input=None):
        errors = {}
        if user_input is not None:
            if len(self.pages) >= 4:
                errors["base"] = "limit_reached"
            else:
                pages = deepcopy(self.pages)
                pages.append({"title": user_input["title"].strip(),
                              "columns": int(user_input["columns"]), "cards": []})
                error = await self._save({"pages": pages})
                if error is None:
                    return await self.async_step_pages()
                errors["base"] = error
        return self.async_show_form(step_id="page_add", errors=errors, data_schema=vol.Schema({
            vol.Required("title", default=f"Page {len(self.pages) + 1}"): selector.TextSelector(),
            vol.Required("columns", default="3"): self._select([
                ("2", "2"), ("3", "3"), ("4", "4")]),
        }))

    async def async_step_page_select(self, user_input=None):
        if user_input is not None:
            self.page_index = int(user_input["page"])
            return await self.async_step_page_details()
        return self.async_show_form(step_id="page_select", data_schema=vol.Schema({
            vol.Required("page", default=str(self.page_index)): self._select(self._page_options()),
        }))

    async def async_step_page_details(self, user_input=None):
        page = self.pages[self.page_index]
        errors = {}
        if user_input is not None:
            pages = deepcopy(self.pages)
            pages[self.page_index]["title"] = user_input["title"].strip()
            pages[self.page_index]["columns"] = int(user_input["columns"])
            error = await self._save({"pages": pages})
            if error is None:
                return await self.async_step_pages()
            errors["base"] = error
        return self.async_show_form(step_id="page_details", errors=errors,
            data_schema=vol.Schema({
                vol.Required("title", default=page["title"]): selector.TextSelector(),
                vol.Required("columns", default=str(page["columns"])): self._select([
                    ("2", "2"), ("3", "3"), ("4", "4")]),
            }))

    async def async_step_page_move(self, user_input=None):
        errors = {}
        if user_input is not None:
            pages = deepcopy(self.pages)
            if not move(pages, int(user_input["page"]), -1 if user_input["direction"] == "up" else 1):
                errors["base"] = "invalid_move"
            else:
                error = await self._save({"pages": pages})
                if error is None:
                    return await self.async_step_pages()
                errors["base"] = error
        return self.async_show_form(step_id="page_move", errors=errors,
            data_schema=vol.Schema({
                vol.Required("page"): self._select(self._page_options()),
                vol.Required("direction"): self._select([("up", "Up"), ("down", "Down")]),
            }))

    async def async_step_page_remove(self, user_input=None):
        errors = {}
        if user_input is not None:
            if len(self.pages) < 2:
                errors["base"] = "last_page"
            elif not user_input["confirm"]:
                errors["base"] = "not_confirmed"
            else:
                pages = deepcopy(self.pages)
                pages.pop(int(user_input["page"]))
                error = await self._save({"pages": pages})
                if error is None:
                    self.page_index = 0
                    return await self.async_step_pages()
                errors["base"] = error
        return self.async_show_form(step_id="page_remove", errors=errors,
            data_schema=vol.Schema({
                vol.Required("page"): self._select(self._page_options()),
                vol.Required("confirm", default=False): bool,
            }))

    async def async_step_tiles_page(self, user_input=None):
        if user_input is not None:
            self.page_index = int(user_input["page"])
            return await self.async_step_tiles()
        return self.async_show_form(step_id="tiles_page", data_schema=vol.Schema({
            vol.Required("page", default=str(self.page_index)): self._select(self._page_options()),
        }))

    async def async_step_tiles(self, user_input=None):
        return self._menu("tiles", ["tile_add", "tile_select", "tile_move", "tile_remove", "tiles_page", "init"])

    @staticmethod
    def _tile_schema(card: dict | None = None):
        card = card or new_card("metric", "New tile")
        return vol.Schema({
            vol.Required("type", default=card["type"]): FrameOptionsFlow._select([
                ("metric", "Sensor"), ("control", "Action"), ("gauge", "Gauge"),
                ("thermostat", "Thermostat"), ("light", "Light"), ("cover", "Cover")
            ]),
            vol.Required("label", default=card["label"]): selector.TextSelector(),
            (vol.Optional("entity_id", default=card["entity_id"]) if card["entity_id"]
             else vol.Optional("entity_id")): selector.EntitySelector(),
            vol.Optional("clear_entity", default=False): bool,
            vol.Required("min", default=card.get("min", 0)): vol.Coerce(float),
            vol.Required("max", default=card.get("max", 100)): vol.Coerce(float),
        })

    @staticmethod
    def _updated_card(user_input: dict, old: dict | None = None) -> dict:
        entity_id = "" if user_input.get("clear_entity") else user_input.get("entity_id", old.get("entity_id", "") if old else "")
        card = new_card(user_input["type"], user_input["label"], entity_id,
                        user_input["min"], user_input["max"])
        domain = entity_id.split(".", 1)[0]
        required = {"thermostat": "climate", "light": "light", "cover": "cover"}.get(card["type"])
        if entity_id and required and domain != required:
            raise ValueError("Entity domain does not match card type")
        if entity_id and card["type"] == "control" and domain not in {
            "light", "switch", "fan", "input_boolean", "cover", "scene", "script", "button", "input_button"
        }:
            raise ValueError("This entity does not support the action card")
        return card

    async def async_step_tile_add(self, user_input=None):
        errors = {}
        if user_input is not None:
            pages = deepcopy(self.pages)
            page = pages[self.page_index]
            if len(page["cards"]) >= page["columns"] * 3:
                errors["base"] = "limit_reached"
            else:
                try:
                    page["cards"].append(self._updated_card(user_input))
                    error = await self._save({"pages": pages})
                except ValueError:
                    error = "invalid_config"
                if error is None:
                    return await self.async_step_tiles()
                errors["base"] = error
        return self.async_show_form(step_id="tile_add", errors=errors,
            data_schema=self._tile_schema())

    async def async_step_tile_select(self, user_input=None):
        if user_input is not None:
            self.card_index = int(user_input["tile"])
            return await self.async_step_tile_details()
        if not self._tile_options():
            return await self.async_step_tiles()
        return self.async_show_form(step_id="tile_select", data_schema=vol.Schema({
            vol.Required("tile"): self._select(self._tile_options()),
        }))

    async def async_step_tile_details(self, user_input=None):
        old = self.pages[self.page_index]["cards"][self.card_index]
        errors = {}
        if user_input is not None:
            try:
                tile = self._updated_card(user_input, old)
                pages = deepcopy(self.pages)
                pages[self.page_index]["cards"][self.card_index] = tile
                error = await self._save({"pages": pages})
            except ValueError:
                error = "invalid_config"
            if error is None:
                return await self.async_step_tiles()
            errors["base"] = error
        return self.async_show_form(step_id="tile_details", errors=errors,
            data_schema=self._tile_schema(old))

    async def async_step_tile_move(self, user_input=None):
        errors = {}
        if user_input is not None:
            pages = deepcopy(self.pages)
            cards = pages[self.page_index]["cards"]
            if not move(cards, int(user_input["tile"]), -1 if user_input["direction"] == "up" else 1):
                errors["base"] = "invalid_move"
            else:
                error = await self._save({"pages": pages})
                if error is None:
                    return await self.async_step_tiles()
                errors["base"] = error
        if not self._tile_options():
            return await self.async_step_tiles()
        return self.async_show_form(step_id="tile_move", errors=errors,
            data_schema=vol.Schema({
                vol.Required("tile"): self._select(self._tile_options()),
                vol.Required("direction"): self._select([("up", "Up"), ("down", "Down")]),
            }))

    async def async_step_tile_remove(self, user_input=None):
        errors = {}
        if user_input is not None:
            if not user_input["confirm"]:
                errors["base"] = "not_confirmed"
            else:
                pages = deepcopy(self.pages)
                pages[self.page_index]["cards"].pop(int(user_input["tile"]))
                error = await self._save({"pages": pages})
                if error is None:
                    return await self.async_step_tiles()
                errors["base"] = error
        if not self._tile_options():
            return await self.async_step_tiles()
        return self.async_show_form(step_id="tile_remove", errors=errors,
            data_schema=vol.Schema({
                vol.Required("tile"): self._select(self._tile_options()),
                vol.Required("confirm", default=False): bool,
            }))