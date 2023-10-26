/*

This is an rtl_433 Child Device driver. It must be used in conjunction with the Parent Device driver.
Please see https://github.com/arktronic/hubitat/tree/main/rtl_433 for details.

License: ISC

v0.1 - Initial release
v0.2 - Stop forcing event status changes for battery level

*/

import java.time.LocalDateTime

metadata {
  definition(name: "rtl_433 Child Device Rubicson Temperature Sensor", namespace: "arktronic", author: "Sasha Kotlyar") {
    capability "Battery"
    capability "TemperatureMeasurement"
    capability "SignalStrength"
  }
}

def installed() {
  configure()
}

def updated() {
  configure()
}

def configure() {
  state.clear()
}

def processIncomingEvent(eventName, eventDataMap)
{
  if (eventDataMap["rssi"] != null) {
    sendEvent(name: "rssi", value: eventDataMap["rssi"])
  }
  if (eventDataMap["snr"] != null) {
    state.lastSnrValue = eventDataMap["snr"]
  }
  if (eventDataMap["time"] != null) {
    state.lastCommunicationsTimestamp = eventDataMap["time"]
    state.lastCommunicationsDate = new Date((eventDataMap["time"] as long) * 1000).format("EEE, d MMM yyyy HH:mm:ss Z")
  }
  if (eventDataMap["temperature_C"] != null) {
    temp = (location.temperatureScale == "F") ? ((eventDataMap["temperature_C"] * 1.8) + 32) : eventDataMap["temperature_C"]
    temp = (temp as double).round(2)
    sendEvent(name: "temperature", value: temp, unit: "°${location.temperatureScale}", descriptionText: "Temperature is ${temp}°${location.temperatureScale}")
  }
  if (eventDataMap["battery_ok"] != null) {
    batteryReport(eventDataMap["battery_ok"])
  }
}

def batteryReport(batteryOk) {
  state.batteryOk = batteryOk
  if (batteryOk < 1)
    sendEvent(name: "battery", value: 1, unit: "%", descriptionText: "$device.displayName battery is low", type: "physical")
  else
    sendEvent(name: "battery", value: 100, unit: "%", descriptionText: "$device.displayName battery is OK", type: "physical")
}
