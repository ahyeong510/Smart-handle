package com.example.smart_handle.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.smart_handle.R
import com.example.smart_handle.ui.fitness.ai.AiWorkoutFragment

class FitnessRouteFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_fitness_route, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ⭐ 운동탭에 AI 운동 Fragment 삽입
        childFragmentManager.beginTransaction()
            .replace(R.id.fitness_container, AiWorkoutFragment())
            .commit()
    }
}
