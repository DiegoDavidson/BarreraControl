package com.example.barrera

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

// Tiempo sin tocar la pantalla antes de cerrar la sesión (15 minutos).
// Para probar, cámbialo temporalmente a 10_000L (10 segundos).
const val INACTIVIDAD_MS = 10_000L

// Pantalla base: cierra la sesión si el usuario deja de usar la app
open class ActividadSegura : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val alExpirar = Runnable { cerrarSesion() }

    companion object {
        private var ultimaActividad = System.currentTimeMillis()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ultimaActividad = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        // Si la app estuvo en segundo plano más del tiempo permitido, cierra la sesión
        if (System.currentTimeMillis() - ultimaActividad > INACTIVIDAD_MS) {
            cerrarSesion()
        } else {
            reiniciar()
        }
    }

    // Se ejecuta cada vez que el usuario toca la pantalla
    override fun onUserInteraction() {
        super.onUserInteraction()
        reiniciar()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(alExpirar)
    }

    private fun reiniciar() {
        ultimaActividad = System.currentTimeMillis()
        handler.removeCallbacks(alExpirar)
        handler.postDelayed(alExpirar, INACTIVIDAD_MS)
    }

    private fun cerrarSesion() {
        Barrera.desconectar()
        FirebaseAuth.getInstance().signOut()
        Toast.makeText(this, "Sesión cerrada por inactividad", Toast.LENGTH_LONG).show()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}