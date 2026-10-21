import io.homekotlin.dsl.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

class PresenceLightingAutomations : HomeScript() {
    init {
        configure()
    }

    override fun configure() {

        automation("Porch Welcome & Auto-Off") {
            description("Turns on porch lights on motion, waits 3 minutes, then smoothly dims off")

            trigger.state("binary_sensor.porch_motion", to = "on")

            action {
                log.info("Porch motion detected. Turning on lights at full brightness.")
                services.light.turnOn("light.porch", brightness = 255)

                // Wait for motion to clear or delay 2 minutes
                delay(2.minutes)

                log.info("Timer expired. Dimming porch lights off.")
                services.light.turnOff("light.porch", transition = 5.0)
            }
        }

        automation("Morning Espresso Machine Prep") {
            description("Powers on coffee maker on weekdays at 07:00 AM")

            trigger.time("07:00")

            action {
                log.info("Good morning! Pre-heating coffee maker.")
                services.switch.turnOn("switch.coffee_maker")
            }
        }
    }
}

PresenceLightingAutomations()
