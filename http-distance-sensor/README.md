# HTTP Distance Sensor HE driver

The HTTP distance sensor [Hubitat Elevation driver](https://github.com/arktronic/hubitat/blob/main/http-distance-sensor/http-distance-sensor-driver.groovy) included in this folder allows the use of an HTTP-based bridge to access distance data.

The bridge exposes distance data as JSON, and the HE driver simply reads that data.

The HE driver must be pointed to the correct IP address of the bridge. Hence, a static IP or a DHCP reservation is highly recommended.

## Sensor hardware and firmware

The [provided Arduino firmware](https://github.com/arktronic/hubitat/blob/main/http-distance-sensor/VL53L4CD-server.ino) has been tested on an ESP32-S2, but should work on most ESP32 variants. It's designed to query a [VL53L4CD ToF sensor](https://www.adafruit.com/product/5396) via a secondary I2C connection (STEMMA QT, in this case), but that configuration is easily modified.
