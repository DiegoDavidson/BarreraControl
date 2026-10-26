package com.example.barrera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

class ConexionActivity : ActividadSegura() {

    private lateinit var tvEstado: TextView
    private lateinit var btnConectar: Button
    private lateinit var btnPanel: Button

    // Respuesta del usuario al permiso de Bluetooth (Android 12 o superior)
    private val pedirPermiso = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            iniciarConexion()
        } else {
            Toast.makeText(this, "Se necesita el permiso de Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conexion)

        tvEstado = findViewById(R.id.tvEstado)
        btnConectar = findViewById(R.id.btnConectar)
        btnPanel = findViewById(R.id.btnPanel)

        btnConectar.setOnClickListener { conectar() }

        btnPanel.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun conectar() {
        val faltaPermiso = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED

        if (faltaPermiso) {
            pedirPermiso.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            iniciarConexion()
        }
    }

    // La conexión sigue simulada; más adelante aquí va el BluetoothSocket
    private fun iniciarConexion() {
        btnConectar.isEnabled = false
        tvEstado.text = "Conectando..."

        Barrera.conectar("HC-05 · Barrera 1") {
            tvEstado.text = "Conectado a ${Barrera.dispositivo}"
            btnPanel.isEnabled = true
        }
    }
}
