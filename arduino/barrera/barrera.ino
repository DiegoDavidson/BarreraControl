// Barrera de estacionamiento - TI3042 Evaluacion 3
// Arduino Uno + HC-05 (D4/D5, 38400 baudios) + HC-SR04 (D7/D6) + servo (D3)
// Lector RC522 simulado: comando SIMULAR:<uid> desde la app,
// o desde el Monitor Serie: T = tarjeta registrada, X = desconocida

#include <Servo.h>
#include <SoftwareSerial.h>

Servo barrera;
SoftwareSerial bt(4, 5);  // D4 <- TXD del HC-05 | D5 -> RXD del HC-05 (con divisor)

const int TRIG = 7, ECHO = 6, SERVO = 3;
const int DISTANCIA_AUTO = 10;             // cm
const unsigned long TIEMPO_CIERRE = 3000;  // ms sin detectar nada antes de cerrar

enum Estado { LIBRE, ESPERANDO, ABIERTA, POR_CERRAR };
const char* NOMBRES[] = { "LIBRE", "ESPERANDO", "ABIERTA", "POR_CERRAR" };
Estado estado = LIBRE;
unsigned long inicioCierre = 0;
bool modoRegistro = false;
String comando = "";
String tarjetaSimulada = "";  // UID recibido con SIMULAR:<uid>, se usa una vez

// Manda un mensaje a la app y lo muestra en el Monitor Serie
void enviar(String msg) {
  Serial.println("-> " + msg);
  bt.println(msg);
}

void cambiarEstado(Estado nuevo) {
  estado = nuevo;
  Serial.print("[Estado] ");
  Serial.println(NOMBRES[estado]);
}

void abrir(const char* motivo) {
  barrera.write(90);
  cambiarEstado(ABIERTA);
  enviar(String("ABIERTA:") + motivo);
}

void cerrar(const char* motivo) {
  barrera.write(0);
  cambiarEstado(LIBRE);
  enviar(String("CERRADA:") + motivo);
}

long ultimoCm = 0;                  // ultima distancia medida (0 = sin eco)
unsigned long ultimoEnvioDist = 0;
const unsigned long PERIODO_DIST = 1000;  // ms entre mensajes DIST:<cm> a la app

bool hayAuto() {
  digitalWrite(TRIG, LOW);  delayMicroseconds(2);
  digitalWrite(TRIG, HIGH); delayMicroseconds(10);
  digitalWrite(TRIG, LOW);
  long cm = pulseIn(ECHO, HIGH, 30000) / 58;
  ultimoCm = cm;
  return cm > 0 && cm < DISTANCIA_AUTO;
}

// SIMULACION del RC522. Cuando llegue el lector, aqui se lee el UID real.
String leerTarjeta() {
  if (tarjetaSimulada != "") {  // desde la app
    String uid = tarjetaSimulada;
    tarjetaSimulada = "";
    return uid;
  }
  while (Serial.available()) {  // desde el Monitor Serie
    char c = toupper(Serial.read());
    if (c == 'T') return "A1 B2 C3 D4";
    if (c == 'X') return "00 00 00 00";
  }
  return "";
}

void procesarComando(String c, bool autoAhora) {
  c.trim();
  if (c == "ABRIR") {
    if (estado != ABIERTA) abrir("APP");
  } else if (c == "ABRIR_TARJETA") {
    if (estado == ESPERANDO) abrir("TARJETA");
  } else if (c == "DENEGADO") {
    Serial.println("Acceso denegado por la app");
  } else if (c == "CERRAR") {
    if (autoAhora) enviar("NO_SE_PUEDE_CERRAR");  // nunca cerrar sobre un auto
    else if (estado == ABIERTA || estado == POR_CERRAR) cerrar("APP");
    else enviar(String("ESTADO:") + NOMBRES[estado]);
  } else if (c == "ESTADO") {
    enviar(String("ESTADO:") + NOMBRES[estado]);
  } else if (c == "LEER_TARJETA") {
    modoRegistro = true;
    enviar("LISTO_PARA_LEER");
  } else if (c.startsWith("SIMULAR:")) {
    tarjetaSimulada = c.substring(8);
  } else {
    enviar("COMANDO_INVALIDO");
  }
}

void setup() {
  Serial.begin(9600);
  bt.begin(38400);
  barrera.attach(SERVO);
  barrera.write(0);
  pinMode(TRIG, OUTPUT);
  pinMode(ECHO, INPUT);
  Serial.println("Barrera lista");
  cambiarEstado(LIBRE);
}

void loop() {
  bool autoAhora = hayAuto();

  // 1) Comandos de la app (una linea, maximo 20 caracteres)
  while (bt.available()) {
    char ch = bt.read();
    if (ch == '\n') { procesarComando(comando, autoAhora); comando = ""; }
    else if (ch != '\r' && comando.length() < 20) comando += ch;
  }

  // 2) Tarjeta
  String uid = leerTarjeta();
  if (uid != "") {
    if (modoRegistro)            { enviar("UID:" + uid); modoRegistro = false; }
    else if (estado == ESPERANDO) enviar("TARJETA:" + uid);
    else                          Serial.println("Tarjeta ignorada: no hay auto esperando");
  }

  // 3) Logica de la barrera
  switch (estado) {
    case LIBRE:
      if (autoAhora) { cambiarEstado(ESPERANDO); enviar("AUTO"); }
      break;
    case ESPERANDO:
      if (!autoAhora) { cambiarEstado(LIBRE); enviar("AUTO_SE_FUE"); }
      break;
    case ABIERTA:
      if (!autoAhora) { cambiarEstado(POR_CERRAR); inicioCierre = millis(); }
      break;
    case POR_CERRAR:
      if (autoAhora) cambiarEstado(ABIERTA);  // aparecio algo: no cerrar
      else if (millis() - inicioCierre > TIEMPO_CIERRE) cerrar("AUTO");
      break;
  }

  // 4) Lectura del sensor para el panel de la app
  if (millis() - ultimoEnvioDist >= PERIODO_DIST) {
    ultimoEnvioDist = millis();
    bt.println(String("DIST:") + ultimoCm);  // sin eco en el Monitor Serie para no llenarlo
  }

  delay(100);
}
