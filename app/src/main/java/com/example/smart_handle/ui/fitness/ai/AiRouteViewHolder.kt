package com.example.smart_handle.ui.fitness.ai

import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.databinding.ItemAiRouteBinding
import com.example.smart_handle.ui.fitness.model.AiRoute

class AiRouteViewHolder(
    private val binding: ItemAiRouteBinding,
    private val onClick: (Int) -> Unit    // 🔑 routeId만 전달
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(route: AiRoute) {

        binding.tvTitle.text = "운동 코스 ${route.id}"
        binding.tvInfo.text =
            "${"%.1f".format(route.distanceKm)} km · 약 ${route.durationMin} 분"

        binding.root.setOnClickListener {
            onClick(route.id)   // 🔑 여기 중요
        }
    }
}
