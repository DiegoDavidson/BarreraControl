package com.example.barrera

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ImageView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class HistorialActivity : ActividadSegura() {

    private var registro: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        configurarBarra(R.id.menuHistorial)

        val lista = findViewById<ListView>(R.id.lvEventos)
        val vacio = findViewById<TextView>(R.id.tvVacio)
        val adaptador = AdaptadorEventos(this)
        lista.adapter = adaptador

        // Se actualiza solo cuando llega un evento nuevo
        registro = FirebaseFirestore.getInstance().collection("eventos")
            .orderBy("hora", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { datos, _ ->
                adaptador.clear()
                adaptador.addAll(datos?.documents ?: emptyList())
                vacio.visibility = if (adaptador.isEmpty) View.VISIBLE else View.GONE
            }
    }

    override fun onDestroy() {
        registro?.remove()
        super.onDestroy()
    }

    // Una tarjeta por evento: punto de color según el tipo, detalle y hora a la derecha
    private class AdaptadorEventos(contexto: Context) : ArrayAdapter<DocumentSnapshot>(contexto, 0) {
        private val formato = SimpleDateFormat("dd/MM\nHH:mm:ss", Locale.getDefault())

        override fun getView(posicion: Int, vista: View?, padre: ViewGroup): View {
            val fila = vista ?: LayoutInflater.from(context).inflate(R.layout.item_evento, padre, false)
            val doc = getItem(posicion)!!
            val tipo = doc.getString("tipo") ?: ""

            fila.findViewById<TextView>(R.id.tvTipo).text = tipo
            fila.findViewById<TextView>(R.id.tvQuien).text = doc.getString("usuario")
            fila.findViewById<TextView>(R.id.tvHora).text =
                doc.getTimestamp("hora")?.toDate()?.let { formato.format(it) } ?: "..."

            val (color, icono) = when {
                tipo.startsWith("Entrada") -> "#1F7A4D" to R.drawable.ic_ev_entrada   // verde: acceso autorizado
                tipo.startsWith("Acceso denegado") -> "#B42318" to R.drawable.ic_ev_denegado // rojo
                tipo.contains("manual") -> "#1D4ED8" to R.drawable.ic_ev_manual       // azul: acción del usuario
                else -> "#667085" to R.drawable.ic_ev_auto                            // gris: cierre automático
            }
            val c = Color.parseColor(color)
            val circulo = fila.findViewById<ImageView>(R.id.punto)
            circulo.setImageResource(icono)
            circulo.imageTintList = ColorStateList.valueOf(c)
            circulo.background.mutate().setTint(Color.argb(40, Color.red(c), Color.green(c), Color.blue(c)))
            return fila
        }
    }
}
