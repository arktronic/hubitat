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

_to be continued_
