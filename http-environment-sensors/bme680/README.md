# BME680 HTTP Server and Hubitat Client

## What

This code allows for using a Bosch BME680 sensor with an ESP32 MCU to gather environmental data and send it over to a Hubitat device.

## Why

Because there are very few ZigBee/Z-Wave sensors that will provide barometric pressure or indoor air quality data. A BME680+ESP32 combination is cheap and effective.

## Hardware

The most "plug and play" version of hardware is listed below. There are many other compatible options, especially if you are comfortable with soldering.

- Hubitat Elevation device (obviously)
- BME680 breakout board, like [this one](https://www.sparkfun.com/products/16466)
- ESP32-S2 dev board, like [this one](https://www.adafruit.com/product/5000)
- JST SH 4-pin male-male cable, like [in this kit](https://www.sparkfun.com/products/15081)
- USB-C cable and power adapter (code is **not** optimized for battery power at all!)

## Software

### Basic, without air quality measurements

- CircuitPython

### Advanced, with air quality measurements

- Arduino IDE
- [Bosch BSEC2 library](https://github.com/BoschSensortec/Bosch-BSEC2-Library)

Note, most ESP32 variants are supported by the BSEC2 library, but you should still verify that you're getting something that will work with it.

## Directions

1. Acquire necessary hardware and connect everything together (the JST SH cable should be used to connect the ESP32 and BME680 boards)
2. Download and configure software/IDEs
3. Flash either the [CircuitPython code](circuitpython/) or the [Arduino code](arduino-esp32/bme680server.ino) (taking care to add/update Wi-Fi connection information)
4. Verify it's working by accessing its IP address via a web browser (e.g., http://192.168.1.23)
5. Ensure the IP address has a static lease (i.e., will not change between reboots)
6. Install the Hubitat driver and create a device, pointing to the configured IP address
