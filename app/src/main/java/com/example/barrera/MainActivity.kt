package com.example.barrera

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val usuario = FirebaseAuth.getInstance().currentUser
        findViewById<TextView>(R.id.tvBienvenida).text =
            "Hola, ${usuario?.displayName ?: usuario?.email}"
        findViewById<TextView>(R.id.tvConexion).text =
            "Conectado a ${Barrera.dispositivo}"

        val tvEstado = findViewById<TextView>(R.id.tvEstado)
        val btnToggle = findViewById<Button>(R.id.btnToggle)
        var abierta = false

        // Se ejecuta cada vez que cambia el estado, también desde el otro celular
        registro = Barrera.estado.addSnapshotListener { doc, _ ->
            abierta = doc?.getBoolean("abierta") ?: false
            if (abierta) {
                tvEstado.text = "ABIERTA"
                tvEstado.setTextColor(Color.parseColor("#1F7A4D"))
                btnToggle.text = "Cerrar barrera"
            } else {
                tvEstado.text = "CERRADA"
                tvEstado.setTextColor(Color.parseColor("#B42318"))
                btnToggle.text = "Abrir barrera"
            }
        }

        btnToggle.setOnClickListener {
            Barrera.enviar(if (abierta) "CERRAR" else "ABRIR")
        }

        findViewById<Button>(R.id.btnSimular).setOnClickListener {
            simularTarjeta()
        }

        findViewById<Button>(R.id.btnHistorial).setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }

        findViewById<Button>(R.id.btnTarjetas).setOnClickListener {
            startActivity(Intent(this, TarjetasActivity::class.java))
        }

        findViewById<Button>(R.id.btnSalir).setOnClickListener {
            Barrera.desconectar()
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
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
                        Barrera.usarTarjeta(uids[i]) { autorizada ->
                            val mensaje = if (autorizada) "Acceso autorizado" else "Acceso denegado"
                            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
            }
    }

    override fun onDestroy() {
        registro?.remove()
        super.onDestroy()
    }
}