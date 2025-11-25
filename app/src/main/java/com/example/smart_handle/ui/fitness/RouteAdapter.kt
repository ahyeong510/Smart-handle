package com.example.smart_handle.ui.fitness

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions

/**
 * 운동 코스 추천 리스트 어댑터
 * - item_fitness_route.xml 사용
 * - ViewBinding / DataBinding 사용 X (findViewById만 사용)
 */
class RouteAdapter(
    private val items: List<RouteModel>,
    private val onSelected: (RouteModel) -> Unit
) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

    private var selectedId: Int? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fitness_route, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, item.id == selectedId)

        holder.itemView.setOnClickListener {
            selectedId = item.id
            notifyDataSetChanged()
            onSelected(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class RouteViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView), OnMapReadyCallback {

        private val cardView: CardView = itemView.findViewById(R.id.cardView)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvLevel: TextView = itemView.findViewById(R.id.tvLevel)
        private val mapView: MapView = itemView.findViewById(R.id.mapView)

        private var googleMap: GoogleMap? = null
        private var route: RouteModel? = null

        init {
            // 간단한 미니맵이니까 라이프사이클 단순 처리
            mapView.onCreate(null)
            mapView.getMapAsync(this)
        }

        fun bind(item: RouteModel, isSelected: Boolean) {
            route = item

            tvTitle.text = item.title
            tvDistance.text = "${item.distanceKm} km"
            tvTime.text = "${item.timeMin} 분"


            // 선택된 카드 시각 효과 (배경색만 간단하게)
            val bgColor = if (isSelected) 0xFFE3F2FD.toInt() else 0xFFFFFFFF.toInt()
            cardView.setCardBackgroundColor(bgColor)

            // 지도 위 경로 갱신
            googleMap?.let { drawRoute(it, item) }
        }

        override fun onMapReady(map: GoogleMap) {
            googleMap = map
            route?.let { drawRoute(map, it) }
        }

        private fun drawRoute(map: GoogleMap, route: RouteModel) {
            val pts = route.path
            if (pts.isEmpty()) return

            map.clear()
            map.uiSettings.apply {
                isZoomGesturesEnabled = false
                isScrollGesturesEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }

            map.addPolyline(
                PolylineOptions()
                    .addAll(pts)
            )

            val bounds = LatLngBounds.Builder().apply {
                pts.forEach { include(it) }
            }.build()

            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 40))
        }
    }
}
