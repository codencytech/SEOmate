package com.codency.seomate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.codency.seomate.databinding.FragmentKeywordBinding

class KeywordFragment : Fragment() {

    private var _binding: FragmentKeywordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeywordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Placeholder content
        binding.title.text = "Keyword Tracker"
        binding.description.text = "Monitor your top keywords and their ranking changes."

        // TODO: replace with real keyword data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
