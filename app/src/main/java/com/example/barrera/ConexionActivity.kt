package com.example.barrera

import android.Manifest
import android.bluetooth.BluetoothManager
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

        // Sin conexión: el panel solo muestra el estado que llega por Firestore
        findViewById<Button>(R.id.btnSoloLectura).setOnClickListener {
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

    private fun iniciarConexion() {
        // El Bluetooth tiene que estar encendido
        val adaptador = getSystemService(BluetoothManager::class.java)?.adapter
        if (adaptador == null || !adaptador.isEnabled) {
            tvEstado.text = "Enciende el Bluetooth del celular"
            return
        }

        // El módulo es BLE: no hace falta emparejarlo, se conecta directo por su dirección MAC
        val modulo = adaptador.getRemoteDevice(MAC_HC05)

        btnConectar.isEnabled = false
        tvEstado.text = "Conectando..."

        Barrera.conectar(this, modulo) { exito ->
            btnConectar.isEnabled = true
            if (exito) {
                // Conectado: aviso y directo al panel
                Toast.makeText(this, "Conectado a ${Barrera.nombreDispositivo ?: "HC-05"}", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                tvEstado.text = "Error: no se pudo conectar (${Barrera.ultimoError}). ¿Está encendido el Arduino y cerrada la otra app?"
            }
        }
    }
}
