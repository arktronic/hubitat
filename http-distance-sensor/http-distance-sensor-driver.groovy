/*
    HTTP Distance Sensor for sensors with HTTP server components
    Copyright Arktronic
    ISC License
    
    Note, a compatible HTTP server is required.
    Arduino code is provided as a sample here: https://github.com/arktronic/hubitat/tree/main/http-distance-sensor
*/

metadata {
  definition(name: "HTTP Distance Sensor", namespace: "arktronic", author: "Sasha Kotlyar", importUrl: "https://raw.githubusercontent.com/arktronic/hubitat/main/http-distance-sensor/http-distance-sensor-driver.groovy") {
    capability "PresenceSensor"
    capability "Sensor"

    attribute "distance", "int"
    attribute "distance_validated", "boolean"

    command "refresh"
  }
  preferences {
    input name: "targetHttpAddress", type: "string", title: "<b>Target Server HTTP Address</b>", description: "For example: http://192.168.1.23:80", required: true
    input name: "autoPoll", type: "bool", title: "Enable Auto Poll", description: "Refreshes data every 2 minutes", required: true, defaultValue: false
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

        if (resp.data["distance_mm"]) {
          result = resp.data["distance_mm"]
          sendEvent(name: "distance", value: result, unit: "mm", descriptionText: "Distance is ${result} mm")
        } else {
          device.deleteCurrentState("distance")
        }

        if (resp.data["distance_validated"]) {
          if (resp.data["distance_validated"] as boolean) {
            sendEvent(name: "distance_validated", value: true, descriptionText: "Distance is considered valid")
          } else {
            sendEvent(name: "distance_validated", value: false, descriptionText: "Distance is NOT considered valid")
          }
        } else {
          device.deleteCurrentState("distance_validated")
        }

        resp.data.each { key, val ->
          state[key] = val
        }

        state.lastUpdated = new Date().format("EEE, d MMM yyyy HH:mm:ss Z")
      }
    }
  } catch (Exception e) {
    log.warn "Call failed: ${e.message}"
    device.deleteCurrentState("distance")
    device.deleteCurrentState("distance_validated")
    sendEvent(name: "presence", value: "not present")
  }
}
