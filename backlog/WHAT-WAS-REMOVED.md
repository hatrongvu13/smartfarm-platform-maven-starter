# Removed from V1-V3 Core

The following are intentionally removed from the main implementation roadmap:

## 3D

Reason: useful visualization, but not required to operate a modern farm.

Can be added later as a consumer of Spatial + Warehouse APIs.

## Camera / AI

Reason: useful for advanced monitoring, but expensive in infrastructure, data, privacy, storage and model validation.

Can be added later without changing Inventory/Task/Telemetry ownership.

## YouTube

Reason: personal content feature, not farm operational core.

Keep outside core domain.

## What remains

IoT environmental automation stays because it has direct operational value:

temperature → heater
humidity → ventilation
gas concentration → ventilation/safety alert

The architecture still keeps extension points for future camera/3D/AI without making them dependencies.
