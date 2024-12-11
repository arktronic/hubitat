/*
ESP32 Airthings server

This is part of https://github.com/arktronic/hubitat (various Hubitat Elevation projects), although it does not require the Hubitat ecosystem.
See the README file for more info.

Hardware requirements:
- a WiFi- and BLE-capable ESP32 board
- TFT screen (optional)

Software requirements:
- this file, opened in the Arduino IDE
- the "ArduinoBLE" library (tested with version 1.3.7)
- the "TFT_eSPI" library (optional; tested with version 2.5.43)
- esp32 board support (tested with version 3.0.7)

Network endpoints:
/ - shows current status as JSON, including Airthings data when available
/ota - allows OTA updates
/reboot - allows to reboot the system

*** IMPORTANT: Set your SSID and password below. Also, change the admin username/password, which are used for OTA and reboots.
*/


// -----------------------------------------
// CONFIGURATION START
// -----------------------------------------

const char* ssid = "your ssid here";
const char* password = "your password here";

const char* adminUsername = "root";
const char* adminPassword = "toor";

// Comment this out for devices without attached screens:
// (note: you must configure the TFT_eSPI library for your screen, if you have one!)
#define USE_SCREEN

// -----------------------------------------
// CONFIGURATION END
// -----------------------------------------


#include <WiFi.h>
#include <esp_task_wdt.h>
#include <ArduinoBLE.h>
#include <WebServer.h>
#include <Update.h>

#define FIRMWARE_NAME "Airthings Bridge"
#define FIRMWARE_VERSION "v0.1.1"

#define AIRTHINGS_REFRESH_TIME_MSECS (1000 * 60 * 30)
#define AIRTHINGS_RETRY_MSECS (1000 * 30)
#define AIRTHINGS_MAX_CONNECT_FAILURES 10

#define AIRTHINGS_BLE_SERVICE "b42e1c08-ade7-11e4-89d3-123b93f75cba"
#define AIRTHINGS_BLE_CHARACTERISTIC "b42e2a68-ade7-11e4-89d3-123b93f75cba"

BLEDevice airthingsDevice;

WebServer server(80);

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

#ifndef LED_BUILTIN
#define LED_BUILTIN 13
#endif

void sos() {
  Serial.println("SOS!");
  digitalWrite(LED_BUILTIN, LOW);
  esp_task_wdt_reset();
  delay(800);
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
  delay(400);
  digitalWrite(LED_BUILTIN, HIGH);
  esp_task_wdt_reset();
  delay(800);
  digitalWrite(LED_BUILTIN, LOW);
  delay(200);
  digitalWrite(LED_BUILTIN, HIGH);
  esp_task_wdt_reset();
  delay(800);
  digitalWrite(LED_BUILTIN, LOW);
  delay(200);
  digitalWrite(LED_BUILTIN, HIGH);
  esp_task_wdt_reset();
  delay(800);
  digitalWrite(LED_BUILTIN, LOW);
  delay(400);
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

  esp_task_wdt_config_t twdt_config = {
      .timeout_ms = 100,
      .idle_core_mask = (1 << portNUM_PROCESSORS) - 1,    // Bitmask of all cores
      .trigger_panic = true,
  };
  esp_task_wdt_reconfigure(&twdt_config);
  delay(5000);
  ESP.restart();
  while(true);
}

void sendStatus() {
  String result = "{";
  result += "\"last_airthings_address\":\"" + String(airthingsAddress) + "\",";
  if (currentAirthingsData->version == 1) {
    result += "\"humidity_percent\":" + String(currentAirthingsData->humidity / 2.0) + ",";
    result += "\"light_level\":" + String(currentAirthingsData->lightLevel) + ",";
    result += "\"radon_bq_m3\":" + String(currentAirthingsData->radon) + ",";
    result += "\"long_term_radon_bq_m3\":" + String(currentAirthingsData->radonLongTerm) + ",";
    result += "\"temperature_c\":" + String(currentAirthingsData->temperature / 100.0) + ",";
    result += "\"pressure_hpa\":" + String(currentAirthingsData->pressure / 50.0) + ",";
    result += "\"co2_ppm\":" + String(currentAirthingsData->co2) + ",";
    result += "\"voc_ppb\":" + String(currentAirthingsData->voc) + ",";
  }
  result += "\"debug_free_heap\":" + String((int)ESP.getFreeHeap()) + ",";
  result += "\"debug_free_psram\":" + String((int)ESP.getFreePsram()) + ",";
  result += "\"debug_uptime_hrs\":" + String((double)1 * millis() / 3600000) + ",";
  result += "\"fw\":\"" + String(FIRMWARE_VERSION) + "\"}";

  esp_task_wdt_reset();
  server.send(200, "application/json", result);
}


void handleUpdateEnd() {
  esp_task_wdt_reset();
  server.sendHeader("Connection", "close");
  if (Update.hasError()) {
    server.send(500, "text/plain", Update.errorString());
  } else {
    server.sendHeader("Refresh", "10, url=/ota");
    server.send(200, "text/plain", "OK. Rebooting, please wait...");
  }
  delay(200);
  esp_task_wdt_reset();
  delay(200);
  ESP.restart();
}

void handleUpdate() {
  size_t fsize = UPDATE_SIZE_UNKNOWN;
  if (server.hasArg("size")) {
    fsize = server.arg("size").toInt();
  }
  esp_task_wdt_reset();
  HTTPUpload &upload = server.upload();
  esp_task_wdt_reset();
  if (upload.status == UPLOAD_FILE_START) {
    Serial.printf("Receiving Update: %s, Size: %d\n", upload.filename.c_str(), fsize);
    if (!Update.begin(fsize)) {
      Update.printError(Serial);
    }
  } else if (upload.status == UPLOAD_FILE_WRITE) {
    if (Update.write(upload.buf, upload.currentSize) != upload.currentSize) {
      Update.printError(Serial);
    } else {
      esp_task_wdt_reset();
    }
  } else if (upload.status == UPLOAD_FILE_END) {
    if (Update.end(true)) {
      esp_task_wdt_reset();
      Serial.printf("Update Success: %u bytes\nRebooting...\n", upload.totalSize);
    } else {
      Serial.printf("%s\n", Update.errorString());
    }
  }
}

void initWebServer() {
  server.on("/", HTTP_GET, []() {
    server.sendHeader("Connection", "close");
    sendStatus();
  });
  server.on("/reboot", HTTP_GET, []() {
    if (!server.authenticate(adminUsername, adminPassword)) {
      return server.requestAuthentication();
    }
    server.sendHeader("Connection", "close");
    server.send(200, "text/html", "<form method='POST' action='/rebootnow'><input type='submit' value='Reboot now!'></form>");
  });
  server.on("/rebootnow", HTTP_POST, []() {
    if (!server.authenticate(adminUsername, adminPassword)) {
      return server.requestAuthentication();
    }
    server.sendHeader("Connection", "close");
    server.sendHeader("Refresh", "10, url=/");
    server.send(200, "text/plain", "Rebooting, please wait...");
    delay(500);
    ESP.restart();
  });
  server.on("/ota", HTTP_GET, []() {
    if (!server.authenticate(adminUsername, adminPassword)) {
      return server.requestAuthentication();
    }
    server.sendHeader("Connection", "close");
    server.send(200, "text/html", "<div>" + String(FIRMWARE_NAME) + " " + String(FIRMWARE_VERSION) + "</div><br /><form method='POST' action='/update' enctype='multipart/form-data'><input type='file' name='update'><input type='submit' value='Flash'></form>");
  });
  server.on(
    "/update", HTTP_POST,
    []() {
      handleUpdateEnd();
    },
    []() {
      if (!server.authenticate(adminUsername, adminPassword)) {
        return server.requestAuthentication();
      }
      handleUpdate();
    }
  );
  server.onNotFound([]() {
    server.send(404, "text/plain", "Not Found");
  });
  server.begin();
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
  esp_task_wdt_deinit();

  Serial.begin(9600);
  Serial.println();
  Serial.println("Starting up... firmware " + String(FIRMWARE_NAME) + " " + String(FIRMWARE_VERSION));
  pinMode(LED_BUILTIN, OUTPUT);
  delay(100);

  // Initialize watchdog
  esp_task_wdt_config_t twdt_config = {
      .timeout_ms = 30000,
      .idle_core_mask = (1 << portNUM_PROCESSORS) - 1,    // Bitmask of all cores
      .trigger_panic = true,
  };
  esp_task_wdt_init(&twdt_config);
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

  WiFi.setHostname("AirthingsBridge");
  WiFi.setMinSecurity(WIFI_AUTH_WPA_PSK);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(ssid, password);
  WiFi.setSleep(false);

  unsigned long wifiConnectStart = millis();
  bool ledOn = true;
  digitalWrite(LED_BUILTIN, HIGH);
  uint8_t counter = 0;
  while (WiFi.status() != WL_CONNECTED && millis() >= wifiConnectStart && millis() < wifiConnectStart + 30000) {
    if (counter % 50 == 0) {
      Serial.println("WiFi status: " + String(WiFi.status()));
    }
    delay(50);
    ledOn = !ledOn;
    digitalWrite(LED_BUILTIN, ledOn ? HIGH : LOW);
    esp_task_wdt_reset();
    counter++;
  }
  digitalWrite(LED_BUILTIN, LOW);
  esp_task_wdt_reset();

  if (WiFi.status() != WL_CONNECTED) {
    sos();
  }
  Serial.println("Connected!");

  digitalWrite(LED_BUILTIN, LOW);
  initWebServer();

  esp_task_wdt_reset();

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
  esp_task_wdt_reset();
  server.handleClient();
  esp_task_wdt_reset();

  if (WiFi.status() != WL_CONNECTED) {
    sos();
  }
}
