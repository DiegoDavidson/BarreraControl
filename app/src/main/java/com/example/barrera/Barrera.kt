package com.example.barrera

import android.os.Handler
import android.os.Looper
import android.util.Log

// Conexión SIMULADA con la barrera.
// Cuando llegue el HC-05, solo se cambia el contenido de este archivo.
object Barrera {

    var dispositivo: String? = null
        private set

    var abierta = false
        private set

    val conectado: Boolean
        get() = dispositivo != null

    fun conectar(nombre: String, alTerminar: () -> Unit) {
        // Simula los 2 segundos que tarda en vincularse el Bluetooth
        Handler(Looper.getMainLooper()).postDelayed({
            dispositivo = nombre
            alTerminar()
        }, 2000)
    }

    fun desconectar() {
        dispositivo = null
        abierta = false
    }

    fun enviar(comando: String) {
        // Más adelante: escribir el comando en el socket Bluetooth
        Log.d("Barrera", "Comando enviado: $comando")
        if (comando == "ABRIR") abierta = true
        if (comando == "CERRAR") abierta = false
    }
}