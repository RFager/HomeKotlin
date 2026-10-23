package io.homekotlin.automations

import io.homekotlin.dsl.*
import io.homekotlin.generated.*
import kotlin.time.Duration.Companion.minutes

class PresenceLighting : HomeScript() {

    var others = false

    override fun configure() {
        automation("Hallway OPEN") {
            trigger.state(
                BinarySensor.hallwayEspPresence,
                to = BinarySensor.hallwayEspPresence.OPEN
            )
            action {
                val presence = BinarySensor.hallwayEspPresence.isOn
                val lux : Double = Sensors.hallwayEspLux.state.numericValue ?: 0.0
                if (lux < 20.0) {
                    services.light.turnOn(Light.hall)
                }
            }
        }

        automation("Porch Welcome & Auto-Off") {
            description("Turns on porch lights on motion, waits 3 minutes, then smoothly dims off")
            mode = AutomationMode.RESTART

            trigger.state(BinarySensor.ALTAN_DOOR, to = BinarySensor.ALTAN_DOOR.OPEN)

            action {
                if (BinarySensor.ALTAN_DOOR.state.isOn) {
                    log.info("Altan door is open. Turning on facade lights at full brightness.")
                    services.light.turnOn(Light.FACADE, brightness = 255)

                    // Wait 2 minutes then dim off
                    delay(2.minutes)

                    log.info("Timer expired. Dimming facade lights off.")
                    services.light.turnOff(Light.FACADE, transition = 5.0)
                }
            }
        }

        automation("Morning Espresso Machine Prep") {
            description("Powers on coffee maker on weekdays at 07:00 AM")

            trigger.cron("0 7 * * 1-5")

            action {
                log.info("Good morning! Pre-heating coffee maker.")
                services.switch.turnOn(Switch.COFFEE)
            }
        }
    }
}
