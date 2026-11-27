package com.example.barrera

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

// Mientras no llegue el lector RC522 esto va en true: la app puede simular
// tarjetas mandando SIMULAR:<uid> al Arduino. Cuando llegue el RC522, cambiar a false.
const val LECTOR_SIMULADO = true

// Dirección MAC del módulo Bluetooth (viene grabada en el módulo y no cambia).
// Si algún día se usa otro módulo, hay que cambiarla aquí.
const val MAC_HC05 = "9C:F4:90:10:AD:31"

// Conexión Bluetooth de bajo consumo (BLE) con el módulo del Arduino.
// El módulo solo se conecta por BLE (por eso no aparece en los Ajustes de Bluetooth).
// El Arduino es quien manda: la app solo envía órdenes y reacciona a sus mensajes.
@SuppressLint("MissingPermission") // el permiso BLUETOOTH_CONNECT se pide en ConexionActivity
@Suppress("DEPRECATION")
object Barrera {

    private val db = FirebaseFirestore.getInstance()

    // Documento con el estado actual, compartido entre los celulares
    val estado = db.collection("barrera").document("barrera1")

    // Descriptor que hay que activar para que el módulo nos avise cuando manda datos
    private val UUID_NOTIFICACIONES = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val handler = Handler(Looper.getMainLooper())

    private var contexto: Context? = null
    private var dispositivo: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null
    private var canal: BluetoothGattCharacteristic? = null  // por aquí se escribe hacia el Arduino
    private var llegoAlgo = false  // true cuando el Arduino ya mandó algún mensaje
    private var desconectado = true

    // Mientras sea true, un fallo significa "no se pudo conectar"; después, "se perdió la conexión"
    private var primeraVez = true
    private var alTerminarConexion: ((Boolean) -> Unit)? = null

    // Texto que va llegando del Arduino (una línea puede llegar partida en pedazos)
    private val recibido = StringBuilder()

    // Cola de comandos: BLE solo deja escribir uno a la vez
    private val cola = ArrayDeque<String>()
    private var escribiendo = false

    // Nombre del módulo (null = el celular está en modo solo lectura)
    var nombreDispositivo: String? = null
        private set

    // true solo mientras hay conexión real con el Arduino
    var conectado = false
        private set

    // Canal que se está usando (para mostrarlo en pantalla)
    var canalUsado = ""
        private set

    // Último error al conectar, para mostrarlo en pantalla
    var ultimoError = ""
        private set

    // Las pantallas se enteran de los cambios con estas dos funciones (siempre en el hilo principal)
    var alCambiarSituacion: ((String) -> Unit)? = null
    var alCambiarConexion: (() -> Unit)? = null
    var alCambiarDistancia: (() -> Unit)? = null

    // Última distancia que midió el sensor ultrasónico (0 = sin eco), llega cada segundo
    var distanciaCm = 0
        private set

    // Esperando el UID de una tarjeta nueva (la pide TarjetasActivity)
    private var alLeerUid: ((String?) -> Unit)? = null
    private val tiempoAgotado = Runnable {
        val respuesta = alLeerUid
        alLeerUid = null
        respuesta?.invoke(null)
    }

    // ---------- Conexión ----------

    // Respuestas del Bluetooth. Llegan en otro hilo, así que todo se pasa al hilo principal.
    private val respuestas = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, nuevoEstado: Int) {
            if (nuevoEstado == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices() // busca los servicios del módulo
            } else {
                handler.post {
                    ultimoError = "se perdió la conexión (código $status)"
                    seCortoLaConexion(g)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val canales = buscarCanales(g)
            if (canales == null) {
                handler.post {
                    ultimoError = "no se encontró el canal serial del módulo"
                    seCortoLaConexion(g)
                }
                return
            }
            val avisos = canales.second
            canal = canales.first
            canalUsado = avisos.uuid.toString().substring(4, 8)

            // Activa los avisos de datos; cuando termina se llama onDescriptorWrite
            g.setCharacteristicNotification(avisos, true)
            val descriptor = avisos.getDescriptor(UUID_NOTIFICACIONES)
            if (descriptor == null) {
                handler.post { empezar(g) }
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(descriptor)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            handler.post { empezar(g) }
        }

        // Llegó un pedazo de texto del Arduino (Android 12 o menor)
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val datos = c.value ?: return
            val texto = String(datos)
            handler.post { recibirTexto(texto) }
        }

        // Lo mismo en Android 13 o superior, donde los datos vienen aparte
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, datos: ByteArray) {
            val texto = String(datos)
            handler.post { recibirTexto(texto) }
        }

        // Terminó de escribirse un comando: se puede mandar el siguiente
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            handler.post { terminoEscritura() }
        }
    }

    // Busca los canales para hablar con el Arduino: uno para escribir y otro que avise
    // cuando llegan datos. En los módulos tipo HM-10 es el mismo (FFE1); en otros son dos.
    private fun buscarCanales(g: BluetoothGatt):
        Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        var mejor: Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? = null

        for (servicio in g.services) {
            var escribe: BluetoothGattCharacteristic? = null
            var avisa: BluetoothGattCharacteristic? = null

            for (c in servicio.characteristics) {
                val p = c.properties
                val puedeAvisar = p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                val puedeEscribir = p and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

                if (puedeAvisar && puedeEscribir) {
                    escribe = c
                    avisa = c
                    break
                }
                if (puedeEscribir && escribe == null) escribe = c
                if (puedeAvisar && avisa == null) avisa = c
            }

            // Se prefiere el servicio FFE0, que es el de los módulos tipo HM-10
            if (escribe != null && avisa != null) {
                if (mejor == null || servicio.uuid.toString().startsWith("0000ffe0")) {
                    mejor = Pair(escribe, avisa)
                }
            }
        }
        return mejor
    }

    fun conectar(contextoApp: Context, modulo: BluetoothDevice, alTerminar: (Boolean) -> Unit) {
        contexto = contextoApp.applicationContext
        dispositivo = modulo
        desconectado = false
        primeraVez = true
        alTerminarConexion = alTerminar
        gatt?.close()
        gatt = modulo.connectGatt(contexto, false, respuestas)
    }

    // El canal ya está listo: desde aquí se puede hablar con el Arduino
    private fun empezar(g: BluetoothGatt) {
        if (g != gatt) return
        conectado = true
        llegoAlgo = false
        primeraVez = false
        nombreDispositivo = dispositivo?.name ?: "HC-05"
        alCambiarConexion?.invoke()
        alCambiarSituacion?.invoke("")
        alTerminarConexion?.invoke(true)
        alTerminarConexion = null
        enviar("ESTADO") // pregunta cómo está la barrera

        // Si en 3 segundos el Arduino no contestó nada, se avisa en pantalla
        handler.postDelayed({
            if (conectado && !llegoAlgo) {
                alCambiarSituacion?.invoke("El Arduino no responde. Revisa la velocidad del módulo")
            }
        }, 3000)
    }

    private fun seCortoLaConexion(g: BluetoothGatt) {
        // Si el usuario llamó desconectar(), este gatt ya no es el actual
        if (g != gatt) return
        g.close()
        gatt = null
        canal = null
        conectado = false
        cola.clear()
        escribiendo = false
        recibido.clear()

        if (primeraVez) {
            // Nunca llegó a conectar: se avisa el error y no se reintenta
            alTerminarConexion?.invoke(false)
            alTerminarConexion = null
            return
        }
        alCambiarConexion?.invoke()
        alCambiarSituacion?.invoke("Reconectando...")
        reintentar()
    }

    // Reintenta cada 3 segundos hasta conectar o hasta que el usuario desconecte
    private fun reintentar() {
        handler.postDelayed({
            if (desconectado) return@postDelayed
            gatt = dispositivo?.connectGatt(contexto, false, respuestas)
        }, 3000)
    }

    // Cierra la conexión y detiene los reintentos
    fun desconectar() {
        desconectado = true
        handler.removeCallbacksAndMessages(null)
        alLeerUid = null
        alTerminarConexion = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        canal = null
        dispositivo = null
        nombreDispositivo = null
        conectado = false
        cola.clear()
        escribiendo = false
        recibido.clear()
        alCambiarConexion?.invoke()
    }

    // ---------- Enviar y recibir ----------

    fun enviar(comando: String) {
        if (!conectado) return
        Log.d("Barrera", "Comando enviado: $comando")
        cola.addLast("$comando\n")
        escribirSiguiente()
    }

    private fun escribirSiguiente() {
        if (escribiendo) return
        val texto = cola.removeFirstOrNull() ?: return
        val c = canal ?: return
        escribiendo = true

        // Si el canal acepta escritura normal se usa esa; si no, la "sin respuesta"
        val normal = c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        c.writeType = if (normal) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        c.value = texto.toByteArray()

        if (gatt?.writeCharacteristic(c) != true) {
            terminoEscritura()
        } else {
            // Algunos celulares nunca avisan que terminó: a los 0,5 s se sigue igual
            handler.postDelayed(liberarEscritura, 500)
        }
    }

    // Se terminó de escribir un comando (o se dejó de esperar): sigue el próximo
    private fun terminoEscritura() {
        handler.removeCallbacks(liberarEscritura)
        escribiendo = false
        escribirSiguiente()
    }

    private val liberarEscritura = Runnable { terminoEscritura() }

    // Va juntando los pedazos hasta tener una línea completa (termina en \n)
    private fun recibirTexto(texto: String) {
        llegoAlgo = true
        recibido.append(texto)
        while (true) {
            val fin = recibido.indexOf("\n")
            if (fin < 0) break
            val linea = recibido.substring(0, fin).trim()
            recibido.delete(0, fin + 1)
            if (linea.isNotEmpty()) procesar(linea)
        }
    }

    // Solo mientras no esté el RC522: el Arduino hace como si el lector leyera esta tarjeta
    // y la respuesta vuelve como TARJETA:<uid>
    fun simularTarjeta(uid: String) {
        enviar("SIMULAR:$uid")
    }

    // Modo registro: pide al Arduino la próxima tarjeta. Responde null si no llega en 15 s
    fun leerTarjeta(alTerminar: (String?) -> Unit) {
        if (!conectado) {
            alTerminar(null)
            return
        }
        alLeerUid = alTerminar
        handler.removeCallbacks(tiempoAgotado)
        handler.postDelayed(tiempoAgotado, 15000)
        enviar("LEER_TARJETA")

        if (LECTOR_SIMULADO) {
            val uid = (1..4).joinToString(" ") { "%02X".format((0..255).random()) }
            simularTarjeta(uid)
        }
    }

    // ---------- Mensajes del Arduino ----------

    private fun procesar(msg: String) {
        Log.d("Barrera", "Mensaje recibido: $msg")

        when {
            msg.startsWith("DIST:") -> {
                distanciaCm = msg.substring(5).toIntOrNull() ?: 0
                alCambiarDistancia?.invoke()
            }

            msg == "AUTO" -> alCambiarSituacion?.invoke("Auto esperando tarjeta")
            msg == "AUTO_SE_FUE" -> alCambiarSituacion?.invoke("")

            msg.startsWith("TARJETA:") -> validarTarjeta(msg.substring(8))

            msg.startsWith("UID:") -> {
                handler.removeCallbacks(tiempoAgotado)
                val respuesta = alLeerUid
                alLeerUid = null
                respuesta?.invoke(msg.substring(4))
            }

            msg.startsWith("ABIERTA:") -> {
                estado.set(mapOf("abierta" to true))
                // ABIERTA:TARJETA ya quedó registrada como "Entrada autorizada"
                if (msg == "ABIERTA:APP") {
                    registrar("Apertura manual", nombreUsuario())
                    alCambiarSituacion?.invoke("")
                }
            }

            msg.startsWith("CERRADA:") -> {
                estado.set(mapOf("abierta" to false))
                if (msg == "CERRADA:APP") registrar("Cierre manual", nombreUsuario())
                if (msg == "CERRADA:AUTO") registrar("Cierre automático", "Barrera")
                alCambiarSituacion?.invoke("")
            }

            msg.startsWith("ESTADO:") -> {
                val nombre = msg.substring(7)
                estado.set(mapOf("abierta" to (nombre == "ABIERTA" || nombre == "POR_CERRAR")))
                if (nombre == "ESPERANDO") alCambiarSituacion?.invoke("Auto esperando tarjeta")
            }

            msg == "NO_SE_PUEDE_CERRAR" -> alCambiarSituacion?.invoke("No se puede cerrar: hay un auto")
        }
    }

    // Revisa si la tarjeta está registrada y activa, y le responde al Arduino
    private fun validarTarjeta(uid: String) {
        db.collection("tarjetas").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { resultado ->
                val tarjeta = resultado.documents.firstOrNull()

                if (tarjeta?.getBoolean("activa") == true) {
                    enviar("ABRIR_TARJETA")
                    registrar("Entrada autorizada", "${tarjeta.getString("nombre")} ($uid)")
                    alCambiarSituacion?.invoke("Acceso autorizado")
                } else {
                    enviar("DENEGADO")
                    registrar("Acceso denegado", "Tarjeta $uid")
                    alCambiarSituacion?.invoke("Acceso denegado")
                }
            }
            .addOnFailureListener {
                // Si no se puede consultar Firestore, mejor no abrir
                enviar("DENEGADO")
                alCambiarSituacion?.invoke("Acceso denegado")
            }
    }

    private fun registrar(tipo: String, quien: String) {
        db.collection("eventos").add(
            mapOf(
                "tipo" to tipo,
                "usuario" to quien,
                "hora" to FieldValue.serverTimestamp()
            )
        )
    }

    private fun nombreUsuario(): String {
        val usuario = FirebaseAuth.getInstance().currentUser
        return usuario?.displayName ?: usuario?.email ?: "desconocido"
    }
}
