BarreraControl



App Android (Kotlin) para controlar una barrera de estacionamiento con acceso por tarjeta NFC. Proyecto TI3042 Aplicaciones Móviles para IoT, Unidad 2.



Problemática



Abrir y cerrar la barrera desde el celular, permitir el ingreso solo a tarjetas autorizadas y dejar registro de quién entró y cuándo.



Dispositivos a conectar: celulares Android, Arduino Uno con módulo Bluetooth HC-05, lector NFC RC522 y servo.



La conexión con el hardware está simulada en esta unidad (Barrera.kt).



Funcionalidades

Registro, login y recuperación de contraseña

Abrir y cerrar la barrera, con cierre automático a los 5 segundos

Estado sincronizado en tiempo real entre celulares

Acceso con tarjeta (simulado): solo abre si está registrada y activa

Gestión de tarjetas: agregar, pausar, eliminar

Historial de eventos

Cierre de sesión por inactividad (15 min)

Herramientas

Kotlin + Android Studio: lenguaje oficial para Android

Firebase Authentication: manejo seguro de credenciales

Cloud Firestore: base de datos en la nube con tiempo real entre dispositivos

Bluetooth (HC-05): corto alcance y bajo consumo

Seguridad (ISO 27400)

Credenciales gestionadas por Firebase Auth

Reglas de Firestore (firestore.rules): solo usuarios con sesión, historial no modificable

Conexión HTTPS/TLS

Cierre de sesión por inactividad

Permisos: INTERNET y BLUETOOTH\_CONNECT

Cómo ejecutar

Abrir en Android Studio

Publicar firestore.rules en Firebase Console

Ejecutar en dos dispositivos con cuentas distintas

