# HomeKotlin Home Assistant Add-on

Write type-safe, reactive Home Assistant automations in **Kotlin** with live telemetry, hot reloading, and Ingress support.

## Features

- **Reactive Coroutines & Flow**: High-performance, non-blocking automations.
- **Expressive Kotlin DSL**:
  ```kotlin
  automation("Evening Lights") {
      trigger.state("binary_sensor.motion", to = "on")
      condition { isSunBelowHorizon() }
      action {
          services.light.turnOn("light.living_room", brightness = 200)
      }
  }
  ```
- **Type-Safe Compiled Automations**: Write high-performance automations with full IDE autocompletion and compile-time validation.
- **Ingress Web Dashboard**: View active automations, live state changes, execution statistics, and run manual tests directly in Home Assistant.
- **Type-Safe Entity Codegen**: Generate typed entity objects (`Entities.Light.LivingRoom`) from your Home Assistant state registry.

## Installation

1. In Home Assistant, navigate to **Settings** -> **Add-ons** -> **Add-on Store**.
2. Click the three dots in the top right -> **Repositories**.
3. Add your repository URL (or this repo).
4. Find **HomeKotlin** in the list, click **Install**, and turn on **Show in sidebar**.
5. Start the add-on and open the Web UI from the sidebar!

## Configuration

In the Add-on Configuration tab:
- `log_level`: `DEBUG`, `INFO`, `WARN`, or `ERROR`.
