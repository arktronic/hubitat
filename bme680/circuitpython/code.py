import json
import wifi
import socketpool
import board
import adafruit_bme680
import time
from microcontroller import watchdog as w
from watchdog import WatchDogMode

from adafruit_httpserver.mime_type import MIMEType
from adafruit_httpserver.request import HTTPRequest
from adafruit_httpserver.response import HTTPResponse
from adafruit_httpserver.server import HTTPServer

w.timeout = 30 # seconds
w.mode = WatchDogMode.RESET
w.feed()

i2c = board.STEMMA_I2C()
bme680 = adafruit_bme680.Adafruit_BME680_I2C(i2c, debug=False, refresh_rate=1)
bme680.temperature_oversample = 16
bme680.humidity_oversample = 16
bme680.pressure_oversample = 16

try:
    from secrets import secrets
except ImportError:
    print("WiFi secrets are kept in secrets.py, please add them there!")
    raise

wifi.radio.hostname = secrets["hostname"]
print("My MAC address is", [hex(i) for i in wifi.radio.mac_address])

print("Connecting to \"%s\"..." % secrets["ssid"])
wifi.radio.connect(secrets["ssid"], secrets["password"])
print("Connected to \"%s\"!" % secrets["ssid"])

w.feed()

pool = socketpool.SocketPool(wifi.radio)
server = HTTPServer(pool)

@server.route("/")
def root_handler(request: HTTPRequest):
    data = {
        "temperature_c": bme680.temperature,
        "humidity_percent": bme680.relative_humidity,
        "pressure_hpa": bme680.pressure,
        "gas_ohm": bme680.gas,
    }

    with HTTPResponse(request, content_type=MIMEType.TYPE_JSON) as response:
        response.send(json.dumps(data))

print(f"Listening on http://{wifi.radio.ipv4_address}:80")
server.start(str(wifi.radio.ipv4_address))

while True:
    server.poll()
    time.sleep(0.1)
    w.feed()
