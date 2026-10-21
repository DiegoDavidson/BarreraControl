package com.example.barrera

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ConexionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conexion)

        val tvEstado = findViewById<TextView>(R.id.tvEstado)
        val btnConectar = findViewById<Button>(R.id.btnConectar)
        val btnPanel = findViewById<Button>(R.id.btnPanel)

        btnConectar.setOnClickListener {
            btnConectar.isEnabled = false
            tvEstado.text = "Conectando..."

            Barrera.conectar("HC-05 · Barrera 1") {
                tvEstado.text = "Conectado a ${Barrera.dispositivo}"
                btnPanel.isEnabled = true
            }
        }

        btnPanel.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}