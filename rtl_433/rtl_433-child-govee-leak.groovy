/*

This is an rtl_433 Child Device driver. It must be used in conjunction with the Parent Device driver.
Please see https://github.com/arktronic/hubitat/tree/main/rtl_433 for details.

License: ISC

v0.1 - Initial release

*/

import java.time.LocalDateTime

metadata {
  definition(name: "rtl_433 Child Device Govee Water Leak Detector H5054", namespace: "arktronic", author: "Sasha Kotlyar") {
    capability "Battery"
    capability "PushableButton"
    capability "SignalStrength"
    capability "WaterSensor"
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
  sendEvent(name: "numberOfButtons", value: 1)
}

def processIncomingEvent(eventName, eventDataMap)
{
  switch (eventName) {
    case "Button Press":
      pushButton()
      break
    case "Water Leak":
      leakDetected()
      break
    case "Battery Report":
      batteryReport(eventDataMap["battery_ok"], eventDataMap["battery_mV"])
      break
    default:
      log.warn "Unknown incoming event: $eventName"
      break
  }
  processMetadata(eventDataMap)
}

def processMetadata(eventDataMap) {
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
}

def pushButton() {
	sendEvent(name: "pushed", value: 1, descriptionText: "$device.displayName button was pushed", isStateChange: true)
}

def batteryReport(batteryOk, batteryMilliVolts) {
  state.batteryOk = batteryOk
  state.batteryMilliVolts = batteryMilliVolts
  if (batteryOk < 1)
    sendEvent(name: "battery", value: 1, unit: "%", descriptionText: "$device.displayName battery is low ($batteryMilliVolts mV)", type: "physical", isStateChange: true)
  else
    sendEvent(name: "battery", value: 100, unit: "%", descriptionText: "$device.displayName battery is OK ($batteryMilliVolts mV)", type: "physical", isStateChange: true)
}

def leakDetected() {
  unschedule(leakTimeout)
  sendEvent(name: "water", value: "wet", descriptionText: "$device.displayName is wet", type: "physical")
  runIn(30, leakTimeout)
}

def leakTimeout() {
  sendEvent(name: "water", value: "dry", descriptionText: "$device.displayName is dry", type: "physical")
}
