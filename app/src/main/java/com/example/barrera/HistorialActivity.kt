package com.example.barrera

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class HistorialActivity : AppCompatActivity() {

    private var registro: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        findViewById<Button>(R.id.btnVolver).setOnClickListener {
            finish()
        }

        val lista = findViewById<ListView>(R.id.lvEventos)
        val formato = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())

        // Se actualiza solo cuando llega un evento nuevo
        registro = FirebaseFirestore.getInstance().collection("eventos")
            .orderBy("hora", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { datos, _ ->
                val filas = datos?.documents?.map { doc ->
                    val hora = doc.getTimestamp("hora")?.toDate()
                    val texto = if (hora != null) formato.format(hora) else "..."
                    "${doc.getString("tipo")}\n${doc.getString("usuario")} · $texto"
                } ?: emptyList()

                lista.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filas)
            }
    }

    override fun onDestroy() {
        registro?.remove()
        super.onDestroy()
    }
}