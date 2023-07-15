# rtl_433 Hubitat Elevation Drivers

## Overview

[rtl_433](https://github.com/merbanan/rtl_433) is an open source project used to receive signals from various wireless devices, mainly around the 433MHz frequency. This includes weather stations, TPMS sensors in tires, security devices, and many others.

The drivers here, in conjunction with a correctly configured rtl_433 instance, allow Hubitat Elevation hubs to instantaneously process signals from devices monitored by rtl_433.

## Setup

### Networking

rtl_433 must be running on the same network as the HE hub.

Both the hub and the rtl_433 device should have static IP addresses configured either internally or assigned via DHCP reservations. **Dynamic (changing) IPs will cause problems.**

In the example setup below, the Hubitat Elevation has the IP address `192.168.1.30` and the Raspberry Pi running rtl_433 has the IP address `192.168.1.45`.

### rtl_433

Hardware requirements for rtl_433 are beyond the scope of this readme; please see [the rtl_433 source repo](https://github.com/merbanan/rtl_433) for details.

For this example, let's assume that a Raspberry Pi is being used in conjunction with a USB RTL-SDR dongle.

To simplify things, the use of Docker is encouraged. Conveniently, there are existing [Docker images](https://github.com/hertzg/rtl_433_docker/) for rtl_433. Please follow the directions in this repo for determining which USB device to attach to the rtl_433 container.

### Raspberry Pi

Regardless of whether Docker is used, [supervisord](http://supervisord.org/) will serve as the process controller. Installing it is as simple as `sudo apt install supervisor`.

To keep rtl_433 running, create a supervisord configuration file, `/etc/supervisor/conf.d/rtl433.conf`:

```ini
[program:rtl433]
command=bash -c 'docker run --rm -q -a stdout --device /dev/bus/usb/001/003 hertzg/rtl_433:alpine-latest-master -Mbits -Mlevel -Mprotocol -Mtime:unix -F json | xargs -n1 -d"\n" curl http://192.168.1.30:39501 -d'
autorestart=true
startsecs=10
startretries=30
stdout_logfile=NONE
stderr_logfile=NONE
```

Note: the `command=` line above will need to be modified to have the correct USB device as well as the Hubitat Elevation's IP address. (The port is always 39501.)

Optionally, a keepalive or heartbeat can be installed as a cron job by adding to the user crontab, like so:

```
# m h  dom mon dow   command
*/15 * * * * curl http://192.168.1.30:39501 -d "{\"heartbeat_rtl_433\": $(date +\%s)}"
```

### Hubitat

The `rtl_433-parent.groovy` parent driver must be installed as a custom driver, along with one or more child drivers.

#### Finding devices

To find devices that can be added, the first step is to enter the Raspberry Pi's IP address and enable debug logging.
![image](https://github.com/arktronic/hubitat/assets/344911/79ce86b3-97d5-48a8-b490-edfd1412fc48)

Assuming everything is configured correctly and there are devices close by that are broadcasting, debug logs that look similar to the following will start appearing:

```
Message received: {"time" : "1689444701", "protocol" : 40, "model" : "Acurite-Tower", "id" : 10327, "channel" : "A", "battery_ok" : 1, "temperature_C" : 26.500, "humidity" : 72, "mic" : "CHECKSUM", "mod" : "ASK", "freq" : 433.932, "rssi" : -0.101, "snr" : 21.039, "noise" : -21.140}
```

Before the Acurite sensor can be added, its unique properties must be noted: the ID of 10327, and the channel A.

**IMPORTANT:** Once device information has been found, be sure to disable debug logging!

#### Adding a device

![image](https://github.com/arktronic/hubitat/assets/344911/82713bd2-e54f-4d3b-862e-6a619abc7d0a)

Enter the previously noted ID and channel in the appropriate boxes, in this case under "Add New Device By Id And Channel". Give the device a desriptive label. And then click the "Add New Device By Id And Channel" on top.

This will create a child device, which will receive updates from the Acurite sensor as soon as they are processed.

Note, if the Acurite child driver has not been installed, the above procedure will not work. Ensure that the necessary drivers are present.
