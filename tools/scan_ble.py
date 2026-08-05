"""Collect nearby BLE advertisements and emit a signal-ranked JSON summary."""

from __future__ import annotations

import argparse
import asyncio
import json
import statistics
from collections import defaultdict
from typing import Any

from bleak import BleakScanner


async def scan(seconds: int) -> list[dict[str, Any]]:
    observations: dict[str, list[dict[str, Any]]] = defaultdict(list)

    def on_advertisement(device: Any, advertisement: Any) -> None:
        observations[device.address].append(
            {
                "name": advertisement.local_name or device.name or "",
                "rssi": advertisement.rssi,
                "tx_power": advertisement.tx_power,
                "manufacturer_data": {
                    f"0x{company_id:04X}": payload.hex().upper()
                    for company_id, payload in advertisement.manufacturer_data.items()
                },
                "service_uuids": sorted(advertisement.service_uuids),
                "service_data": {
                    uuid: payload.hex().upper()
                    for uuid, payload in advertisement.service_data.items()
                },
            }
        )

    scanner = BleakScanner(detection_callback=on_advertisement, scanning_mode="active")
    async with scanner:
        await asyncio.sleep(seconds)

    summary = []
    for address, rows in observations.items():
        rssis = [int(row["rssi"]) for row in rows if row["rssi"] is not None]
        if not rssis:
            continue
        names = sorted({row["name"] for row in rows if row["name"]})
        tx_powers = sorted(
            {int(row["tx_power"]) for row in rows if row["tx_power"] is not None}
        )
        manufacturer_data: dict[str, set[str]] = defaultdict(set)
        service_data: dict[str, set[str]] = defaultdict(set)
        service_uuids: set[str] = set()
        for row in rows:
            for company_id, payload in row["manufacturer_data"].items():
                manufacturer_data[company_id].add(payload)
            for uuid, payload in row["service_data"].items():
                service_data[uuid].add(payload)
            service_uuids.update(row["service_uuids"])
        summary.append(
            {
                "address": address,
                "name": " / ".join(names),
                "max_rssi": max(rssis),
                "avg_rssi": round(statistics.fmean(rssis), 1),
                "min_rssi": min(rssis),
                "samples": len(rssis),
                "tx_power": tx_powers,
                "manufacturer_data": {
                    company_id: sorted(payloads)
                    for company_id, payloads in manufacturer_data.items()
                },
                "service_uuids": sorted(service_uuids),
                "service_data": {
                    uuid: sorted(payloads) for uuid, payloads in service_data.items()
                },
            }
        )
    return sorted(summary, key=lambda row: (row["max_rssi"], row["avg_rssi"]), reverse=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--seconds", type=int, default=40, choices=range(5, 121))
    args = parser.parse_args()
    print(json.dumps(asyncio.run(scan(args.seconds)), indent=2))


if __name__ == "__main__":
    main()
