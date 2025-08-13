package com.codency.seomate.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.codency.seomate.R
import com.codency.seomate.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Navigate to AnalysisFragment when "Check Now" is clicked
        binding.checkNowButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_quickscoreFragment)
        }

        // Navigate to Audit Site (full site audit)
        binding.cardAuditSite.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_analysisFragment)
        }

        // Navigate to Keyword Tracker
        binding.cardTrackKeywords.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_keywordFragment)
        }

        // Navigate to Fix SEO Errors
        binding.cardFixErrors.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_fixerrorFragment)
        }

        // Navigate to Generate Report
        binding.cardGenerateReport.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_reportFragment)
        }

        // Navigate to Competitor Analysis
        binding.cardCompetitor.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_competitorFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
