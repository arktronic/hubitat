/*

This is the rtl_433 Parent Device driver. It must be used in conjunction with one or more Child Device drivers.
Please see https://github.com/arktronic/hubitat/tree/main/rtl_433 for details.

Create a Hubitat Elevation device with this driver. Child Devices will be created by this driver afterward.

License: ISC

v0.1 - Initial release
v0.2 - Rework DNI and classification of devices

*/

import java.time.LocalDateTime
import groovy.transform.Field

@Field static DEVICES_BY_ID = [
  "Govee Water Leak Detector H5054 [Govee-Water]": "Govee-Water",
  "Rubicson Temperature Sensor [Rubicson-Temperature]": "Rubicson-Temperature",
  "Generic Trigger": "__rtl433_generic_trigger__"
]

@Field static DEVICES_BY_ID_AND_CHANNEL = [
  "Acurite Temperature Humidity Sensor [Acurite-Tower]": "Acurite-Tower"
]

@Field static ALL_DEVICES_TO_DRIVER_NAMES = [
  "Govee Water Leak Detector H5054 [Govee-Water]": "rtl_433 Child Device Govee Water Leak Detector H5054",
  "Rubicson Temperature Sensor [Rubicson-Temperature]": "rtl_433 Child Device Rubicson Temperature Sensor",
  "Generic Trigger": "rtl_433 Child Device Generic Trigger",
  "Acurite Temperature Humidity Sensor [Acurite-Tower]": "rtl_433 Child Device Acurite Temperature Humidity Sensor"
]

metadata {
  definition(name: "rtl_433 Parent Device", namespace: "arktronic", author: "Sasha Kotlyar") {
    capability "PresenceSensor"

    command "addNewDeviceById", [
      [name:"id *", type: "STRING", description: "Unique identifier", required: true],
      [name:"label *", type: "STRING", description: "Friendly name for this device", required: true],
      [name:"Device type *", type: "ENUM", description: "Choose one", constraints: DEVICES_BY_ID.keySet(), required: true]
    ]

    command "addNewDeviceByIdAndChannel", [
      [name:"id *", type: "STRING", description: "Unique identifier", required: true],
      [name:"channel *", type: "STRING", description: "Channel", required: true],
      [name:"label *", type: "STRING", description: "Friendly name for this device", required: true],
      [name:"Device type *", type: "ENUM", description: "Choose one", constraints: DEVICES_BY_ID_AND_CHANNEL.keySet(), required: true]
    ]
  }

  preferences {
    input name: "rtl433IpAddress", type: "string", title:"<b>rtl_433 IP Address</b>", description: "WARNING: if this ever changes, all child devices must be recreated (or their DNIs changed)!", required: true
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
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

  def dni = settings.rtl433IpAddress.tokenize(".").collect { String.format("%02x", it.toInteger()) }.join().toUpperCase()
  if (device.deviceNetworkId != "$dni") {
    device.deviceNetworkId = "$dni"
    if (logEnable) log.debug "Device Network Id set to ${device.deviceNetworkId}"
  }
  state.hubTarget = "http://${location.hub.localIP}:39501"
}

def deriveChildDNI(String id, String deviceType) {
  return "${device.deviceNetworkId}-type_${deviceType}-id_${id}"
}

def deriveChildDNI(String id, String channel, String deviceType) {
  return "${device.deviceNetworkId}-type_${deviceType}-id_${id}-channel_${channel}"
}

def addNewDeviceById(String id, String label, String deviceType) {
  dniTypeCode = DEVICES_BY_ID[deviceType]
  driverName = ALL_DEVICES_TO_DRIVER_NAMES[deviceType]
  addChildDevice("arktronic", driverName, deriveChildDNI(id, dniTypeCode), [label: label])
}

def addNewDeviceByIdAndChannel(String id, String channel, String label, String deviceType) {
  dniTypeCode = DEVICES_BY_ID_AND_CHANNEL[deviceType]
  driverName = ALL_DEVICES_TO_DRIVER_NAMES[deviceType]
  addChildDevice("arktronic", driverName, deriveChildDNI(id, channel, dniTypeCode), [label: label])
}

def parse(String description) {
  stillAlive()
  def msg = parseLanMessage(description)
  if (logEnable) log.debug "Message received: ${msg.body}"

  def json = parseJson(msg.body)
  def jsonModel = (json["model"] ? json["model"].toString() : "")
  def jsonId = (json["id"] ? json["id"].toString() : "")
  def jsonChannel = (json["channel"] ? json["channel"].toString() : "")

  if (json["heartbeat_rtl_433"] != null) {
    // This is a heartbeat message from the rtl_433 host
    state.lastReceivedHeartbeat = json["heartbeat_rtl_433"]
    state.lastReceivedHeartbeatDate = new Date((json["heartbeat_rtl_433"] as long) * 1000).format("EEE, d MMM yyyy HH:mm:ss Z")
  } else if (jsonId && jsonModel && DEVICES_BY_ID.values().any { el -> el == jsonModel }) {
    def childDevice = getChildDevice(deriveChildDNI(jsonId, jsonModel))
    if (childDevice != null) {
      if (logEnable) log.debug "Sending message to child device: ${childDevice.getLabel()}"
      childDevice.processIncomingEvent(json["event"], json)
    } else {
      if (logEnable) log.debug "No child device found for ${jsonModel} ${jsonId}"
    }
  } else if (jsonId && jsonModel && jsonChannel && DEVICES_BY_ID_AND_CHANNEL.values().any { el -> el == jsonModel }) {
    def childDevice = getChildDevice(deriveChildDNI(jsonId, jsonChannel, jsonModel))
    if (childDevice != null) {
      if (logEnable) log.debug "Sending message to child device: ${childDevice.getLabel()}"
      childDevice.processIncomingEvent(json["event"], json)
    } else {
      if (logEnable) log.debug "No child device found for ${jsonModel} ${jsonId} ${jsonChannel}"
    }
  } else if (jsonId) {
    def childDevice = getChildDevice(deriveChildDNI(jsonId, "__rtl433_generic_trigger__"))
    if (childDevice != null) {
      if (logEnable) log.debug "Sending message to child device: ${childDevice.getLabel()}"
      childDevice.processIncomingEvent(json["event"], json)
    }
  }
}

def stillAlive() {
  unschedule(theCakeIsALie)
  sendEvent(name: "presence", value: "present")
  runIn(2 * 60 * 60, theCakeIsALie)
}

def theCakeIsALie() {
  sendEvent(name: "presence", value: "not present")
}
