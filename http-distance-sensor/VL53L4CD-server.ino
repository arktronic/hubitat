#include <esp_task_wdt.h>
#include <WiFi.h>
#include <Wire.h>
#include <vl53l4cd_class.h>

const char* ssid = "your ssid here";
const char* password = "your password here";

#define RESULTS_TOTAL 10
#define WDT_TIMEOUT_SECS 30
#define I2C Wire1
#define I2C_PINS SDA1, SCL1
#ifndef LED_BUILTIN
  #define LED_BUILTIN 13
#endif

VL53L4CD_Result_t results[RESULTS_TOTAL];
uint8_t currentResultIndex = 0;
bool resultsReady = false;

VL53L4CD tofSensor(&I2C, -1);

WiFiServer server(80);

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

void sendMessageChunk(WiFiClient& client, String jsonName, uint16_t jsonValue, String suffix = ",") {
  sendMessageChunk(client, "\"" + jsonName + "\": " + String(jsonValue) + suffix);
}

void sendMessageChunk(WiFiClient& client, String jsonName, bool jsonValue, String suffix = ",") {
  sendMessageChunk(client, "\"" + jsonName + "\": " + (jsonValue ? "true" : "false") + suffix);
}

void sendMessageChunk(WiFiClient& client, String jsonName, String jsonValue, String suffix = ",") {
  sendMessageChunk(client, "\"" + jsonName + "\": \"" + jsonValue + "\"" + suffix);
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

void sendStatus(WiFiClient& client) {
  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: application/json");
  client.println("Connection: close");
  client.println("Transfer-Encoding: chunked");
  client.println();
  sendMessageChunk(client, "{");

  bool validated;
  uint16_t distance_mm;
  bool gotDistance = getDistance(validated, distance_mm);
  if (gotDistance) {
    sendMessageChunk(client, "distance_validated", validated);
    sendMessageChunk(client, "distance_mm", distance_mm);
  }
  sendMessageChunk(client, "debug_free_heap", (int)ESP.getFreeHeap());
  sendMessageChunk(client, "debug_free_psram", (int)ESP.getFreePsram());
  sendMessageChunk(client, "debug_uptime_hrs", (double)1 * millis() / 3600000, "");
  sendMessageChunk(client, "}");
  sendMessageChunk(client, "");
}

void setup() {
  I2C.setPins(I2C_PINS);
  I2C.begin();
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);
  delay(10);

  // Initialize watchdog
  esp_task_wdt_init(WDT_TIMEOUT_SECS, true);
  esp_task_wdt_add(NULL);

  WiFi.setMinSecurity(WIFI_AUTH_WPA_PSK);
  WiFi.begin(ssid, password);

  // If Wi-Fi doesn't connect in WDT_TIMEOUT_SECS, we'll reboot and start over
  bool ledOn = true;
  digitalWrite(LED_BUILTIN, HIGH);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    ledOn = !ledOn;
    digitalWrite(LED_BUILTIN, ledOn ? HIGH : LOW);
  }
  digitalWrite(LED_BUILTIN, LOW);

  esp_task_wdt_reset();
  
  server.begin();

  tofSensor.begin();
  tofSensor.VL53L4CD_Off();
  tofSensor.InitSensor();
  tofSensor.VL53L4CD_SetRangeTiming(200, 0);
  tofSensor.VL53L4CD_StartRanging();
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
