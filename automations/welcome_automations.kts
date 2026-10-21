import io.homekotlin.dsl.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

class WelcomeAutomations : HomeScript() {
    override fun configure() {

        automation("Living Room Evening Motion") {
            description("Turns on ambient light when motion is detected after dark")

            trigger.state("binary_sensor.living_room_motion", to = "on")
            condition { isSunBelowHorizon() }

            action {
                log.info("Motion detected after sunset. Turning on living room lights.")
                services.light.turnOn(
                    entityId = "light.living_room",
                    brightness = 200,
                    transition = 1.5
                )
            }
        }

        automation("Front Door Security Alert") {
            description("Sends notification when the front door opens")

            trigger.state("binary_sensor.front_door", to = "on")

            action {
                log.info("Front door opened!")
                services.notify.send("Front door was opened", title = "Home Security")
            }
        }

        automation("Living Room Climate Guard") {
            description("Adjusts thermostat target if room temperature exceeds 23°C")

            trigger.numeric("sensor.living_room_temperature", above = 23.0)

            action {
                log.info("Living room is warmer than 23°C. Setting cooling.")
                services.climate.setTemperature("climate.living_room", 21.0)
            }
        }

        every(15.minutes) {
            val temp = states["sensor.living_room_temperature"]?.state ?: "unknown"
            val sun = states["sun.sun"]?.state ?: "unknown"
            log.info("Periodic Home Audit: Sun is $sun, Temp is $temp°C")
        }
    }
}

WelcomeAutomations()
