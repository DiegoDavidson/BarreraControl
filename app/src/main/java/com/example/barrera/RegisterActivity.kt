package com.example.barrera

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest

class RegisterActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etNombre = findViewById<EditText>(R.id.etNombre)
        val etCorreo = findViewById<EditText>(R.id.etCorreo)
        val etClave = findViewById<EditText>(R.id.etClave)
        val etClave2 = findViewById<EditText>(R.id.etClave2)

        findViewById<Button>(R.id.btnCrear).setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val correo = etCorreo.text.toString().trim()
            val clave = etClave.text.toString()
            val clave2 = etClave2.text.toString()

            if (nombre.isEmpty() || correo.isEmpty() || clave.isEmpty()) {
                mostrar("Completa todos los campos")
                return@setOnClickListener
            }
            val problema = errorDeClave(clave, correo)
            if (problema != null) {
                mostrar(problema)
                return@setOnClickListener
            }
            if (clave != clave2) {
                mostrar("Las contraseñas no coinciden")
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(correo, clave)
                .addOnSuccessListener { resultado ->
                    // Guarda el nombre en el perfil del usuario
                    val cambios = UserProfileChangeRequest.Builder().setDisplayName(nombre).build()
                    resultado.user?.updateProfile(cambios)

                    startActivity(Intent(this, ConexionActivity::class.java))
                    finishAffinity()
                }
                .addOnFailureListener { error ->
                    if (error is FirebaseAuthUserCollisionException) {
                        mostrar("Ese correo ya tiene una cuenta")
                    } else {
                        mostrar("Error: ${error.message}")
                    }
                }
        }
    }

    // Devuelve el primer requisito que no cumple la contraseña, o null si es válida
    private fun errorDeClave(clave: String, correo: String): String? {
        val usuarioCorreo = correo.substringBefore("@")
        return when {
            clave.length < 8 -> "La contraseña debe tener al menos 8 caracteres"
            clave.any { it.isWhitespace() } -> "La contraseña no puede tener espacios"
            clave.none { it.isUpperCase() } -> "La contraseña debe tener una mayúscula"
            clave.none { it.isLowerCase() } -> "La contraseña debe tener una minúscula"
            clave.none { it.isDigit() } -> "La contraseña debe tener un número"
            clave.all { it.isLetterOrDigit() } -> "La contraseña debe tener un símbolo (ej: ! # $ %)"
            usuarioCorreo.length >= 3 && clave.contains(usuarioCorreo, ignoreCase = true) ->
                "La contraseña no puede contener tu correo"
            else -> null
        }
    }

    private fun mostrar(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}