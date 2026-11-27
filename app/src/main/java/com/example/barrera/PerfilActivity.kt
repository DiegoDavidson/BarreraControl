package com.example.barrera

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.EmailAuthProvider
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest

class PerfilActivity : ActividadSegura() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        val usuario = FirebaseAuth.getInstance().currentUser
        val correo = usuario?.email ?: ""
        val nombreInicial = usuario?.displayName?.takeIf { it.isNotBlank() } ?: correo
        findViewById<TextView>(R.id.tvNombre).text = nombreInicial
        findViewById<TextView>(R.id.tvCorreo).text = correo
        ponerInicial(nombreInicial)

        val etActual = findViewById<EditText>(R.id.etActual)
        val etNueva = findViewById<EditText>(R.id.etNueva)

        findViewById<Button>(R.id.btnCambiar).setOnClickListener {
            val actual = etActual.text.toString()
            val nueva = etNueva.text.toString()
            val error = errorDeClave(nueva, correo)
            when {
                usuario == null -> return@setOnClickListener
                actual.isEmpty() -> mostrar("Escribe tu contraseña actual")
                error != null -> mostrar(error)
                // Firebase pide volver a autenticar antes de cambiar la contraseña
                else -> usuario.reauthenticate(EmailAuthProvider.getCredential(correo, actual))
                    .addOnSuccessListener {
                        usuario.updatePassword(nueva)
                            .addOnSuccessListener {
                                etActual.text.clear()
                                etNueva.text.clear()
                                mostrar("Contraseña actualizada")
                            }
                            .addOnFailureListener { mostrar("No se pudo cambiar la contraseña") }
                    }
                    .addOnFailureListener { mostrar("La contraseña actual es incorrecta") }
            }
        }

        // UPDATE: cambiar el nombre que se muestra
        findViewById<Button>(R.id.btnNombre).setOnClickListener {
            val campo = EditText(this)
            campo.setText(usuario?.displayName)
            AlertDialog.Builder(this)
                .setTitle("Editar nombre")
                .setView(campo)
                .setPositiveButton("Guardar") { _, _ ->
                    val nombre = campo.text.toString().trim()
                    if (nombre.isEmpty()) return@setPositiveButton
                    usuario?.updateProfile(userProfileChangeRequest { displayName = nombre })
                        ?.addOnSuccessListener {
                            findViewById<TextView>(R.id.tvNombre).text = nombre
                            ponerInicial(nombre)
                            mostrar("Nombre actualizado")
                        }
                        ?.addOnFailureListener { mostrar("No se pudo actualizar el nombre") }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // DELETE: eliminar la cuenta (pide la contraseña para confirmar)
        findViewById<Button>(R.id.btnEliminarCuenta).setOnClickListener {
            val campo = EditText(this)
            campo.hint = "Tu contraseña"
            campo.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            AlertDialog.Builder(this)
                .setTitle("Eliminar cuenta")
                .setMessage("Esta acción no se puede deshacer. Escribe tu contraseña para confirmar.")
                .setView(campo)
                .setPositiveButton("Eliminar") { _, _ ->
                    val clave = campo.text.toString()
                    if (usuario == null || clave.isEmpty()) return@setPositiveButton
                    usuario.reauthenticate(EmailAuthProvider.getCredential(correo, clave))
                        .addOnSuccessListener {
                            usuario.delete()
                                .addOnSuccessListener { salir("Cuenta eliminada") }
                                .addOnFailureListener { mostrar("No se pudo eliminar la cuenta") }
                        }
                        .addOnFailureListener { mostrar("La contraseña es incorrecta") }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        findViewById<Button>(R.id.btnConexion).setOnClickListener {
            startActivity(Intent(this, ConexionActivity::class.java))
        }

        findViewById<Button>(R.id.btnSalir).setOnClickListener { salir("Sesión cerrada") }

        configurarBarra(R.id.menuPerfil)
    }

    private fun ponerInicial(nombre: String) {
        findViewById<TextView>(R.id.tvInicial).text = nombre.trim().take(1).uppercase()
    }

    private fun mostrar(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}
