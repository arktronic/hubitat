/*
    AirNow.gov US AQI reporting driver
    Copyright Arktronic
    ISC License
*/

metadata {
  definition(name: "AirNow.gov US AQI", namespace: "arktronic", author: "Sasha Kotlyar", importUrl: "https://raw.githubusercontent.com/arktronic/hubitat/main/airnow-us-aqi/airnow-driver.groovy") {
    capability "PresenceSensor"
    capability "AirQuality"

    command "refresh"
  }
  preferences {
    input name: "airNowApiKey", type: "string", title: "<b>AirNow.gov API key</b>", description: "To get an API key, register here: https://docs.airnowapi.org/account/request/", required: true
    input name: "autoPoll", type: "bool", title: "Enable Auto Poll", description: "Refreshes data every 30 minutes", required: true, defaultValue: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", required: true, defaultValue: false
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
    schedule("${randomSeconds} */30 * * * ?", "refresh")
  }
}

def aqiToColorName(int aqi) {
  if (aqi <= 50) return "Green";
  if (aqi <= 100) return "Yellow";
  if (aqi <= 150) return "Orange";
  if (aqi <= 200) return "Red";
  if (aql <= 300) return "Purple";
  return "Maroon";
}

def aqiToColorHex(int aqi) {
  if (aqi <= 50) return "#00e400";
  if (aqi <= 100) return "#ffff00";
  if (aqi <= 150) return "#ff7e00";
  if (aqi <= 200) return "#ff0000";
  if (aql <= 300) return "#8f3f97";
  return "#7e0023";
}
def refresh() {
  state.clear()
  try {
    localLat = location.latitude.toString()
    if (logEnable) log.debug("Latitude: ${localLat}")
    localLon = location.longitude.toString()
    if (logEnable) log.debug("Longitude: ${localLon}")
    url = "https://www.airnowapi.org/aq/observation/latLong/current/?format=application/json&latitude=${localLat}&longitude=${localLon}&distance=200&API_KEY=${airNowApiKey}"
    if (logEnable) log.debug("URL: ${url}")
    httpGet(url) { resp ->
      if (resp.success) {
        sendEvent(name: "presence", value: "present")
        if (logEnable) log.debug("Success! ${resp.data}")

        aqi = 0
        highestAqiDataIndex = 0
        for (int i = 0; i < resp.data.size(); i++) {
          if (resp.data[i]["AQI"] > aqi) {
            aqi = resp.data[i]["AQI"]
            highestAqiDataIndex = i
          }
        }

        aqiData = resp.data[highestAqiDataIndex]
        sendEvent(name: "airQualityIndex", value: aqiData["AQI"], descriptionText: "Air Quality Index (AQI) is ${aqiData["AQI"]}")

        state.dateObserved = aqiData["DateObserved"]
        state.hourObserved = aqiData["HourObserved"]
        state.localTimeZone = aqiData["LocalTimeZone"]
        state.reportingArea = aqiData["ReportingArea"]
        state.stateCode = aqiData["StateCode"]
        state.observationLat = aqiData["Latitude"]
        state.observationLon = aqiData["Longitude"]
        state.aqiCause = aqiData["ParameterName"]
        state.aqi = aqiData["AQI"]
        state.categoryNumber = aqiData["Category"]["Number"]
        state.categoryName = aqiData["Category"]["Name"]
        state.colorName = aqiToColorName(aqiData["AQI"])
        state.colorHex = aqiToColorHex(aqiData["AQI"])
        state.lastUpdated = new Date().format("EEE, d MMM yyyy HH:mm:ss Z")
      }
    }
  } catch (Exception e) {
    log.warn "Call failed: ${e.message}"
    sendEvent(name: "presence", value: "not present")
    throw e
  }
}
