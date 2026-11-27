package com.example.barrera

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si ya inició sesión antes, pasa directo
        if (auth.currentUser != null) {
            irAlInicio()
            return
        }

        setContentView(R.layout.activity_login)

        val etCorreo = findViewById<EditText>(R.id.etCorreo)
        val etClave = findViewById<EditText>(R.id.etClave)

        findViewById<Button>(R.id.btnIngresar).setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val clave = etClave.text.toString()

            if (correo.isEmpty() || clave.isEmpty()) {
                Toast.makeText(this, "Completa correo y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(correo, clave)
                .addOnSuccessListener { irAlInicio() }
                .addOnFailureListener {
                    Toast.makeText(this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                }
        }

        findViewById<TextView>(R.id.tvOlvide).setOnClickListener {
            // Diálogo propio, con el correo ya escrito si lo había puesto arriba
            val campo = EditText(this)
            campo.hint = "Correo"
            campo.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            campo.setText(etCorreo.text.toString().trim())

            val dialogo = AlertDialog.Builder(this)
                .setTitle("Recuperar contraseña")
                .setMessage("Escribe tu correo y te enviaremos un enlace para crear una contraseña nueva.")
                .setView(campo)
                .setPositiveButton("Enviar enlace", null)
                .setNegativeButton("Cancelar", null)
                .create()
            dialogo.show()

            // Se asigna aparte para que el diálogo no se cierre si el correo es inválido
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val correo = campo.text.toString().trim()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                    campo.error = "Correo no válido"
                    return@setOnClickListener
                }
                auth.sendPasswordResetEmail(correo)
                // Mismo mensaje exista o no la cuenta, para no revelar quién está registrado
                dialogo.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Revisa tu correo")
                    .setMessage("Si $correo tiene una cuenta, te enviamos un enlace para cambiar la contraseña. Mira también la carpeta de spam.")
                    .setPositiveButton("Entendido", null)
                    .show()
            }
        }

        findViewById<TextView>(R.id.tvRegistro).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun irAlInicio() {
        startActivity(Intent(this, ConexionActivity::class.java))
        finish()
    }
}