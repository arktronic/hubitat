/*
ESP32 ToF sensor server

This is part of https://github.com/arktronic/hubitat (various Hubitat Elevation projects), although it does not require the Hubitat ecosystem.
See the README file for more info.

Hardware requirements:
- a WiFi-capable ESP32 board
- VL53L4CD sensor

Software requirements:
- this file, opened in the Arduino IDE
- the "STM32duino VL53L4CD" library (tested with version 1.0.5)
- esp32 board support (tested with version 3.0.7)

Network endpoints:
/ - shows current status as JSON, including ToF distance when available
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

// -----------------------------------------
// CONFIGURATION END
// -----------------------------------------


#include <esp_task_wdt.h>
#include <WiFi.h>
#include <Wire.h>
#include <vl53l4cd_class.h>
#include <WebServer.h>
#include <Update.h>

#define FIRMWARE_NAME "VL53L4CD ToF server"
#define FIRMWARE_VERSION "v0.1.1"

#define RESULTS_TOTAL 10
#define I2C Wire1
#define I2C_PINS SDA1, SCL1
#ifndef LED_BUILTIN
  #define LED_BUILTIN 13
#endif

VL53L4CD_Result_t results[RESULTS_TOTAL];
uint8_t currentResultIndex = 0;
bool resultsReady = false;

VL53L4CD tofSensor(&I2C, -1);

WebServer server(80);

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

bool getDistance(bool& validated, uint16_t& distance_mm) {
  validated = false;
  distance_mm = 0;
  if (!resultsReady) return false;

  byte valid_results = 0;
  byte workable_results = 0;
  double valid_average = 0;
  double workable_average = 0;
  for (byte i = 0; i < RESULTS_TOTAL; i++) {
    if (results[i].range_status == 0) {
      valid_results++;
      workable_results++;
      valid_average += results[i].distance_mm;
      workable_average += results[i].distance_mm;
    } else if (results[i].distance_mm > 0) {
      workable_results++;
      workable_average += results[i].distance_mm;
    }
  }

  if (valid_results > 3) {
    validated = true;
    distance_mm = (uint16_t)(valid_average / valid_results);
    return true;
  }
  if (workable_results > 6) {
    validated = false;
    distance_mm = (uint16_t)(workable_average / workable_results);
    return true;
  }
  return false;
}

void sendStatus() {
  String result = "{";
  bool validated;
  uint16_t distance_mm;
  bool gotDistance = getDistance(validated, distance_mm);
  if (gotDistance) {
    result += "\"distance_validated\":" + String(validated ? "true" : "false") + ",";
    result += "\"distance_mm\":" + String(distance_mm) + ",";
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

void setup() {
  esp_task_wdt_deinit();
  I2C.setPins(I2C_PINS);
  I2C.begin();
  Serial.begin(9600);
  pinMode(LED_BUILTIN, OUTPUT);
  delay(10);
  Serial.println();
  Serial.println("Starting up... firmware " + String(FIRMWARE_NAME) + " " + String(FIRMWARE_VERSION));

  // Initialize watchdog
  esp_task_wdt_config_t twdt_config = {
      .timeout_ms = 30000,
      .idle_core_mask = (1 << portNUM_PROCESSORS) - 1,    // Bitmask of all cores
      .trigger_panic = true,
  };
  esp_task_wdt_init(&twdt_config);
  esp_task_wdt_add(NULL);

  WiFi.setMinSecurity(WIFI_AUTH_WPA_PSK);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(ssid, password);

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
  
  digitalWrite(LED_BUILTIN, LOW);
  initWebServer();

  esp_task_wdt_reset();

  tofSensor.begin();
  tofSensor.VL53L4CD_Off();
  if (tofSensor.InitSensor() == VL53L4CD_ERROR_NONE) {
    tofSensor.VL53L4CD_SetRangeTiming(200, 0);
    tofSensor.VL53L4CD_StartRanging();
  }
}

void processDistanceReading() {
  // Slow down readings
  static byte shouldRead = 0;
  if (shouldRead++ % 3 != 0) return;

  uint8_t newDataReady = 0;
  if (tofSensor.VL53L4CD_CheckForDataReady(&newDataReady) != 0 || newDataReady == 0) {
    // Not ready.
    return;
  }

  tofSensor.VL53L4CD_ClearInterrupt();
  tofSensor.VL53L4CD_GetResult(&results[currentResultIndex]);

  Serial.println(String("#") + String(currentResultIndex) + ": " + String(results[currentResultIndex].range_status) + " -- " + String(results[currentResultIndex].distance_mm));

  currentResultIndex++;
  if (currentResultIndex >= RESULTS_TOTAL) {
    currentResultIndex = 0;
    resultsReady = true;
  }
}

void loop() {
  esp_task_wdt_reset();
  processDistanceReading();
  delay(200);
  esp_task_wdt_reset();
  server.handleClient();
  esp_task_wdt_reset();

  if (WiFi.status() != WL_CONNECTED) {
    sos();
  }
}
