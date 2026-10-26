package com.example.barrera

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// Conexión SIMULADA con la barrera y el lector de tarjetas.
// Cuando lleguen el HC-05 y el RC522, se cambia el contenido de este archivo.
object Barrera {

    private val db = FirebaseFirestore.getInstance()

    // Documento con el estado actual, compartido entre los celulares
    val estado = db.collection("barrera").document("barrera1")

    var dispositivo: String? = null
        private set

    fun conectar(nombre: String, alTerminar: () -> Unit) {
        // Simula los 2 segundos que tarda en vincularse el Bluetooth
        Handler(Looper.getMainLooper()).postDelayed({
            dispositivo = nombre
            alTerminar()
        }, 2000)
    }

    fun desconectar() {
        dispositivo = null
    }

    // Simula que el lector RC522 detecta una tarjeta nueva (devuelve su UID)
    fun leerTarjeta(alTerminar: (String) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            val uid = (1..4).joinToString(" ") { "%02X".format((0..255).random()) }
            alTerminar(uid)
        }, 2000)
    }

    // La barrera real baja sola después de unos segundos, se abra como se abra
    private const val TIEMPO_ABIERTA_MS = 5000L
    private val reloj = Handler(Looper.getMainLooper())
    private val cierreAutomatico = Runnable {
        enviar("CERRAR", "Cierre automático", "Barrera")
    }

    fun enviar(comando: String, tipo: String? = null, quien: String? = null) {
        // Más adelante: escribir el comando en el socket Bluetooth
        Log.d("Barrera", "Comando enviado: $comando")

        val abrir = comando == "ABRIR"
        estado.set(mapOf("abierta" to abrir))
        registrar(
            tipo ?: if (abrir) "Apertura manual" else "Cierre manual",
            quien ?: nombreUsuario()
        )

        // Cada orden reinicia la cuenta: un cierre manual cancela el cierre
        // pendiente y una nueva apertura vuelve a contar desde cero
        reloj.removeCallbacks(cierreAutomatico)
        if (abrir) reloj.postDelayed(cierreAutomatico, TIEMPO_ABIERTA_MS)
    }

    // Revisa si la tarjeta está registrada y activa
    fun usarTarjeta(uid: String, alTerminar: (Boolean) -> Unit) {
        db.collection("tarjetas").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { resultado ->
                val tarjeta = resultado.documents.firstOrNull()
                val autorizada = tarjeta?.getBoolean("activa") == true

                if (autorizada) {
                    enviar("ABRIR", "Entrada autorizada", "${tarjeta?.getString("nombre")} ($uid)")
                } else {
                    registrar("Acceso denegado", "Tarjeta $uid")
                }
                alTerminar(autorizada)
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