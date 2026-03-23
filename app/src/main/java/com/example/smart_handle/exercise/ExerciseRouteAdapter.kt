package com.example.smart_handle.exercise

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R

class ExerciseRouteAdapter(
    private val routes: List<ExerciseRouteCandidate>,
    private val onClick: (ExerciseRouteCandidate) -> Unit
) : RecyclerView.Adapter<ExerciseRouteAdapter.RouteViewHolder>() {

    inner class RouteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRouteTitle: TextView = view.findViewById(R.id.tvRouteTitle)
        val tvRouteDescription: TextView = view.findViewById(R.id.tvRouteDescription)
        val tvRouteInfo: TextView = view.findViewById(R.id.tvRouteInfo)

        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onClick(routes[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_candidate, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]

        holder.tvRouteTitle.text = route.title
        holder.tvRouteDescription.text = route.description
        holder.tvRouteInfo.text =
            "거리 %.1fkm | 예상 %d분 | 회전 %d회"
                .format(route.distanceKm, route.estimatedTimeMin, route.turnCount)
    }

    override fun getItemCount(): Int = routes.size
}