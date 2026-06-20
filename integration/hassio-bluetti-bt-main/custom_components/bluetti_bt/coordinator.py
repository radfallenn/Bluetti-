"""Coordinator for Bluetti integration."""

from __future__ import annotations

import asyncio
from datetime import datetime, timedelta
import logging
import random
from typing import Any

import async_timeout
from homeassistant.components import bluetooth
from homeassistant.core import HomeAssistant
from homeassistant.helpers.update_coordinator import DataUpdateCoordinator
from bluetti_bt_lib import build_device, DeviceReader, DeviceReaderConfig

from .utils import mac_loggable
from .types import FullDeviceConfig


class PollingCoordinator(DataUpdateCoordinator):
    """Polling coordinator with resilient BLE retries and stale-data protection."""

    def __init__(
        self,
        hass: HomeAssistant,
        config: FullDeviceConfig,
        lock: asyncio.Lock,
    ):
        """Initialize coordinator."""
        super().__init__(
            hass,
            logging.getLogger(
                f"{__name__}.{mac_loggable(config.address).replace(':', '_')}"
            ),
            name="Bluetti polling coordinator",
            update_interval=timedelta(seconds=config.polling_interval),
        )

        self.config = config
        self._lock = lock
        self._last_good_data: dict[str, Any] | None = None
        self._last_success_at: datetime | None = None
        self._last_error: str | None = None
        self._consecutive_failures = 0
        self._successful_reads = 0
        self._failed_reads = 0
        self._bluetooth_missing_count = 0

        self.logger.info("Creating client for %s", config.name)
        bluetti_device = build_device(config.name)

        if bluetti_device is None:
            self.logger.error("Device is unknown type")
            self.async_shutdown()
            return None

        self.reader = DeviceReader(
            config.address,
            bluetti_device,
            self.hass.loop.create_future,
            self._build_reader_config(),
            lock,
        )

    def _build_reader_config(self) -> DeviceReaderConfig:
        """Build a reader config compatible with old and new bluetti-bt-lib versions."""
        try:
            return DeviceReaderConfig(
                self.config.polling_timeout,
                self.config.use_encryption,
                self.config.max_retries,
            )
        except TypeError:
            self.logger.debug(
                "Installed bluetti-bt-lib does not expose max_retries in DeviceReaderConfig"
            )
            return DeviceReaderConfig(
                self.config.polling_timeout,
                self.config.use_encryption,
            )

    @property
    def diagnostics(self) -> dict[str, Any]:
        """Return connection diagnostics for diagnostic entities and troubleshooting."""
        return {
            "online": self._consecutive_failures == 0 and self._last_good_data is not None,
            "degraded": self._consecutive_failures > 0 and self._last_good_data is not None,
            "last_success_at": self._last_success_at.isoformat() if self._last_success_at else None,
            "last_error": self._last_error,
            "consecutive_failures": self._consecutive_failures,
            "successful_reads": self._successful_reads,
            "failed_reads": self._failed_reads,
            "bluetooth_missing_count": self._bluetooth_missing_count,
            "polling_interval": self.config.polling_interval,
            "polling_timeout": self.config.polling_timeout,
            "max_retries": self.config.max_retries,
        }

    def _diagnostic_payload(self) -> dict[str, Any]:
        """Diagnostics included in coordinator data, ignored by normal Bluetti fields."""
        d = self.diagnostics
        return {
            "bluetti_connection_state": (
                "online" if d["online"] else "degraded" if d["degraded"] else "offline"
            ),
            "bluetti_consecutive_failures": d["consecutive_failures"],
            "bluetti_last_error": d["last_error"] or "",
        }

    async def _async_update_data(self):
        """Fetch data from the device with retries and keep the last valid state."""
        if not bluetooth.async_address_present(
            self.hass, self.config.address, connectable=True
        ):
            self._bluetooth_missing_count += 1
            self._last_error = "Bluetooth address not present/connectable"
            self._consecutive_failures += 1
            self._failed_reads += 1
            self.logger.warning(
                "Bluetti device %s not currently connectable; keeping last value if available",
                mac_loggable(self.config.address),
            )
            if self._last_good_data is not None:
                return {**self._last_good_data, **self._diagnostic_payload()}
            return self._diagnostic_payload()

        max_attempts = max(1, int(self.config.max_retries or 1))
        last_exception: Exception | None = None

        for attempt in range(1, max_attempts + 1):
            try:
                async with async_timeout.timeout(self.config.polling_timeout + 5):
                    data = await self.reader.read()

                if not isinstance(data, dict) or not data:
                    raise ValueError("Empty or invalid response from device")

                self._last_good_data = data
                self._last_success_at = datetime.now()
                self._last_error = None
                self._consecutive_failures = 0
                self._successful_reads += 1
                return {**data, **self._diagnostic_payload()}

            except (asyncio.TimeoutError, TimeoutError, EOFError, OSError, ValueError) as err:
                last_exception = err
                self._last_error = f"{type(err).__name__}: {err}"
                self.logger.debug(
                    "Read attempt %s/%s failed for %s: %s",
                    attempt,
                    max_attempts,
                    mac_loggable(self.config.address),
                    self._last_error,
                )
                if attempt < max_attempts:
                    await asyncio.sleep(min(2**attempt, 10) + random.uniform(0, 0.4))

        self._consecutive_failures += 1
        self._failed_reads += 1
        if last_exception is not None:
            self.logger.warning(
                "All read attempts failed for %s; keeping last valid state when possible: %s",
                mac_loggable(self.config.address),
                self._last_error,
            )

        if self._last_good_data is not None:
            return {**self._last_good_data, **self._diagnostic_payload()}

        return self._diagnostic_payload()
