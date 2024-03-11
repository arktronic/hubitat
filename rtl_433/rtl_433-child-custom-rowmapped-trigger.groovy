/*

This is an rtl_433 Child Device driver. It must be used in conjunction with the Parent Device driver.
Please see https://github.com/arktronic/hubitat/tree/main/rtl_433 for details.

License: ISC

v0.1 - Initial release

*/

import java.time.LocalDateTime

metadata {
  definition(name: "rtl_433 Child Device Custom Row Mapped Trigger", namespace: "arktronic", author: "Sasha Kotlyar") {
    capability "Battery"
    capability "MotionSensor"
    capability "PushableButton"
    capability "SignalStrength"
    capability "Initialize"
  }

  preferences {
    input name: "rowFieldName", type: "string", title: "<b>Row Field Name</b>", description: "The name of the field inside a row that will be matched", required: true, defaultValue: "data"
    input name: "rowFieldValue", type: "string", title: "<b>Row Field Value</b>", description: "The value of the field that will be matched", required: true, defaultValue: "1"
    input name: "debounceTime", type: "number", title: "<b>Debounce (milliseconds)</b>", description: "Time period during which duplicate signals are ignored", required: true, defaultValue: 500, range: "0..60000"
    input name: "motionTime", type: "number", title: "<b>Motion Trigger Time (seconds)</b>", description: "How long to keep motion triggered after the last message was received", required: true, defaultValue: 30, range: "0..600"
  }
}

def installed() {
  configure()
}

def updated() {
  configure()
}

def initialize() {
  configure()
}

def configure() {
  state.clear()
  sendEvent(name: "motion", value: "inactive")
}

def processIncomingEvent(eventName, eventDataMap)
{
  def matched = false;
  if (eventDataMap["rows"] != null) {
    def dataRows = eventDataMap["rows"];
    for (row in dataRows) {
      if (row[rowFieldName] != null && row[rowFieldName].toString() == rowFieldValue.toString()) {
        matched = true;
        break;
      }
    }
  }
  
  if (!matched) return;

  if (!state.debouncing) {
    state.debouncing = true
    sendButtonEvent("pushed", 1, "physical")
    triggerMotion()
    runInMillis(debounceTime.toInteger(), finishDebounce)

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
    if (eventDataMap["battery_ok"] != null) {
      batteryReport(eventDataMap["battery_ok"])
    }
  }
}

def batteryReport(batteryOk) {
  state.batteryOk = batteryOk
  if (batteryOk < 1)
    sendEvent(name: "battery", value: 1, unit: "%", descriptionText: "$device.displayName battery is low", type: "physical", isStateChange: true)
  else
    sendEvent(name: "battery", value: 100, unit: "%", descriptionText: "$device.displayName battery is OK", type: "physical", isStateChange: true)
}

def push(button) {
  sendButtonEvent("pushed", button, "digital")
}

void sendButtonEvent(action, button, type) {
  String descriptionText = "${device.displayName} button ${button} was ${action} [${type}]"
  sendEvent(name: action, value: button, descriptionText: descriptionText, isStateChange: true, type: type)
}

def finishDebounce() {
  state.debouncing = false
}

def triggerMotion() {
  unschedule(deactivateMotion)
  sendEvent(name: "motion", value: "active", descriptionText: "$device.displayName motion is active", type: "physical")
  runIn(motionTime.toInteger(), deactivateMotion)
}

def deactivateMotion() {
  sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion is inactive", type: "physical")
}
