/*
    Roku YouTube Randomizer
    Copyright Arktronic
    ISC License
*/

metadata {
    definition (name: "RokuYouTubeRandomizer", namespace: "ark", author: "ark", importUrl: "https://raw.githubusercontent.com/arktronic/hubitat/main/drivers/roku-youtube-rnd.groovy") {
        capability "Switch"
	}

    preferences {
        input(name: "rokuAddress", type: "string", title:"Roku IP Address", description: "The IP address of your Roku device -- it should be a static IP", required: true, displayDuringSetup: true)
        input(name: "youtubeVideoSource", type: "string", title:"YouTube Video Source", description: "Location of the JSON file with YouTube videos to choose from", defaultValue: "https://raw.githubusercontent.com/arktronic/hubitat/main/data/youtube-videos.json", required: true, displayDuringSetup: true)
        input(name: "isDebugEnabled", type: "bool", title: "Enable debug logging?", defaultValue: false, required: false)
    }
}

def on() {
    sendEvent(name: "switch", value: "on", isStateChange: true)
    runIn(1, off)
    def params = [
        uri: youtubeVideoSource,
        requestContentType: "application/json",
        contentType: "application/json"
    ]
    httpGet(params) { response ->
        def rng = new java.util.Random()
        logDebug("Found list of videos: ${response.data}")
        def video = response.data[rng.nextInt(response.data.size())]
        logDebug("Selected video: ${video}")
        def target = "http://${rokuAddress}:8060/launch/837?contentId=${video}"
        logDebug("Sending POST to Roku: ${target}")
        httpPost([uri: target, requestContentType: "application/x-www-form-urlencoded"]) {}
    }
}

def off() {
    sendEvent(name: "switch", value: "off", isStateChange: true)
}

private logDebug(msg) {
    if (isDebugEnabled == true) {
        if (msg instanceof List && msg.size() > 0) {
            msg = msg.join(", ");
        }
        log.debug "$msg"
    }
}
