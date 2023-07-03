#include <WiFi.h>
#include <esp_task_wdt.h>
#include <ArduinoBLE.h>

const char* ssid = "your ssid here";
const char* password = "your password here";

#define AIRTHINGS_REFRESH_TIME_MSECS (1000 * 60 * 30)
#define AIRTHINGS_RETRY_MSECS (1000 * 30)
#define AIRTHINGS_MAX_CONNECT_FAILURES 10

#define WDT_TIMEOUT_SECS 30

// uncomment this out for devices with attached screens:
// (note: you must configure the TFT_eSPI library for your screen!)
//#define USE_SCREEN

#define AIRTHINGS_BLE_SERVICE "b42e1c08-ade7-11e4-89d3-123b93f75cba"
#define AIRTHINGS_BLE_CHARACTERISTIC "b42e2a68-ade7-11e4-89d3-123b93f75cba"

WiFiServer server(80);
BLEDevice airthingsDevice;

struct AirthingsData {
  uint8_t version;
  uint8_t humidity;
  uint8_t lightLevel;
  uint8_t _;
  uint16_t radon;
  uint16_t radonLongTerm;
  uint16_t temperature;
  uint16_t pressure;
  uint16_t co2;
  uint16_t voc;
};
byte airthingsBuffer[20] = {0};
AirthingsData* currentAirthingsData = (AirthingsData*)&airthingsBuffer;
String airthingsAddress = "N/A";

#ifdef USE_SCREEN
#include <SPI.h>
#include <TFT_eSPI.h>
TFT_eSPI tft = TFT_eSPI();
#endif

void sos() {
  // Flash LED until watchdog forces a reboot
  while(true) {
    Serial.println("SOS!");
    digitalWrite(LED_BUILTIN, HIGH);
    delay(200);
    digitalWrite(LED_BUILTIN, LOW);
    delay(200);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(200);
    digitalWrite(LED_BUILTIN, LOW);
    delay(200);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(200);
    digitalWrite(LED_BUILTIN, LOW);
    delay(200);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(800);
    digitalWrite(LED_BUILTIN, LOW);
    delay(200);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(800);
    digitalWrite(LED_BUILTIN, LOW);
    delay(200);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(800);
    digitalWrite(LED_BUILTIN, LOW);
    delay(200);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(200);
    digitalWrite(LED_BUILTIN, LOW);
    delay(200);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(200);
    digitalWrite(LED_BUILTIN, LOW);
    delay(200);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(200);
    digitalWrite(LED_BUILTIN, LOW);
    delay(2000);
  }
}

byte processIncomingLine(String& currentLine) {
  return (currentLine.startsWith("GET / HTTP/") ? 1 : 0);
}

void sendNotFound(WiFiClient& client) {
  client.println("HTTP/1.1 404 Not Found");
  client.println("Connection: close");
  client.println("Content-Length: 0");
  client.println();
}

void sendMessageChunk(WiFiClient& client, String chunk) {
  client.println(String(chunk.length(), HEX));
  client.println(chunk);
}

void sendMessageChunk(WiFiClient& client, String jsonName, double jsonValue, String suffix = ",") {
  sendMessageChunk(client, "\"" + jsonName + "\": " + String(jsonValue) + suffix);
}

void sendMessageChunk(WiFiClient& client, String jsonName, float jsonValue, String suffix = ",") {
  sendMessageChunk(client, "\"" + jsonName + "\": " + String(jsonValue) + suffix);
}

void sendMessageChunk(WiFiClient& client, String jsonName, int jsonValue, String suffix = ",") {
  sendMessageChunk(client, "\"" + jsonName + "\": " + String(jsonValue) + suffix);
}

void sendMessageChunk(WiFiClient& client, String jsonName, String jsonValue, String suffix = ",") {
  sendMessageChunk(client, "\"" + jsonName + "\": \"" + jsonValue + "\"" + suffix);
}

void sendStatus(WiFiClient& client) {
  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: application/json");
  client.println("Connection: close");
  client.println("Transfer-Encoding: chunked");
  client.println();
  sendMessageChunk(client, "{");
  sendMessageChunk(client, "last_airthings_address", airthingsAddress);
  if (currentAirthingsData->version == 1) {
    sendMessageChunk(client, "humidity_percent", currentAirthingsData->humidity / 2.0);
    sendMessageChunk(client, "light_level", currentAirthingsData->lightLevel);
    sendMessageChunk(client, "radon_bq_m3", currentAirthingsData->radon);
    sendMessageChunk(client, "long_term_radon_bq_m3", currentAirthingsData->radonLongTerm);
    sendMessageChunk(client, "temperature_c", currentAirthingsData->temperature / 100.0);
    sendMessageChunk(client, "pressure_hpa", currentAirthingsData->pressure / 50.0);
    sendMessageChunk(client, "co2_ppm", currentAirthingsData->co2);
    sendMessageChunk(client, "voc_ppb", currentAirthingsData->voc);
  }
  sendMessageChunk(client, "debug_free_heap", (int)ESP.getFreeHeap());
  sendMessageChunk(client, "debug_free_psram", (int)ESP.getFreePsram());
  sendMessageChunk(client, "debug_uptime_hrs", (double)1 * millis() / 3600000, "");
  sendMessageChunk(client, "}");
  sendMessageChunk(client, "");
}

void findAirthingsDevice() {
  BLEDevice dev = BLE.available();
  if (dev) {
    airthingsDevice = dev;
    BLE.stopScan();
  }
}

void accessAirthingsDevice() {
  static unsigned long lastAttempt = 0;
  static int unsuccessfulAttempts = 0;

  if (unsuccessfulAttempts > AIRTHINGS_MAX_CONNECT_FAILURES) {
    sos();
  }

  if (lastAttempt == 0 || millis() < lastAttempt || millis() > lastAttempt + AIRTHINGS_REFRESH_TIME_MSECS || (unsuccessfulAttempts > 0 && lastAttempt + AIRTHINGS_RETRY_MSECS > millis())) {
    lastAttempt = millis();
    unsuccessfulAttempts++;

    Serial.println("Connecting to Airthings");
    if (airthingsDevice.connect()) {
      esp_task_wdt_reset();
      airthingsAddress = airthingsDevice.address();
      Serial.println("Connected");
      Serial.println("Discovering attributes");
      if (airthingsDevice.discoverAttributes()) {
        esp_task_wdt_reset();
        Serial.println("Discovered");
        unsuccessfulAttempts = 0;
        BLEService airthingsService = airthingsDevice.service(AIRTHINGS_BLE_SERVICE);
        if (airthingsService) {
          BLECharacteristic airthingsChar = airthingsService.characteristic(AIRTHINGS_BLE_CHARACTERISTIC);
          if (airthingsChar) {
            if (airthingsChar.readValue(airthingsBuffer, sizeof(airthingsBuffer)) > 0) {
              esp_task_wdt_reset();
              Serial.println("Success");
              unsuccessfulAttempts = 0;
              airthingsDevice.disconnect();
              return;
            } else {
              Serial.println("Characteristic read did not return any data");
            }
          } else {
            Serial.println("Airthings characteristic is unavailable");
          }
        } else {
          Serial.println("Airthings service is unavailable");
        }
      } else {
        Serial.println("Discovery failed");
      }
      airthingsDevice.disconnect();
      unsuccessfulAttempts++;
    } else {
      esp_task_wdt_reset();
      Serial.println("Connection failed");
    }
  }
}

void processBleTasks() {
  if (!airthingsDevice) {
    findAirthingsDevice();
  } else {
    accessAirthingsDevice();
  }
}

#ifdef USE_SCREEN
void updateScreenInfo() {
  static bool metric = true;
  static unsigned long lastRefresh = 0;
  if (lastRefresh > 0 && millis() > lastRefresh && millis() < lastRefresh + 10000) return;
  
  lastRefresh = millis();

  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);  
  tft.setTextSize(1);

  tft.setCursor(0, 0, 2);
  tft.println(String("IP: ") + WiFi.localIP().toString());

  tft.setCursor(0, 20, 2);
  tft.println(String("Airthings: ") + airthingsAddress);

  if (currentAirthingsData->version == 1) {
    if (metric) {
      tft.setCursor(0, 40, 2);
      tft.println(String("Radon: ") + currentAirthingsData->radon + String(" Bq/m^3"));

      tft.setCursor(0, 60, 2);
      tft.println(String("Long Term Radon: ") + currentAirthingsData->radonLongTerm + String(" Bq/m^3"));

      tft.setCursor(0, 80, 2);
      tft.println(String("Temperature: ") + currentAirthingsData->temperature / 100.0 + String(" C"));
    } else {
      tft.setCursor(0, 40, 2);
      tft.println(String("Radon: ") + currentAirthingsData->radon / 37.0 + String(" pCi/L"));

      tft.setCursor(0, 60, 2);
      tft.println(String("Long Term Radon: ") + currentAirthingsData->radonLongTerm / 37.0 + String(" pCi/L"));

      tft.setCursor(0, 80, 2);
      tft.println(String("Temperature: ") + (currentAirthingsData->temperature / 100.0 * 9 / 5 + 32) + String(" F"));
    }

    tft.setCursor(0, 100, 2);
    tft.println(String("Humidity: ") + currentAirthingsData->humidity / 2.0 + String(" %"));
  }

  metric = !metric;
}
#endif

void setup() {
  Serial.begin(115200);
  Serial.println("Starting up");
  pinMode(LED_BUILTIN, OUTPUT);
  delay(100);

  // Initialize watchdog
  esp_task_wdt_init(WDT_TIMEOUT_SECS, true);
  esp_task_wdt_add(NULL);

#ifdef USE_SCREEN
  tft.init();
  tft.setRotation(3);
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);  
  tft.setTextSize(2);
  tft.setCursor(0, 0, 2);
  tft.println("Starting up");
#endif

  Serial.print("Connecting to AP: ");
  Serial.println(ssid);

  WiFi.setMinSecurity(WIFI_AUTH_WPA_PSK);
  WiFi.begin(ssid, password);

  // If Wi-Fi doesn't connect in WDT_TIMEOUT_SECS, we'll reboot and start over
  bool ledOn = true;
  digitalWrite(LED_BUILTIN, HIGH);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    ledOn = !ledOn;
    digitalWrite(LED_BUILTIN, ledOn ? HIGH : LOW);
  }
  digitalWrite(LED_BUILTIN, LOW);
  Serial.println("Connected!");

  esp_task_wdt_reset();

  server.begin();

  BLE.begin();
  BLE.scanForUuid(AIRTHINGS_BLE_SERVICE);
}

void loop() {
  esp_task_wdt_reset();
  processBleTasks();
#ifdef USE_SCREEN
  updateScreenInfo();
#endif
  esp_task_wdt_reset();
  delay(100);

  if (WiFi.status() != WL_CONNECTED) {
    sos();
  }

  WiFiClient client = server.available();

  if (client) {
    String currentLine = "";
    byte action = 0;
    while (client.connected()) {
      esp_task_wdt_reset();
      if (client.available()) {
        char c = client.read();
        if (c == '\r') continue;
        if (c == '\n') {
          if (currentLine.length() == 0) {
            // execute current action
            if (action == 0) {
              sendNotFound(client);
            } else {
              sendStatus(client);
            }
            break;
          }
          action = action | processIncomingLine(currentLine);
          currentLine = "";
          continue;
        }
        currentLine += c;
      }
    }
    client.stop();
  }
}
