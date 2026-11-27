package com.example.barrera

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : ActividadSegura() {

    private var registro: ListenerRegistration? = null
    private lateinit var btnToggle: Button
    private lateinit var btnSimular: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val usuario = FirebaseAuth.getInstance().currentUser
        findViewById<TextView>(R.id.tvBienvenida).text =
            "Hola, ${usuario?.displayName ?: usuario?.email}"

        val tvEstado = findViewById<TextView>(R.id.tvEstado)
        btnToggle = findViewById(R.id.btnToggle)
        btnSimular = findViewById(R.id.btnSimular)
        var abierta = false

        // El botón de simular solo existe mientras el lector sea simulado
        if (!LECTOR_SIMULADO) btnSimular.visibility = View.GONE

        // Se ejecuta cada vez que cambia el estado, también desde el otro celular
        registro = Barrera.estado.addSnapshotListener { doc, _ ->
            abierta = doc?.getBoolean("abierta") ?: false
            if (abierta) {
                tvEstado.text = "ABIERTA"
                pintarEstado(tvEstado, R.color.estado_ok)
                btnToggle.text = "Cerrar barrera"
            } else {
                tvEstado.text = "CERRADA"
                pintarEstado(tvEstado, R.color.estado_error)
                btnToggle.text = "Abrir barrera"
            }
        }

        btnToggle.setOnClickListener {
            Barrera.enviar(if (abierta) "CERRAR" else "ABRIR")
        }

        btnSimular.setOnClickListener {
            simularTarjeta()
        }

        configurarBarra(R.id.menuPanel)
    }

    private fun pintarEstado(tv: TextView, colorRes: Int) {
        val c = getColor(colorRes)
        tv.setTextColor(c)
        findViewById<View>(R.id.puntoEstado).backgroundTintList = ColorStateList.valueOf(c)
    }

    // Elige qué tarjeta "acerca" el usuario al lector
    private fun simularTarjeta() {
        FirebaseFirestore.getInstance().collection("tarjetas").get()
            .addOnSuccessListener { datos ->
                val nombres = datos.documents.map { doc ->
                    val pausa = if (doc.getBoolean("activa") == true) "" else " (pausada)"
                    "${doc.getString("nombre")}$pausa"
                } + "Tarjeta desconocida"
                val uids = datos.documents.map { it.getString("uid") ?: "" } + "00 00 00 00"

                AlertDialog.Builder(this)
                    .setTitle("¿Qué tarjeta acerca al lector?")
                    .setItems(nombres.toTypedArray()) { _, i ->
                        // El UID viaja al Arduino y vuelve como TARJETA:<uid>
                        Barrera.simularTarjeta(uids[i])
                    }
                    .show()
            }
    }

    // Muestra si hay conexión o si el celular está en modo solo lectura,
    // y habilita o no los botones que mandan órdenes
    private fun actualizarConexion() {
        val tvConexion = findViewById<TextView>(R.id.tvConexion)
        tvConexion.text = when {
            Barrera.conectado -> "Conectado a ${Barrera.nombreDispositivo}"
            Barrera.nombreDispositivo != null -> "Reconectando..."
            else -> "Sin conexión (modo solo lectura)"
        }
        val colorPunto = when {
            Barrera.conectado -> R.color.estado_ok
            Barrera.nombreDispositivo != null -> R.color.estado_info
            else -> R.color.estado_neutro
        }
        findViewById<View>(R.id.puntoConexion).backgroundTintList =
            ColorStateList.valueOf(getColor(colorPunto))
        btnToggle.isEnabled = Barrera.conectado
        btnSimular.isEnabled = Barrera.conectado
        actualizarSensor()
    }

    private fun actualizarSensor() {
        findViewById<TextView>(R.id.tvSensor).text = when {
            !Barrera.conectado -> ""
            Barrera.distanciaCm == 0 -> "Sensor: sin lectura"
            else -> "Sensor: ${Barrera.distanciaCm} cm"
        }
    }

    // Mientras la pantalla se ve, recibe los avisos de Barrera
    override fun onStart() {
        super.onStart()
        Barrera.alCambiarConexion = { actualizarConexion() }
        Barrera.alCambiarDistancia = { actualizarSensor() }
        Barrera.alCambiarSituacion = { texto ->
            findViewById<TextView>(R.id.tvSituacion).text = texto
        }
        actualizarConexion()
    }

    override fun onStop() {
        Barrera.alCambiarConexion = null
        Barrera.alCambiarDistancia = null
        Barrera.alCambiarSituacion = null
        super.onStop()
    }

    override fun onDestroy() {
        registro?.remove()
        super.onDestroy()
    }
}