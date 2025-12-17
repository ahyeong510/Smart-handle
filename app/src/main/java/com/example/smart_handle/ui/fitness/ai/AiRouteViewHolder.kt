package com.example.smart_handle.ui.fitness.ai

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R

class AiRouteViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    val tvTitle: TextView = view.findViewById(R.id.tvTitle)
    val tvInfo: TextView = view.findViewById(R.id.tvInfo)
}
