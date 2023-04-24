import java.time.LocalDateTime

metadata {
  definition(name: "HTTP Environment Sensor", namespace: "arktronic", author: "Sasha Kotlyar") {
    capability "PresenceSensor"
    capability "TemperatureMeasurement"
    capability "RelativeHumidityMeasurement"
    capability "PressureMeasurement"
    capability "AirQuality"
    
    command "refresh"
  }
  preferences {
		input name: "targetHttpAddress", type: "string", title: "<b>Target Server HTTP Address</b>", description: "For example: http://192.168.1.23:80", required: true
    input name: "autoPoll", type: "bool", title: "Enable Auto Poll", required: true, defaultValue: false
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
    schedule("${randomSeconds} * * * * ?", "refresh")
  }
}

def refresh() {
  try {
    httpGet(settings.targetHttpAddress) { resp ->
      if (resp.success) {
        sendEvent(name: "presence", value: "present")

        if (resp.data["temperature_c"]) {
          result = (location.temperatureScale == "F") ? ((resp.data["temperature_c"] * 1.8) + 32) : resp.data["temperature_c"]
          result = (result as double).round(2)
          sendEvent(name: "temperature", value: result, unit: "°${location.temperatureScale}", descriptionText: "Temperature is ${result}°${location.temperatureScale}")
        }

        if (resp.data["humidity_percent"]) {
          result = (resp.data["humidity_percent"] as double).round()
          sendEvent(name: "humidity", value: result, descriptionText: "Humidity is ${result}%")
        }

        if (resp.data["pressure_hpa"]) {
          result = ((resp.data["pressure_hpa"] as double) * 100).round()
          sendEvent(name: "pressure", value: result, unit: "Pa", descriptionText: "Pressure is ${result} Pa")
        }

        if (resp.data["aqi"]) {
          sendEvent(name: "airQualityIndex", value: resp.data["aqi"], descriptionText: "AQI is ${resp.data["aqi"]}")
        }

        resp.data.each { key, val ->
          state[key] = val
        }

        state.lastUpdated = new Date().format("EEE, d MMM yyyy HH:mm:ss Z")
      }
    }
  } catch (Exception e) {
    log.warn "Call failed: ${e.message}"
    sendEvent(name: "presence", value: "not present")
  }
}
