/*
    HTTP Environment Sensor for sensors with HTTP server components
    Copyright Arktronic
    ISC License
    
    Note, a compatible HTTP server is required.
    CircuitPython and Arduino code is provided as a sample here: https://github.com/arktronic/hubitat/tree/main/http-environment-sensors

    License: ISC

    v0.2 - Add Airthings Wave Plus compatibility (radon, VOC, light level)
    v0.1 - Initial release
*/

metadata {
  definition(name: "HTTP Environment Sensor", namespace: "arktronic", author: "Sasha Kotlyar", importUrl: "https://raw.githubusercontent.com/arktronic/hubitat/main/http-environment-sensors/http-env-sensor-driver.groovy") {
    capability "PresenceSensor"
    capability "TemperatureMeasurement"
    capability "RelativeHumidityMeasurement"
    capability "PressureMeasurement"
    capability "AirQuality"
    capability "Sensor"
    capability "CarbonDioxideMeasurement"
    capability "IlluminanceMeasurement"
    
    attribute "gasResistance", "number"
    attribute "radon", "number"
    attribute "longTermRadon", "number"
    attribute "voc", "number"
    
    command "refresh"
  }
  preferences {
    input name: "targetHttpAddress", type: "string", title: "<b>Target Server HTTP Address</b>", description: "For example: http://192.168.1.23:80", required: true
    input name: "autoPoll", type: "bool", title: "Enable Auto Poll", description: "Refreshes data every 2 minutes", required: true, defaultValue: false
    input name: "radonUsePicocuriesPerLiter", type: "bool", title: "Use pCi/L for radon measurements", description: "(Otherwise, use Bq/m³)", required: true, defaultValue: true
  }
}

def installed() {
  configure()
}

def updated() {
  configure()
}

def configure() {
  unschedule()
  if (autoPoll) {
    Random rnd = new Random()
    def randomSeconds = rnd.nextInt(60)
    schedule("${randomSeconds} */2 * * * ?", "refresh")
  }
}

def refresh() {
  state.clear()
  try {
    httpGet(settings.targetHttpAddress) { resp ->
      if (resp.success) {
        sendEvent(name: "presence", value: "present")

        if (resp.data["temperature_c"]) {
          result = (location.temperatureScale == "F") ? celsiusToFahrenheit(resp.data["temperature_c"]) : resp.data["temperature_c"]
          result = (result as double).round(2)
          sendEvent(name: "temperature", value: result, unit: "°${location.temperatureScale}", descriptionText: "Temperature is ${result}°${location.temperatureScale}")
        } else {
          device.deleteCurrentState("temperature")
        }

        if (resp.data["humidity_percent"]) {
          result = (resp.data["humidity_percent"] as double).round()
          sendEvent(name: "humidity", value: result, descriptionText: "Humidity is ${result}%")
        } else {
          device.deleteCurrentState("humidity")
        }

        if (resp.data["pressure_hpa"]) {
          result = ((resp.data["pressure_hpa"] as double) * 100).round()
          sendEvent(name: "pressure", value: result, unit: "Pa", descriptionText: "Pressure is ${result} Pa")
        } else {
          device.deleteCurrentState("pressure")
        }

        if (resp.data["gas_ohm"]) {
          result = (resp.data["gas_ohm"] as int)
          sendEvent(name: "gasResistance", value: result, unit: "Ω", descriptionText: "Gas resistance is ${result} Ω")
        } else {
          device.deleteCurrentState("gasResistance")
        }

        if (resp.data["iaq"]) {
          sendEvent(name: "airQualityIndex", value: resp.data["iaq"], descriptionText: "Indoor Air Quality (IAQ) is ${resp.data["iaq"]}")
        } else {
          device.deleteCurrentState("airQualityIndex")
        }

        if (resp.data["co2_ppm"]) {
          result = (resp.data["co2_ppm"] as int)
          sendEvent(name: "carbonDioxide", value: result, unit: "ppm", descriptionText: "Carbon dioxide is ${result} ppm")
        } else {
          device.deleteCurrentState("carbonDioxide")
        }

        if (resp.data["radon_bq_m3"]) {
          result = (resp.data["radon_bq_m3"] as int) / (radonUsePicocuriesPerLiter ? 37.0 : 1.0)
          result = (result as double).round(2)
          rnUnit = (settings.radonUsePicocuriesPerLiter ? "pCi/L" : "Bq/m³")
          sendEvent(name: "radon", value: result, unit: rnUnit, descriptionText: "Radon level is ${result} ${rnUnit}")
        } else {
          device.deleteCurrentState("radon")
        }

        if (resp.data["long_term_radon_bq_m3"]) {
          result = (resp.data["long_term_radon_bq_m3"] as int) / (radonUsePicocuriesPerLiter ? 37.0 : 1.0)
          result = (result as double).round(2)
          rnUnit = (settings.radonUsePicocuriesPerLiter ? "pCi/L" : "Bq/m³")
          sendEvent(name: "longTermRadon", value: result, unit: rnUnit, descriptionText: "Long term radon level is ${result} ${rnUnit}")
        } else {
          device.deleteCurrentState("longTermRadon")
        }

        if (resp.data["voc_ppb"]) {
          result = (resp.data["voc_ppb"] as int)
          sendEvent(name: "voc", value: result, unit: "ppb", descriptionText: "VOC level is ${result} ppb")
        } else {
          device.deleteCurrentState("voc")
        }

        if (resp.data["light_level"]) {
          result = (resp.data["light_level"] as int)
          sendEvent(name: "illuminance", value: result, descriptionText: "Light level is ${result}")
        } else {
          device.deleteCurrentState("illuminance")
        }

        resp.data.each { key, val ->
          state[key] = val
        }

        state.lastUpdated = new Date().format("EEE, d MMM yyyy HH:mm:ss Z")
      }
    }
  } catch (Exception e) {
    log.warn "Call failed: ${e.message}"
    device.deleteCurrentState("temperature")
    device.deleteCurrentState("humidity")
    device.deleteCurrentState("pressure")
    device.deleteCurrentState("gasResistance")
    device.deleteCurrentState("airQualityIndex")
    sendEvent(name: "presence", value: "not present")
  }
}
