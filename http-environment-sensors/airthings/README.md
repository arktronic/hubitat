# Airthings Wave Plus HTTP Server and Hubitat Client

## What

This code allows for using an ESP32 MCU to query an Airthings Wave Plus device over BLE and send the data over to a Hubitat device.

## Why

Because radon monitoring is important, and there don't appear to be any ZigBee or Z-Wave radon monitoring devices on the market.

## Hardware

- Hubitat Elevation device and Airthings Wave Plus (obviously)
- ESP32 dev board, with or without an attached screen - NOTE: ESP32-S2 devices cannot be used, as they have no Bluetooth support!
- USB-C cable and power adapter (code is **not** optimized for battery power at all!)

## Software

- Arduino IDE

## Directions

1. Acquire necessary hardware
2. Download and configure software/IDEs
3. Flash the [Arduino code](airthingsbridge.ino) (taking care to add/update Wi-Fi connection information)
4. Verify it's working by accessing its IP address via a web browser (e.g., http://192.168.1.23)
5. Ensure the IP address has a static lease (i.e., will not change between reboots)
6. Install the [Hubitat driver](../http-env-sensor-driver.groovy) and create a device, pointing to the configured IP address
