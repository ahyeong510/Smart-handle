package com.example.bikenavi.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.smart_handle.R

class TourFragment : Fragment() {

    private lateinit var editText: EditText
    private lateinit var addButton: Button
    private lateinit var listContainer: LinearLayout
    private lateinit var generateButton: Button
    private val spots = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tour_route, container, false)

        editText = view.findViewById(R.id.editTextSpot)
        addButton = view.findViewById(R.id.btnAdd)
        listContainer = view.findViewById(R.id.listContainer)
        generateButton = view.findViewById(R.id.btnGenerate)

        addButton.setOnClickListener {
            val name = editText.text.toString().trim()
            if (name.isNotEmpty()) {
                addSpot(name)
                editText.text.clear()
            } else {
                Toast.makeText(context, "관광지 이름을 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }

        generateButton.setOnClickListener {
            if (spots.size < 2) {
                Toast.makeText(context, "최소 2개 이상의 관광지를 추가해주세요", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "경로를 생성합니다!", Toast.LENGTH_SHORT).show()
                // 경로 생성 로직
            }
        }

        return view
    }

    private fun addSpot(name: String) {
        spots.add(name)

        val spotView = layoutInflater.inflate(R.layout.item_spot, listContainer, false)
        val spotNumber = spotView.findViewById<TextView>(R.id.spotNumber)
        val spotName = spotView.findViewById<TextView>(R.id.spotName)
        val deleteButton = spotView.findViewById<Button>(R.id.btnDelete)

        spotNumber.text = spots.size.toString()
        spotName.text = name

        deleteButton.setOnClickListener {
            listContainer.removeView(spotView)
            spots.remove(name)
            refreshNumbers()
        }

        listContainer.addView(spotView)
    }

    private fun refreshNumbers() {
        for (i in 0 until listContainer.childCount) {
            val spotNumber = listContainer.getChildAt(i).findViewById<TextView>(R.id.spotNumber)
            spotNumber.text = (i + 1).toString()
        }
    }
}
