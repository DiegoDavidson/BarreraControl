# BarreraControl

App Android (Kotlin) para controlar una barrera de estacionamiento con acceso por tarjeta. Proyecto TI3042 Aplicaciones Móviles para IoT.

## Problemática

Abrir y cerrar la barrera desde el celular, permitir el ingreso solo a tarjetas autorizadas y dejar registro de quién entró y cuándo.

Dispositivos: celulares Android, Arduino Uno con módulo Bluetooth HC-05, sensor ultrasónico HC-SR04, servo y lector RC522.

## Cómo funciona

La conexión con el Arduino es **real**, por Bluetooth de bajo consumo (BLE) con el módulo HC-05 (conexión GATT, canal serie tipo HM-10). El Arduino es la fuente de verdad del estado de la barrera; la app manda órdenes y reacciona a sus mensajes.

1. **LIBRE:** barrera cerrada, sin auto.
2. **ESPERANDO:** el sensor detecta un auto. Al pasar una tarjeta, el Arduino manda su UID a la app.
3. La app valida el UID en la colección `tarjetas` de Firestore. Si está registrada y activa, le ordena abrir; si no, responde `DENEGADO`.
4. **ABIERTA:** el auto está pasando.
5. **POR_CERRAR:** el sensor ya no detecta el auto. Si pasan 3 s sin detectar nada, el Arduino cierra solo. Si detecta algo antes, vuelve a ABIERTA.
6. Desde la app también se puede abrir o cerrar manualmente. El Arduino rechaza el cierre si hay un auto bajo la barrera.

El HC-05 acepta un solo celular a la vez. Los demás celulares entran en **modo solo lectura** y ven el estado en tiempo real por Firestore (sin botones de control).

Si se corta la conexión, la app muestra "Reconectando..." y reintenta cada 3 s hasta conectar.

## Protocolo (texto, una línea por mensaje terminada en `\n`)

### App → Arduino

| Comando | Significado |
|---|---|
| `ABRIR` | Apertura manual |
| `CERRAR` | Cierre manual (el Arduino lo rechaza si hay un auto) |
| `ABRIR_TARJETA` | Respuesta a `TARJETA:<uid>` cuando la tarjeta es válida |
| `DENEGADO` | Respuesta a `TARJETA:<uid>` cuando la tarjeta no es válida |
| `ESTADO` | Pide el estado actual (se manda al conectar) |
| `LEER_TARJETA` | Modo registro: la próxima tarjeta leída se devuelve como `UID:<uid>` |
| `SIMULAR:<uid>` | Solo mientras no esté el RC522: el Arduino lo trata como si el lector hubiera leído esa tarjeta |

### Arduino → App

| Mensaje | Significado |
|---|---|
| `ESTADO:<LIBRE\|ESPERANDO\|ABIERTA\|POR_CERRAR>` | Respuesta a `ESTADO` |
| `AUTO` | Llegó un auto (pasa a ESPERANDO) |
| `AUTO_SE_FUE` | El auto se fue sin pasar tarjeta (vuelve a LIBRE) |
| `TARJETA:<uid>` | Se pasó una tarjeta con un auto esperando; la app debe validarla |
| `UID:<uid>` | Tarjeta leída en modo registro |
| `LISTO_PARA_LEER` | Confirma el modo registro |
| `ABIERTA:<APP\|TARJETA>` | Se abrió, con el motivo |
| `CERRADA:<APP\|AUTO>` | Se cerró, con el motivo |
| `NO_SE_PUEDE_CERRAR` | Se rechazó `CERRAR` porque hay un auto |
| `COMANDO_INVALIDO` | El Arduino no entendió el comando |

Los UID tienen el formato `XX XX XX XX` (hexadecimal en mayúsculas, separado por espacios).

El estado en Firestore (`barrera/barrera1`) y el historial (`eventos`) se escriben cuando llegan mensajes del Arduino, no al enviar un comando.

## Sketch del Arduino

Está en [`arduino/barrera/barrera.ino`](arduino/barrera/barrera.ino). Conexiones: HC-05 en D4/D5 (38400 baudios, TX del Arduino con divisor de voltaje), HC-SR04 en D7/D6 y servo en D3.

**El lector RC522 todavía se simula.** Hay dos formas de simular una tarjeta:

- Desde la app, con el botón "Simular tarjeta en el lector" (envía `SIMULAR:<uid>`).
- Desde el Monitor Serie del PC (9600 baudios): `T` = tarjeta `A1 B2 C3 D4`, `X` = tarjeta desconocida `00 00 00 00`.

En ambos casos el UID vuelve a la app como `TARJETA:<uid>`, igual que lo haría el lector real. Cuando llegue el RC522, solo hay que cambiar la función `leerTarjeta()` del sketch y poner `LECTOR_SIMULADO = false` en `Barrera.kt`.

## Funcionalidades

- Registro, login y recuperación de contraseña
- Abrir y cerrar la barrera por Bluetooth, con cierre automático manejado por el Arduino
- Estado sincronizado en tiempo real entre celulares (Firestore)
- Acceso con tarjeta: solo abre si está registrada y activa
- Gestión de tarjetas: agregar (lectura desde el Arduino), pausar, eliminar
- Historial de eventos
- Reconexión automática y modo solo lectura
- Cierre de sesión por inactividad (15 min), que también desconecta el Bluetooth

## Herramientas

- Kotlin + Android Studio
- Firebase Authentication y Cloud Firestore
- Bluetooth BLE con `BluetoothGatt`

## Seguridad (ISO 27400)

- Credenciales gestionadas por Firebase Auth
- Reglas de Firestore (`firestore.rules`): solo usuarios con sesión, historial no modificable
- Conexión HTTPS/TLS con Firebase
- Cierre de sesión por inactividad
- Permisos: INTERNET y BLUETOOTH_CONNECT
- Pendiente: cifrar o firmar los comandos Bluetooth

## Cómo ejecutar y probar

1. Cargar `barrera.ino` en el Arduino. El módulo se conecta por BLE usando su MAC (`MAC_HC05` en `Barrera.kt`), sin emparejarlo desde los Ajustes.
2. Publicar `firestore.rules` en Firebase Console y abrir el proyecto en Android Studio.
3. Iniciar sesión y en "Conectar la barrera" tocar **Conectar con el HC-05**.
4. En "Tarjetas autorizadas" → **Agregar tarjeta**: la app genera un UID simulado, que pasa por el Arduino, y se guarda con un nombre.
5. Acercar la mano al sensor y tocar **Simular tarjeta**: con una tarjeta registrada la barrera abre y queda "Entrada autorizada" en el historial; con una desconocida o pausada queda "Acceso denegado".
6. Simular una tarjeta sin auto delante: el Arduino la ignora.
7. Sacar la mano: a los 3 s la barrera cierra y queda "Cierre automático".
8. Abrir y cerrar desde la app; probar cerrar con la mano bajo la barrera.
9. Apagar y encender el Arduino: la app se reconecta sola.
10. En un segundo celular con otra cuenta, **Entrar sin conexión**: ve el estado en tiempo real con los botones deshabilitados.
