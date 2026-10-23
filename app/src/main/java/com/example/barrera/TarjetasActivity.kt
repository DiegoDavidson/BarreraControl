package com.example.barrera

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class TarjetasActivity : ActividadSegura() {

    private val tarjetas = FirebaseFirestore.getInstance().collection("tarjetas")
    private var registro: ListenerRegistration? = null
    private var docs: List<DocumentSnapshot> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tarjetas)

        val lista = findViewById<ListView>(R.id.lvTarjetas)
        val btnAgregar = findViewById<Button>(R.id.btnAgregar)

        findViewById<Button>(R.id.btnVolver).setOnClickListener {
            finish()
        }

        // La lista se actualiza sola cuando cambia algo en la nube
        registro = tarjetas.orderBy("nombre").addSnapshotListener { datos, _ ->
            docs = datos?.documents ?: emptyList()
            val filas = docs.map { doc ->
                val estado = if (doc.getBoolean("activa") == true) "Activa" else "Pausada"
                "${doc.getString("nombre")}\n${doc.getString("uid")} · $estado"
            }
            lista.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filas)
        }

        // Tocar una tarjeta la activa o la pausa
        lista.setOnItemClickListener { _, _, posicion, _ ->
            val doc = docs[posicion]
            doc.reference.update("activa", doc.getBoolean("activa") != true)
        }

        // Mantener presionada una tarjeta la elimina
        lista.setOnItemLongClickListener { _, _, posicion, _ ->
            docs[posicion].reference.delete()
            true
        }

        btnAgregar.setOnClickListener {
            btnAgregar.isEnabled = false
            btnAgregar.text = "Acerca la tarjeta al lector..."

            Barrera.leerTarjeta { uid ->
                if (isDestroyed) return@leerTarjeta
                btnAgregar.isEnabled = true
                btnAgregar.text = "Agregar tarjeta"
                pedirNombre(uid)
            }
        }
    }

    private fun pedirNombre(uid: String) {
        val campo = EditText(this)
        campo.hint = "Nombre (ej: Llavero 1)"

        AlertDialog.Builder(this)
            .setTitle("Tarjeta detectada: $uid")
            .setView(campo)
            .setPositiveButton("Guardar") { _, _ ->
                val nombre = campo.text.toString().trim().ifEmpty { "Tarjeta" }
                tarjetas.add(mapOf("nombre" to nombre, "uid" to uid, "activa" to true))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroy() {
        registro?.remove()
        super.onDestroy()
    }
}