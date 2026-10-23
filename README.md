# HomeKotlin

<p align="center">
  <strong>Type-Safe, Reactive Home Assistant Automations in Kotlin</strong><br>
  <em>A modern, code-first automation platform and Home Assistant Add-on featuring hot reloading, a live telemetry dashboard, and Ingress integration.</em>
</p>

---

## Overview

**HomeKotlin** brings the ergonomics, static type-safety, and reactive power of Kotlin to Home Assistant. Think of it as **NetDaemon** or a code-driven **Node-RED** designed natively for the Kotlin/JVM ecosystem:

- 🚀 **Full Reactive Kotlin DSL**: Build clean, expressive automations using Coroutines and Kotlin Flow.
- ⚡ **Type-Safe Automations**: Write compiled Kotlin automations with full IDE completion, refactoring, and instant execution.
- 🎛️ **Ingress Web Dashboard**: Seamlessly embedded in Home Assistant's sidebar via Ingress. Monitor active automations, inspect real-time state changes and execution logs, toggle automations, and trigger manual test runs.
- 🧩 **Type-Safe Entity Codegen**: Generate typed Kotlin classes (`Entities.Light.LivingRoom`) from your Home Assistant entity registry with one click or CLI flag.
- 📦 **Home Assistant Add-on Ready**: Complete with `config.yaml`, multi-arch `Dockerfile`, Ingress proxying, and Supervisor API token discovery.
- 🛠️ **Demo / Simulation Mode**: Test and develop locally out of the box even without a live Home Assistant connection.

---

## 🎨 Writing Automations

Automations live directly in **`homekotlin/src/main/kotlin/io/homekotlin/automations/`** (e.g. [PresenceLighting.kt](file:///Users/rickard/Workspaces/HomeKotlin/homekotlin/src/main/kotlin/io/homekotlin/automations/PresenceLighting.kt)):
- **Full IDE Support**: Autocomplete, parameter info, documentation, refactoring, and "Go to Definition" work out of the box with zero setup.
- **Compile-Time Safety**: Checked and validated at build time (`./gradlew test`).
- **Explicit Registration**: Clean, type-safe registration in [Automations.kt](file:///Users/rickard/Workspaces/HomeKotlin/homekotlin/src/main/kotlin/io/homekotlin/automations/Automations.kt) with zero reflection or runtime classpath scanning.

### 1. Declarative Automation

```kotlin
import io.homekotlin.dsl.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

class EveningLighting : HomeScript() {
    init { configure() }

    override fun configure() {
        automation("Living Room Evening Motion") {
            description("Turns on ambient lights if motion is detected after dark")

            trigger.state("binary_sensor.living_room_motion", to = "on")
            condition { isSunBelowHorizon() }

            action {
                log.info("Motion detected after dark! Turning on lights.")
                services.light.turnOn(
                    entityId = "light.living_room",
                    brightness = 200,
                    transition = 1.5
                )
            }
        }
    }
}

EveningLighting()
```

### 2. Multi-Step Delays & Presence Flow

```kotlin
automation("Porch Welcome & Auto-Off") {
    description("Turns on porch lights on motion, waits 2 minutes, then dims off")

    trigger.state("binary_sensor.porch_motion", to = "on")

    action {
        services.light.turnOn("light.porch", brightness = 255)

        // Asynchronous non-blocking delay
        delay(2.minutes)

        services.light.turnOff("light.porch", transition = 5.0)
    }
}
```

### 3. Numeric & Climate Triggers

```kotlin
automation("Climate Humidity Guard") {
    trigger.numeric("sensor.bathroom_humidity", above = 70.0)

    action {
        services.switch.turnOn("switch.exhaust_fan")
        
        // Wait until humidity drops below 55%
        waitUntil(timeout = 30.minutes) {
            (states.getNumber("sensor.bathroom_humidity") ?: 0.0) < 55.0
        }

        services.switch.turnOff("switch.exhaust_fan")
    }
}
```

### 4. Concise Reactive Helpers

```kotlin
listenState("binary_sensor.front_door", to = "on") {
    services.notify.send("Front door opened", title = "Security")
}

every(15.minutes) {
    val temp = states["sensor.living_room_temperature"]?.state
    log.info("Current temperature is $temp°C")
}
```

---

## 🖥️ Ingress Web Dashboard

When running as an Add-on or standalone, HomeKotlin provides a dark-mode web dashboard on port `8099`:

1. **Active Automations**: Card overview with execution counters, last triggered relative timestamps, speed in ms, and on/off toggles.
2. **Manual Test ("Run Now")**: Trigger any automation on demand to verify actions and conditions.
3. **Live Event Feed**: Real-time Server-Sent Events (SSE) terminal streaming Home Assistant state changes and automation execution logs.
4. **Entity Inspector**: Search, inspect, and filter cached Home Assistant entities by domain (`light`, `climate`, `sensor`, `switch`, etc.).
5. **Automation Manager**: Inspect active automations, execution diagnostics, and trigger live reloads.
6. **Entity Codegen**: Preview and copy auto-generated `Entities.kt` code for static type-safety in your IDE.

---

## 🚀 Installation & Deployment

### Option A: Home Assistant Add-on (Recommended)

1. Add this repository to Home Assistant:
   - Go to **Settings** -> **Add-ons** -> **Add-on Store**.
   - Click the three dots (top-right) -> **Repositories**.
   - Add your Git URL or copy the [homekotlin](file:///Users/rickard/Workspaces/HomeKotlin/homekotlin) folder to your HA `addons/` directory.
2. Select **HomeKotlin**, click **Install**.
3. Enable **Show in sidebar**, then click **Start**.
4. Open **HomeKotlin** from the sidebar!

### Option B: Docker Compose (Standalone)

```bash
docker compose up -d
```

Configured via environment variables in `docker-compose.yml`:
- `HA_URL`: `ws://<your-ha-ip>:8123/api/websocket`
- `HA_TOKEN`: Long-Lived Access Token generated from Home Assistant profile.

### Option C: Local Development with Gradle

HomeKotlin includes a built-in simulation mode if no token is configured:

```bash
# Build standalone distribution
./gradlew installDist

# Run HomeKotlin locally
./build/install/homekotlin/bin/homekotlin
```

Open `http://localhost:8099` in your browser.

To regenerate type-safe entity classes from a live Home Assistant instance:
```bash
HA_URL="ws://homeassistant.local:8123/api/websocket" HA_TOKEN="<token>" ./build/install/homekotlin/bin/homekotlin --generate-entities
```

---

## 📂 Project Structure

```
HomeKotlin/
├── homekotlin/                      # Home Assistant Add-on packaging & app
│   ├── config.yaml                  # HA Add-on manifest (Ingress enabled)
│   ├── Dockerfile                   # Multi-stage production container
│   ├── run.sh                       # Supervisor entrypoint script
│   └── DOCS.md                      # Add-on store documentation
│   └── src/main/kotlin/io/homekotlin/
│       ├── HomeKotlinApp.kt         # Main entry point & lifecycle
│       ├── automations/             # Automation classes & Automations.kt registry
│       ├── config/Config.kt         # Supervisor / options.json loader
│       ├── ha/                      # Home Assistant WebSocket client & state store
│       ├── dsl/                     # AutomationBuilder, Triggers, Services
│       ├── codegen/                 # EntityCodeGenerator
│       └── server/                  # Ktor Ingress server & SSE streaming
│   └── src/main/resources/web/      # Embedded Ingress Web Dashboard
│       ├── index.html               # Glassmorphic single-page dashboard
│       ├── app.css                  # Modern dark theme & design tokens
│       └── app.js                   # Live SSE feed & API controller
└── repository.yaml                  # Home Assistant Add-on Repository manifest
```
