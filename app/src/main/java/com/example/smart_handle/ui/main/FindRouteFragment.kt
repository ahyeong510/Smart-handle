package com.example.smart_handle.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smart_handle.R
import com.example.smart_handle.ui.driving.DrivingActivity

class FindRouteFragment : Fragment() {

    private lateinit var startPointInput: EditText
    private lateinit var endPointInput: EditText
    private lateinit var btnGenerateRoute: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_find_route, container, false)

        startPointInput = view.findViewById(R.id.etStart)
        endPointInput = view.findViewById(R.id.etEnd)
        btnGenerateRoute = view.findViewById(R.id.btnGenerateRoute)

        btnGenerateRoute.setOnClickListener {
            val start = startPointInput.text.toString()
            val end = endPointInput.text.toString()

            if (start.isNotEmpty() && end.isNotEmpty()) {
                try {
                    val intent = Intent(requireActivity(), DrivingActivity::class.java)
                    intent.putExtra("startPoint", start)
                    intent.putExtra("endPoint", end)
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "주행화면을 여는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "출발지와 도착지를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}
