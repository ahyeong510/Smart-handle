package com.example.smart_handle.ui.fitness.ai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.databinding.ItemAiRouteBinding
import com.example.smart_handle.ui.fitness.model.AiRoute

class AiRouteAdapter(
    private val onClick: (Int) -> Unit   // 🔑 routeId만 전달
) : RecyclerView.Adapter<AiRouteViewHolder>() {

    private val items = mutableListOf<AiRoute>()

    fun submit(list: List<AiRoute>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AiRouteViewHolder {
        val binding = ItemAiRouteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AiRouteViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: AiRouteViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
