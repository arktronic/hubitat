# HTTP Environment Sensors

The HTTP environment sensor [Hubitat Elevation driver](https://github.com/arktronic/hubitat/blob/main/http-environment-sensors/http-env-sensor-driver.groovy) included in this folder allows the use of HTTP-based bridges to access environmental data.

The bridges expose environmental data as JSON, and the HE driver simply reads that data.

The HE driver must be pointed to the correct IP address of the bridge. Hence, a static IP or a DHCP reservation is highly recommended.

## Bridges

* [BME680](bme680/)
* [Airthings Wave Plus](airthings/)
