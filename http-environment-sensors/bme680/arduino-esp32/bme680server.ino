#include <esp_task_wdt.h>
#include <WiFi.h>
#include <EEPROM.h>
#include <bsec2.h>

#define WDT_TIMEOUT_SECS 30
#define STATE_SAVE_PERIOD UINT32_C(360 * 60 * 1000) /* 360 minutes - 4 times a day */

const char* ssid = "your ssid here";
const char* password = "your password here";

WiFiServer server(80);
Bsec2 envSensor;
static uint8_t bsecState[BSEC_MAX_STATE_BLOB_SIZE];

float bsecDataIaq = 0;
int bsecDataIaqAccuracy = -1;
float bsecDataCo2 = 0;
int bsecDataCo2Accuracy = -1;
float bsecDataTemperature = 0;
float bsecDataHumidity = 0;
float bsecDataPressure = 0;
float bsecDataGas = 0;
float bsecDataStabilizationStatus = -1;
float bsecDataRunInStatus = -1;
String bsecDataVersion;

void sos() {
  // Flash LED until watchdog forces a reboot
  while(true) {
    Serial.println("SOS!");
    Serial.println("BSEC status code: " + String(envSensor.status));
    Serial.println("BME status code: " + String(envSensor.sensor.status));
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

String getAccuracyText(int accuracy) {
  switch(accuracy) {
    case 1:
      return "Low";
    case 2:
      return "Medium";
    case 3:
      return "High";
    default:
      return "Unreliable";
  }
}

void sendStatus(WiFiClient& client) {
  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: application/json");
  client.println("Connection: close");
  client.println("Transfer-Encoding: chunked");
  client.println();
  sendMessageChunk(client, "{");
  if (bsecDataIaqAccuracy > -1) {
    sendMessageChunk(client, "gas_stabilization_status", (bsecDataStabilizationStatus < 0.5 ? "Ongoing" : "Finished"));
    sendMessageChunk(client, "gas_run_in_status", (bsecDataRunInStatus < 0.5 ? "Ongoing" : "Finished"));
    if (bsecDataStabilizationStatus > 0.5 && bsecDataRunInStatus > 0.5) {
      if (bsecDataIaqAccuracy > 0) {
        sendMessageChunk(client, "iaq", bsecDataIaq);
      }
      sendMessageChunk(client, "iaq_accuracy", getAccuracyText(bsecDataIaqAccuracy));
      if (bsecDataCo2Accuracy > 0) {
        sendMessageChunk(client, "co2_ppm", bsecDataCo2);
      }
      sendMessageChunk(client, "co2_accuracy", getAccuracyText(bsecDataCo2Accuracy));
      sendMessageChunk(client, "gas_ohm", bsecDataGas);
    }
    sendMessageChunk(client, "temperature_c", bsecDataTemperature);
    sendMessageChunk(client, "humidity_percent", bsecDataHumidity);
    sendMessageChunk(client, "pressure_hpa", bsecDataPressure);
  }
  sendMessageChunk(client, "bsec_version", bsecDataVersion);
  sendMessageChunk(client, "bsec_raw_status_code", (int)envSensor.status);
  sendMessageChunk(client, "bme_raw_status_code",  (int)envSensor.sensor.status);
  sendMessageChunk(client, "debug_free_heap", (int)ESP.getFreeHeap());
  sendMessageChunk(client, "debug_free_psram", (int)ESP.getFreePsram());
  sendMessageChunk(client, "debug_uptime_hrs", (double)1 * millis() / 3600000, "");
  sendMessageChunk(client, "}");
  sendMessageChunk(client, "");
}

void loadBsecState() {
  if (EEPROM.read(0) == BSEC_MAX_STATE_BLOB_SIZE) {
    Serial.println("Loading BSEC state from EEPROM");
    for (uint8_t i = 0; i < BSEC_MAX_STATE_BLOB_SIZE; i++) {
      bsecState[i] = EEPROM.read(i + 1);
    }
    envSensor.setState(bsecState);
  } else {
    Serial.println("Erasing BSEC EEPROM area");
    for (uint8_t i = 0; i <= BSEC_MAX_STATE_BLOB_SIZE; i++) {
      EEPROM.write(i, 0);
    }
    EEPROM.commit();
  }
}

void saveBsecState() {
  if (!envSensor.getState(bsecState))
    return;

  Serial.println("Writing BSEC state to EEPROM");
  for (uint8_t i = 0; i < BSEC_MAX_STATE_BLOB_SIZE; i++)
  {
    EEPROM.write(i + 1, bsecState[i]);
  }
  EEPROM.write(0, BSEC_MAX_STATE_BLOB_SIZE);
  EEPROM.commit();
}

void updateBsecState() {
  static uint16_t stateUpdateCounter = 0;
  bool update = false;

  if (!stateUpdateCounter || (stateUpdateCounter * STATE_SAVE_PERIOD) < millis()) {
    update = true;
    stateUpdateCounter++;
  }

  if (update)
    saveBsecState();
}

void sensorCallback(const bme68xData data, const bsecOutputs outputs, Bsec2 bsec) {
  if (!outputs.nOutputs)
    return;

  for (uint8_t i = 0; i < outputs.nOutputs; i++) {
    const bsecData output  = outputs.output[i];
    switch (output.sensor_id) {
      case BSEC_OUTPUT_IAQ:
        bsecDataIaq = output.signal;
        bsecDataIaqAccuracy = output.accuracy;
        break;
      case BSEC_OUTPUT_CO2_EQUIVALENT:
        bsecDataCo2 = output.signal;
        bsecDataCo2Accuracy = output.accuracy;
        break;
      case BSEC_OUTPUT_SENSOR_HEAT_COMPENSATED_TEMPERATURE:
        bsecDataTemperature = output.signal;
        break;
      case BSEC_OUTPUT_SENSOR_HEAT_COMPENSATED_HUMIDITY:
        bsecDataHumidity = output.signal;
        break;
      case BSEC_OUTPUT_RAW_PRESSURE:
        bsecDataPressure = output.signal / 100;
        break;
      case BSEC_OUTPUT_RAW_GAS:
        bsecDataGas = output.signal;
        break;
      case BSEC_OUTPUT_STABILIZATION_STATUS:
        bsecDataStabilizationStatus = output.signal;
        break;
      case BSEC_OUTPUT_RUN_IN_STATUS:
        bsecDataRunInStatus = output.signal;
        break;
      default:
        break;
    }
  }

  updateBsecState();
}

void setup() {
  bsecSensor sensorList[] = {
    BSEC_OUTPUT_IAQ,
    BSEC_OUTPUT_CO2_EQUIVALENT,
    BSEC_OUTPUT_RAW_TEMPERATURE,
    BSEC_OUTPUT_SENSOR_HEAT_COMPENSATED_TEMPERATURE,
    BSEC_OUTPUT_RAW_HUMIDITY,
    BSEC_OUTPUT_SENSOR_HEAT_COMPENSATED_HUMIDITY,
    BSEC_OUTPUT_RAW_PRESSURE,
    BSEC_OUTPUT_RAW_GAS,
    BSEC_OUTPUT_STABILIZATION_STATUS,
    BSEC_OUTPUT_RUN_IN_STATUS
  };

  Serial.begin(115200);
  EEPROM.begin(BSEC_MAX_STATE_BLOB_SIZE + 1);
  Wire.begin();
  pinMode(LED_BUILTIN, OUTPUT);
  delay(10);

  // Initialize watchdog
  esp_task_wdt_init(WDT_TIMEOUT_SECS, true);
  esp_task_wdt_add(NULL);

  if (!envSensor.begin(BME68X_I2C_ADDR_HIGH, Wire)) {
    Serial.println("FATAL: cannot connect to sensor!");
    sos();
  }
  loadBsecState();
  if (!envSensor.updateSubscription(sensorList, ARRAY_LEN(sensorList), BSEC_SAMPLE_RATE_LP)) {
    Serial.println("FATAL: cannot subscribe to sensor events!");
    sos();
  }
  envSensor.attachCallback(sensorCallback);
  bsecDataVersion = String(envSensor.version.major) + "." + String(envSensor.version.minor) + "." + String(envSensor.version.major_bugfix) + "." + String(envSensor.version.minor_bugfix);

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
}

void loop() {
  esp_task_wdt_reset();
  envSensor.run();
  delay(100);
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
