package com.example.smart_handle.ui.fitness.ai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R
import com.example.smart_handle.ui.fitness.model.AiRoute

class AiRouteAdapter(
    private val routes: List<AiRoute>,
    private val onClick: (AiRoute) -> Unit
) : RecyclerView.Adapter<AiRouteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AiRouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_route, parent, false)
        return AiRouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: AiRouteViewHolder, position: Int) {
        val route = routes[position]

        holder.tvTitle.text = "운동 코스 ${position + 1}"
        holder.tvInfo.text =
            "${"%.1f".format(route.distanceKm)} km · 약 ${route.durationMin} 분"

        holder.itemView.setOnClickListener {
            onClick(route)
        }
    }

    override fun getItemCount(): Int = routes.size
}
