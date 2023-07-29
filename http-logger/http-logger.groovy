/*****************************************************************************************************************
 *  Source: https://github.com/arktronic/hubitat/http-logger
 *
 *  Raw Source: https://raw.githubusercontent.com/arktronic/hubitat/http-logger/main/http-logger.groovy
 *
 *  Forked from: https://github.com/HubitatCommunity/InfluxDB-Logger/blob/master/influxdb-logger.groovy
 *  Original Author: David Lomas (codersaur)
 *  Previous Author: Joshua Marker (tooluser)
 *  InfluxDB Hubitat Elevation version maintained by HubitatCommunity (https://github.com/HubitatCommunity/InfluxDB-Logger)
 *
 *  License:
 *   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *   for the specific language governing permissions and limitations under the License.
 *
 *****************************************************************************************************************/


definition(
    name: "HTTP Logger",
    namespace: "arktronic",
    author: "Sasha Kotlyar",
    description: "Log device states to an HTTP endpoint as JSON data",
    category: "Utility",
    importUrl: "https://raw.githubusercontent.com/arktronic/hubitat/http-logger/main/http-logger.groovy",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleThreaded: true
)

import groovy.transform.Field

// Device type list
@Field static final Map<String,Map> deviceTypeMap = [
    'accelerometers': [ title: 'Accelerometers', capability: 'accelerationSensor', attributes: ['acceleration'] ],
    'alarms': [ title: 'Alarms', capability: 'alarm', attributes: ['alarm'] ],
    'batteries': [ title: 'Batteries', capability: 'battery', attributes: ['battery'] ],
    'beacons': [ title: 'Beacons', capability: 'beacon', attributes: ['presence'] ],
    'buttons': [ title: 'Buttons', capability: 'pushableButton', attributes: ['pushed', 'doubleTapped', 'held', 'released'] ],
    'cos': [ title: 'Carbon Monoxide Detectors', capability: 'carbonMonoxideDetector', attributes: ['carbonMonoxide'] ],
    'co2s': [ title: 'Carbon Dioxide Detectors', capability: 'carbonDioxideMeasurement', attributes: ['carbonDioxide'] ],
    'colors': [ title: 'Color Controllers', capability: 'colorControl', attributes: ['hue', 'saturation', 'color'] ],
    'consumables': [ title: 'Consumables', capability: 'consumable', attributes: ['consumableStatus'] ],
    'contacts': [ title: 'Contact Sensors', capability: 'contactSensor', attributes: ['contact'] ],
    'currentMeters': [ title: 'Current Meters', capability: 'currentMeter', attributes: ['amperage'] ],
    'doorsControllers': [ title: 'Door Controllers', capability: 'doorControl', attributes: ['door'] ],
    'energyMeters': [ title: 'Energy Meters', capability: 'energyMeter', attributes: ['energy'] ],
    'filters': [ title: 'Filters', capability: 'filterStatus', attributes: ['filterStatus'] ],
    'gasDetectors': [ title: 'Gas Detectors', capability: 'gasDetector', attributes: ['naturalGas'] ],
    'humidities': [ title: 'Humidity Meters', capability: 'relativeHumidityMeasurement', attributes: ['humidity'] ],
    'illuminances': [ title: 'Illuminance Meters', capability: 'illuminanceMeasurement', attributes: ['illuminance'] ],
    'liquidFlowMeters': [ title: 'Liquid Flow Meters', capability: 'liquidFlowRate', attributes: ['rate'] ],
    'locks': [ title: 'Locks', capability: 'lock', attributes: ['lock'] ],
    'motions': [ title: 'Motion Sensors', capability: 'motionSensor', attributes: ['motion'] ],
    'musicPlayers': [ title: 'Music Players', capability: 'musicPlayer', attributes: ['status', 'level', 'trackDescription', 'trackData', 'mute'] ],
    'peds': [ title: 'Pedometers', capability: 'stepSensor', attributes: ['steps', 'goal'] ],
    'phMeters': [ title: 'pH Meters', capability: 'pHMeasurement', attributes: ['pH'] ],
    'powerMeters': [ title: 'Power Meters', capability: 'powerMeter', attributes: ['power'] ],
    'powerSources': [ title: 'Power Sources', capability: 'powerSources', attributes: ['powerSource'] ],
    'presences': [ title: 'Presence Sensors', capability: 'presenceSensor', attributes: ['presence'] ],
    'pressures': [ title: 'Pressure Sensors', capability: 'pressureMeasurement', attributes: ['pressure'] ],
    'shockSensors': [ title: 'Shock Sensors', capability: 'shockSensor', attributes: ['shock'] ],
    'signalStrengthMeters': [ title: 'Signal Strength Meters', capability: 'signalStrength', attributes: ['lqi', 'rssi'] ],
    'sleepSensors': [ title: 'Sleep Sensors', capability: 'sleepSensor', attributes: ['sleeping'] ],
    'smokeDetectors': [ title: 'Smoke Detectors', capability: 'smokeDetector', attributes: ['smoke'] ],
    'soundSensors': [ title: 'Sound Sensors', capability: 'soundSensor', attributes: ['sound'] ],
    'spls': [ title: 'Sound Pressure Level Sensors', capability: 'soundPressureLevel', attributes: ['soundPressureLevel'] ],
    'switches': [ title: 'Switches', capability: 'switch', attributes: ['switch'] ],
    'switchLevels': [ title: 'Switch Levels', capability: 'switchLevel', attributes: ['level'] ],
    'tamperAlerts': [ title: 'Tamper Alerts', capability: 'tamperAlert', attributes: ['tamper'] ],
    'temperatures': [ title: 'Temperature Sensors', capability: 'temperatureMeasurement', attributes: ['temperature'] ],
    'thermostats': [ title: 'Thermostats', capability: 'thermostat', attributes: ['temperature', 'heatingSetpoint', 'coolingSetpoint', 'thermostatSetpoint', 'thermostatMode', 'thermostatFanMode', 'thermostatOperatingState', 'thermostatSetpointMode', 'scheduledSetpoint'] ],
    'threeAxis': [ title: 'Three-axis (Orientation) Sensors', capability: 'threeAxis', attributes: ['threeAxis'] ],
    'touchs': [ title: 'Touch Sensors', capability: 'touchSensor', attributes: ['touch'] ],
    'uvs': [ title: 'UV Sensors', capability: 'ultravioletIndex', attributes: ['ultravioletIndex'] ],
    'valves': [ title: 'Valves', capability: 'valve', attributes: ['valve'] ],
    'volts': [ title: 'Voltage Meters', capability: 'voltageMeasurement', attributes: ['voltage'] ],
    'waterSensors': [ title: 'Water Sensors', capability: 'waterSensor', attributes: ['water'] ],
    'windowShades': [ title: 'Window Shades', capability: 'windowShade', attributes: ['windowShade'] ]
]

// Momentary attributes that should be ignored for keep alive
@Field static final List<String> momentaryAttributes = [
    'pushed', 'doubleTapped', 'held', 'released'
]

@Field static final Map<Integer,String> logOptions = [
    0 : "None",
    1 : "Error",
    2 : "Warning",
    3 : "Info",
    4 : "Debug"
]
@Field static final Integer logNone  = 0
@Field static final Integer logError = 1
@Field static final Integer logWarn  = 2
@Field static final Integer logInfo  = 3
@Field static final Integer logDebug = 4

preferences {
    page(name: "setupMain")
    page(name: "connectionPage")
}

def setupMain() {
    dynamicPage(name: "setupMain", title: "<h2>HTTP Logger</h2>", install: true, uninstall: true) {
        section("<h3>\nGeneral Settings:</h3>") {
            input "appName", "text", title: "Application Name", multiple: false, required: true, submitOnChange: true, defaultValue: app.getLabel()

            input(
                name: "configLoggingLevelIDE",
                title: "System log level - messages with this level and higher will be sent to the system log",
                type: "enum",
                options: logOptions,
                defaultValue: "${logWarn}",
                required: false
            )
        }

        section("\n<h3>Logger Settings:</h3>") {
            href(
                name: "href",
                title: "HTTP connection",
                description : prefHttpEndpoint == null ? "Configure HTTP connection parameters" : uriString(),
                required: true,
                page: "connectionPage"
            )
            input(
                name: "prefBatchTimeLimit",
                title: "Batch time limit - maximum number of seconds before writing a batch to HTTP (range 1-300)",
                type: "number",
                range: "1..300",
                defaultValue: "60",
                required: true
            )
            input(
                name: "prefBatchSizeLimit",
                title: "Batch size limit - maximum number of events in a batch to HTTP (range 1-250)",
                type: "number",
                range: "1..250",
                defaultValue: "50",
                required: true
            )
            input(
                name: "prefBacklogLimit",
                title: "Backlog size limit - maximum number of queued events before dropping failed posts (range 1-10000)",
                type: "number",
                range: "1..10000",
                defaultValue: "5000",
                required: true
            )
        }

        section("\n<h3>Event Handling:</h3>") {
            input(
                name: "prefSoftPollingInterval",
                title: "Post keep alive events (aka softpoll) - check every softpoll interval and re-post last value if a new event has not occurred in this time",
                type: "enum",
                options: [
                    "0" : "disabled",
                    "1" : "1 minute (not recommended)",
                    "5" : "5 minutes",
                    "10" : "10 minutes",
                    "15" : "15 minutes",
                    "30" : "30 minutes",
                    "60" : "60 minutes",
                    "180" : "3 hours"
                ],
                defaultValue: "15",
                submitOnChange: true,
                required: true
            )
            if (prefSoftPollingInterval?.toInteger()) {
                input "prefPostHubInfo", "bool", title:"Post Hub information (IP, firmware, uptime, mode, sunrise/sunset)", defaultValue: false
            }
            input "filterEvents", "bool", title:"Only post device events when the data value changes", defaultValue: true
        }

        section("Devices To Monitor:", hideable:true, hidden:false) {
            input "accessAllAttributes", "bool", title:"Advanced attribute seletion?", defaultValue: false, submitOnChange: true

            if (accessAllAttributes) {
                input name: "allDevices", type: "capability.*", title: "Selected Devices", multiple: true, required: false, submitOnChange: true

                settings.allDevices.each { device ->
                    deviceId = device.getId()
                    attrList = device.getSupportedAttributes().unique()
                    if (attrList) {
                        options = []
                        attrList.each { attr ->
                            options.add("${attr}")
                        }
                        input name:"attrForDev${deviceId}", type: "enum", title: "$device", options: options.sort(), multiple: true, required: false, submitOnChange: true
                    }
                }
            }
            else {
                deviceTypeMap.each { name, entry ->
                    input "${name}", "capability.${entry.capability}", title: "${entry.title}", multiple: true, required: false
                }
            }
        }
    }
}

def connectionPage() {
    dynamicPage(name: "connectionPage", title: "Connection Properties", install: false, uninstall: false) {
        section {
            input "prefHttpEndpoint", "text", title: "HTTP endpoint (e.g., https://192.168.1.23/events)", defaultValue: "", required: true
            input "prefIgnoreSSLIssues", "bool", title: "Ignore TLS certificate validation issues (if HTTPS)", defaultValue: false, required: true

            input(
                name: "prefAuthType",
                title: "Authorization Type",
                type: "enum",
                options: [
                    "none" : "None",
                    "basic" : "Username / Password",
                    "token" : "Token"
                ],
                defaultValue: "none",
                submitOnChange: true
            )
            if (prefAuthType == "basic") {
                input "prefAuthUser", "text", title: "Username", defaultValue: "", required: true
                input "prefAuthPass", "text", title: "Password", defaultValue: "", required: true
            }
            else if (prefAuthType == "token") {
                input "prefAuthToken", "text", title: "Token", required: true
            }
        }
    }
}

/**
 *  installed()
 *
 *  Runs when the app is first installed.
 **/
void installed() {
    state.installedAt = now()
    state.loggerQueue = []
    updated()
    log.info "${app.label}: Installed"
}

/**
 *  uninstalled()
 *
 *  Runs when the app is uninstalled.
 **/
void uninstalled() {
    log.info "${app.label}: Uninstalled"
}

/**
 *  updated()
 *
 *  Runs when app settings are changed.
 **/
void updated() {
    // Update application name
    app.updateLabel(settings.appName)
    logger("${app.label}: Updated", logInfo)

    // Endpoint config:
    setUpEndpointConnectionInfo()

    // Clear out any prior subscriptions
    unsubscribe()

    // Create device subscriptions
    Map<String,List> deviceAttrMap = getDeviceAttrMap()
    deviceAttrMap.each { device, attrList ->
        attrList.each { attr ->
            logger("Subscribing to ${device}: ${attr}", logInfo)
            subscribe(device, attr, handleEvent, ["filterEvents": filterEvents])
        }
    }

    // Subscribe to system start
    subscribe(location, "systemStart", hubRestartHandler)

    // Subscribe to mode events if requested
    if (prefPostHubInfo) {
        subscribe(location, "mode", handleModeEvent)
    }

    // Clear out any prior schedules
    unschedule()

    // Set up softpoll if requested
    state.softPollingInterval = settings.prefSoftPollingInterval.toInteger()
    switch (state.softPollingInterval) {
        case 1:
            runEvery1Minute(softPoll)
            break
        case 5:
            runEvery5Minutes(softPoll)
            break
        case 10:
            runEvery10Minutes(softPoll)
            break
        case 15:
            runEvery15Minutes(softPoll)
            break
        case 30:
            runEvery30Minutes(softPoll)
            break
        case 60:
            runEvery1Hour(softPoll)
            break
        case 180:
            runEvery3Hours(softPoll)
            break
    }

    // Flush any pending batch
    runIn(1, writeQueuedDataToHttpEndpoint)
}

/**
 *  getDeviceAttrMap()
 *
 *  Build a device attribute map.
 *
 * If using attribute selection, a device will appear only once in the array, with one or more attributes.
 * If using capability selection, a device may appear multiple times in the array, each time with a single attribue.
 **/
private Map<String,List> getDeviceAttrMap() {
    deviceAttrMap = [:]

    if (settings.accessAllAttributes) {
        settings.allDevices.each { device ->
            deviceId = device.getId()
            deviceAttrMap[device] = settings["attrForDev${deviceId}"]
        }
    }
    else {
        deviceTypeMap.each { name, entry ->
            deviceList = settings."${name}"
            if (deviceList) {
                deviceList.each { device ->
                    deviceAttrMap[device] = entry.attributes
                }
            }
        }
    }

    return deviceAttrMap
}

/**
 * hubRestartHandler()
 *
 * Handle hub restarts.
**/
void hubRestartHandler(evt) {
    if (prefPostHubInfo) {
        handleModeEvent(null)
    }

    if (state.loggerQueue?.size()) {
        runIn(60, writeQueuedDataToHttpEndpoint)
    }
}

/**
 *  createDeviceEvent(evt, isSoftPoll, extra)
 *
 *  Builds data to send to the HTTP endpoint.
 **/
private Map createDeviceEvent(evt, isSoftPoll, extra) {
    def map = [
        deviceId: evt.deviceId,
        deviceName: evt.displayName,
        event: evt.name,
        value: evt.value.toString(),
        unit: evt.unit.toString(),
        timestamp: evt.unixTime,
        hub: location.name,
        polled: isSoftPoll,
        extra: extra
    ]
    return map
}

/**
 *  handleEvent(evt)
 *
 *  Enqueues live data to be sent to the HTTP endpoint.
 **/
void handleEvent(evt) {
    logger("Handle Event: ${evt}", logDebug)

    // Create the event
    data = createDeviceEvent(evt, false, null)

    // Add event to the queue
    enqueueEvents([data])
}

/**
 *  createHubInfoEvent(evt, isSoftPoll)
 *
 *  Build a Hub Information record with an optional mode event.
 **/
private Map createHubInfoEvent(evt, isSoftPoll) {
    def times = getSunriseAndSunset()
    String sunriseTime = times.sunrise.format("HH:mm", location.timeZone)
    String sunsetTime = times.sunset.format("HH:mm", location.timeZone)

    hubData = [
        localIP: location.hub.localIP,
        firmware: location.hub.firmwareVersionString,
        uptimeSeconds: location.hub.uptime,
        sunrise: sunriseTime,
        sunset: sunsetTime
    ]

    event = createDeviceEvent([
        name: (evt ? "_hubModeChange" : "_hubInfo"),
        value: (evt?.value ? evt.value : location.getMode()),
        unit: "",
        deviceId: -1,
        displayName: "_hubInfo",
        unixTime: (evt?.unixTime ? evt.unixTime : now())
    ], isSoftPoll, groovy.json.JsonOutput.toJson(hubData))

    return event
}

/**
 *  handleModeEvent(evt)
 *
 *  Log hub information when mode changes.
 **/
void handleModeEvent(evt) {
    logger("Handle Mode Event: ${evt}", logDebug)

    // Encode the event
    data = createHubInfoEvent(evt, false)

    // Add event to the queue
    enqueueEvents([data])
}

/**
 *  softPoll()
 *
 *  Re-queues last value unless an event has already been seen in the last softPollingInterval.
 **/
void softPoll() {
    logger("Keepalive check", logDebug)

    // Get the map
    Map<String,List> deviceAttrMap = getDeviceAttrMap()

    // Create the list
    Long timeNow = now()
    List<Map> eventList = []
    deviceAttrMap.each { device, attrList ->
        attrList.each { attr ->
            if (momentaryAttributes.contains(attr)) {
                logger("Keep alive for device ${device}(${attr}) suppressed - momentary attribute", logDebug)
                return
            }
            if (device.latestState(attr)) {
                Integer activityMinutes = (timeNow - device.latestState(attr).date.time) / 60000
                if (activityMinutes > state.softPollingInterval) {
                    logger("Keep alive for device ${device}(${attr})", logDebug)
                    event = createDeviceEvent([
                        name: attr,
                        value: device.latestState(attr).value,
                        unit: device.latestState(attr).unit,
                        device: device,
                        deviceId: device.id,
                        displayName: device.displayName,
                        unixTime: timeNow
                    ], true, null)
                    eventList.add(event)
                }
                else {
                    logger("Keep alive for device ${device}(${attr}) unnecessary - last activity ${activityMinutes} minutes", logDebug)
                }
            }
            else {
                logger("Keep alive for device ${device}(${attr}) suppressed - last activity never", logDebug)
            }
        }
    }

    // Add a hub information record if requested
    if (settings.prefPostHubInfo) {
        eventList.add(createHubInfoEvent(null, true))
    }

    // Queue the events
    enqueueEvents(eventList)
}

/**
 *  enqueueEvents()
 *
 *  Adds events to the queue.
 **/
private void enqueueEvents(List<Map> eventList) {
    if (state.loggerQueue == null) {
        // Failsafe if coming from an old version
        state.loggerQueue = []
    }

    // Add the data to the queue
    priorLoggerQueueSize = state.loggerQueue.size()
    state.loggerQueue += eventList
    eventList.each { event ->
        logger("Queued event: ${event}", logInfo)
    }

    // If this is the first data in the batch, trigger the timer
    if (priorLoggerQueueSize == 0) {
        logger("Scheduling batch", logDebug)
        runIn(settings.prefBatchTimeLimit, writeQueuedDataToHttpEndpoint)
    }
}

/**
 *  writeQueuedDataToHttpEndpoint()
 *
 *  Posts data to the configured HTTP endpoint.
**/
void writeQueuedDataToHttpEndpoint() {
    if (state.loggerQueue == null) {
        // Failsafe if coming from an old version
        return
    }
    if (state.uri == null) {
        // Failsafe if using an old config
        setUpEndpointConnectionInfo()
    }

    Integer loggerQueueSize = state.loggerQueue.size()
    logger("Number of queued events: ${loggerQueueSize}", logDebug)
    if (loggerQueueSize == 0) {
        return
    }

    Integer postCount = state.postCount ?: 0
    Long timeNow = now()
    if (postCount) {
        // A post is already running
        Long elapsed = timeNow - state.lastPost
        logger("Post of ${postCount} events already running (elapsed ${elapsed}ms)", logDebug)
        if (elapsed < 90000) {
            // Come back later
            runIn(30, writeQueuedDataToHttpEndpoint)
            return
        }

        // Failsafe in case handleHttpResponse doesn't get called for some reason such as reboot
        logger("Post callback failsafe timeout", logDebug)
        state.postCount = 0

        if (loggerQueueSize > settings.prefBacklogLimit) {
            logger("Backlog of ${state.loggerQueue.size()} events exceeds limit of ${settings.prefBacklogLimit}: dropping ${postCount} events (failsafe)", logError)
            state.loggerQueue = state.loggerQueue.drop(postCount)
            loggerQueueSize -= postCount
        }
    }

    // If we have a backlog, log a warning
    if (loggerQueueSize > settings.prefBacklogLimit) {
        logger("Backlog of ${loggerQueueSize} queued events", logWarn)
    }

    postCount = loggerQueueSize < settings.prefBatchSizeLimit ? loggerQueueSize : settings.prefBatchSizeLimit
    state.postCount = postCount
    state.lastPost = timeNow

    String data = groovy.json.JsonOutput.toJson(state.loggerQueue.subList(0, postCount).toArray())
    // Uncommenting the following line will eventually drive your hub into the ground. Don't do it.
    // logger("Posting data to HTTP endpoint: ${state.uri}, Data: [${data}]", logDebug)

    // Post it
    def postParams = [
        uri: state.uri,
        requestContentType: 'application/json',
        contentType: 'application/json',
        headers: state.headers,
        ignoreSSLIssues: settings.prefIgnoreSSLIssues,
        timeout: 60,
        body: data
    ]
    asynchttpPost('handleHttpResponse', postParams, [ postTime: timeNow ])
}

/**
 *  handleHttpResponse()
 *
 *  Handles response from post made in writeQueuedDataToHttpEndpoint().
 **/
void handleHttpResponse(hubResponse, closure) {
    if (state.loggerQueue == null) {
        // Failsafe if coming from an old version
        return
    }

    Double elapsed = (closure) ? (now() - closure.postTime) / 1000 : 0
    Integer postCount = state.postCount ?: 0
    state.postCount = 0

    if (hubResponse.status < 400) {
        logger("Post of ${postCount} events complete - elapsed time ${elapsed} seconds - Status: ${hubResponse.status}", logInfo)
    }
    else {
        logger("Post of ${postCount} events failed - elapsed time ${elapsed} seconds - Status: ${hubResponse.status}, Error: ${hubResponse.errorMessage}, Headers: ${hubResponse.headers}, Data: ${data}", logWarn)
        if (postCount == 1) {
            logger("Failed record was: ${state.loggerQueue[0]}", logError)
        }

        if (state.loggerQueue.size() <= settings.prefBacklogLimit) {
            if (state.loggerQueue.size() > postCount) {
                logger("Backlog of ${state.loggerQueue.size()} events", logWarn)
            }

            // Try again later
            runIn(60, writeQueuedDataToHttpEndpoint)
            return
        }

        logger("Backlog of ${state.loggerQueue.size()} events exceeds limit of ${settings.prefBacklogLimit}: dropping ${postCount} events", logError)
    }

    // Remove the post from the queue
    state.loggerQueue = state.loggerQueue.drop(postCount)

    // Go again?
    if (state.loggerQueue.size()) {
        runIn(1, writeQueuedDataToHttpEndpoint)
    }
}

/**
 *  uriString()
 *
 *  Return the uri string.
 **/
private String uriString() {
    return settings.prefHttpEndpoint
}

/**
 *  setUpEndpointConnectionInfo()
 *
 *  Set up the endpoint uri and header state variables.
 **/
private void setUpEndpointConnectionInfo() {
    def headers = [:]
    if (settings.prefAuthType == null || settings.prefAuthType == "basic") {
        if (settings.prefAuthUser && settings.prefAuthPass) {
            String userpass = "${settings.prefAuthUser}:${settings.prefAuthPass}"
            headers.put("Authorization", "Basic " + userpass.bytes.encodeBase64())
        }
    }
    else if (settings.prefAuthType == "token") {
        headers.put("Authorization", "Token ${settings.prefAuthToken}")
    }

    state.uri = uriString()
    state.headers = headers
}

/**
 *  logger()
 *
 *  Wrapper function for logging.
 **/
private void logger(String msg, Integer level = logDebug) {
    Integer loggingLevel = settings.configLoggingLevelIDE != null ? settings.configLoggingLevelIDE.toInteger() : logWarn
    if (level > loggingLevel) {
        return
    }

    switch (level) {
        case logError:
            log.error msg
            break
        case logWarn:
            log.warn msg
            break
        case logInfo:
            log.info msg
            break
        case logDebug:
            log.debug msg
            break
    }
}
