/*

This is the rtl_433 Parent Device driver. It must be used in conjunction with one or more Child Device drivers.
Please see https://github.com/arktronic/hubitat/tree/main/rtl_433 for details.

Create a Hubitat Elevation device with this driver. Child Devices will be created by this driver afterward.

License: ISC

v0.1 - Initial release

*/

import java.time.LocalDateTime

metadata {
  definition(name: "rtl_433 Parent Device", namespace: "arktronic", author: "Sasha Kotlyar") {
    capability "PresenceSensor"

    command "addNewDeviceById", [
      [name:"id *", type: "STRING", description: "Unique identifier", required: true],
      [name:"label *", type: "STRING", description: "Friendly name for this device", required: true],
      [name:"Device type *", type: "ENUM", description: "Choose one", constraints: ["Govee Water Leak Detector H5054", "Rubicson Temperature Sensor", "Generic Trigger"], required: true]
    ]

    command "addNewDeviceByIdAndChannel", [
      [name:"id *", type: "STRING", description: "Unique identifier", required: true],
      [name:"channel *", type: "STRING", description: "Channel", required: true],
      [name:"label *", type: "STRING", description: "Friendly name for this device", required: true],
      [name:"Device type *", type: "ENUM", description: "Choose one", constraints: ["Acurite Temperature Humidity Sensor"], required: true]
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
  addChildDevice("arktronic", "rtl_433 Child Device ${deviceType}", deriveChildDNI(id, deviceType), [label: label])
}

def addNewDeviceByIdAndChannel(String id, String channel, String label, String deviceType) {
  addChildDevice("arktronic", "rtl_433 Child Device ${deviceType}", deriveChildDNI(id, channel, deviceType), [label: label])
}

def parse(String description) {
  stillAlive()
  def msg = parseLanMessage(description)
  if (logEnable) log.debug "Message received: ${msg.body}"

  def json = parseJson(msg.body)
  def jsonId = (json["id"] ? json["id"].toString() : "")

  if (json["heartbeat_rtl_433"] != null) {
    // This is a heartbeat message from the rtl_433 host
    state.lastReceivedHeartbeat = json["heartbeat_rtl_433"]
    state.lastReceivedHeartbeatDate = new Date((json["heartbeat_rtl_433"] as long) * 1000).format("EEE, d MMM yyyy HH:mm:ss Z")
  } else if (json["model"] == "Govee-Water" && jsonId) {
    def childDevice = getChildDevice(deriveChildDNI(jsonId, "Govee Water Leak Detector H5054"))
    if (childDevice != null) {
      if (logEnable) log.debug "Sending message to child device: ${childDevice.getLabel()}"
      childDevice.processIncomingEvent(json["event"], json)
    } else {
      if (logEnable) log.debug "No child device found for ${json["model"]} ${jsonId}"
    }
  } else if (json["model"] == "Rubicson-Temperature" && jsonId) {
    def childDevice = getChildDevice(deriveChildDNI(jsonId, "Rubicson Temperature Sensor"))
    if (childDevice != null) {
      if (logEnable) log.debug "Sending message to child device: ${childDevice.getLabel()}"
      childDevice.processIncomingEvent(json["event"], json)
    } else {
      if (logEnable) log.debug "No child device found for ${json["model"]} ${jsonId}"
    }
  } else if (json["model"] == "Acurite-Tower" && jsonId && json["channel"].toString()) {
    def childDevice = getChildDevice(deriveChildDNI(jsonId, json["channel"].toString(), "Acurite Temperature Humidity Sensor"))
    if (childDevice != null) {
      if (logEnable) log.debug "Sending message to child device: ${childDevice.getLabel()}"
      childDevice.processIncomingEvent(json["event"], json)
    } else {
      if (logEnable) log.debug "No child device found for ${json["model"]} ${jsonId} ${json["channel"]}"
    }
  } else if (jsonId) {
    def childDevice = getChildDevice(deriveChildDNI(jsonId, "Generic Trigger"))
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
