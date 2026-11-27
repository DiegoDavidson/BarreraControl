package com.example.barrera

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class TarjetasActivity : ActividadSegura() {

    private val tarjetas = FirebaseFirestore.getInstance().collection("tarjetas")
    private var registro: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tarjetas)

        val lista = findViewById<ListView>(R.id.lvTarjetas)
        val vacio = findViewById<TextView>(R.id.tvVacio)
        val btnAgregar = findViewById<Button>(R.id.btnAgregar)

        configurarBarra(R.id.menuTarjetas)

        val adaptador = AdaptadorTarjetas(this, ::editar, ::eliminar)
        lista.adapter = adaptador

        // La lista se actualiza sola cuando cambia algo en la nube
        registro = tarjetas.orderBy("nombre").addSnapshotListener { datos, _ ->
            adaptador.clear()
            adaptador.addAll(datos?.documents ?: emptyList())
            vacio.visibility = if (adaptador.isEmpty) View.VISIBLE else View.GONE
        }

        btnAgregar.setOnClickListener {
            btnAgregar.isEnabled = false
            btnAgregar.text = "Acerca la tarjeta al lector..."

            Barrera.leerTarjeta { uid ->
                if (isDestroyed) return@leerTarjeta
                btnAgregar.isEnabled = true
                btnAgregar.text = "Agregar tarjeta"
                if (uid == null) {
                    // No llegó nada: sin conexión o el Arduino no respondió en 15 s
                    Toast.makeText(this, "No se leyó ninguna tarjeta. Revisa la conexión con la barrera", Toast.LENGTH_LONG).show()
                } else {
                    pedirNombre(uid)
                }
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

    // Cambia el nombre de una tarjeta
    private fun editar(doc: DocumentSnapshot) {
        val campo = EditText(this)
        campo.setText(doc.getString("nombre"))

        AlertDialog.Builder(this)
            .setTitle("Editar nombre")
            .setView(campo)
            .setPositiveButton("Guardar") { _, _ ->
                val nombre = campo.text.toString().trim()
                if (nombre.isNotEmpty()) doc.reference.update("nombre", nombre)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminar(doc: DocumentSnapshot) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar tarjeta")
            .setMessage("¿Eliminar \"${doc.getString("nombre")}\"? Ya no podrá abrir la barrera.")
            .setPositiveButton("Eliminar") { _, _ -> doc.reference.delete() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroy() {
        registro?.remove()
        super.onDestroy()
    }

    // Una tarjeta por registro, con interruptor activa/pausada y botones Editar y Eliminar
    private class AdaptadorTarjetas(
        contexto: Context,
        private val alEditar: (DocumentSnapshot) -> Unit,
        private val alEliminar: (DocumentSnapshot) -> Unit
    ) : ArrayAdapter<DocumentSnapshot>(contexto, 0) {

        override fun getView(posicion: Int, vista: View?, padre: ViewGroup): View {
            val fila = vista ?: LayoutInflater.from(context).inflate(R.layout.item_tarjeta, padre, false)
            val doc = getItem(posicion)!!
            val activa = doc.getBoolean("activa") == true

            fila.findViewById<TextView>(R.id.tvNombre).text = doc.getString("nombre")
            fila.findViewById<TextView>(R.id.tvUid).text = doc.getString("uid")

            val estado = fila.findViewById<TextView>(R.id.tvEstadoTarjeta)
            estado.text = if (activa) "Activa" else "Pausada"
            val c = Color.parseColor(if (activa) "#1F7A4D" else "#B42318")
            estado.setTextColor(c)
            estado.background.mutate().setTint(Color.argb(40, Color.red(c), Color.green(c), Color.blue(c)))
            fila.findViewById<View>(R.id.vFranja).setBackgroundColor(c)

            // Se quita el listener antes de marcar, porque la fila se reutiliza al hacer scroll
            val interruptor = fila.findViewById<MaterialSwitch>(R.id.swActiva)
            interruptor.setOnCheckedChangeListener(null)
            interruptor.isChecked = activa
            interruptor.setOnCheckedChangeListener { _, marcado -> doc.reference.update("activa", marcado) }

            fila.findViewById<View>(R.id.btnEditar).setOnClickListener { alEditar(doc) }
            fila.findViewById<View>(R.id.btnEliminar).setOnClickListener { alEliminar(doc) }
            return fila
        }
    }
}
