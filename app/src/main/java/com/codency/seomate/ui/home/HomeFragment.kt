package com.codency.seomate.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.codency.seomate.R
import com.codency.seomate.databinding.FragmentHomeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // 🔑 Same Gemini API key you used in QuickScore
    private val geminiApiKey = "AIzaSyCGVmB8Ox0oXqEBNr3K_jF39ingEEmDfiU"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Navigate to QuickscoreFragment when "Check Now" is clicked
        binding.checkNowButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_quickscoreFragment)
        }

        // Navigate to Audit Site (full site audit)
        binding.cardAuditSite.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_auditFragment)
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

        // =============================
        // 📌 Daily SEO Learn Integration
        // =============================
        fetchDailySeoTip()

        binding.btnRefreshTip.setOnClickListener {
            fetchDailySeoTip()
        }
    }

    private fun fetchDailySeoTip() {
        binding.seoTipContent.text = "Loading today’s SEO tip..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prompt = """
                    You are an SEO trainer.
                    Provide ONE short, unique, practical SEO tip (max 30 words).
                    Keep it general and not tied to a specific website.
                    Return ONLY a JSON:
                    {
                       "tip": "<the tip here>"
                    }
                """.trimIndent()

                val response = callGemini(prompt)
                val clean = response.replace("```json", "").replace("```", "").trim()
                val json = JSONObject(clean)
                var tip = json.optString("tip", "")

                // 🛡 Post-process to avoid raw AI output risk
                if (tip.isBlank()) {
                    tip = getFallbackTip()
                } else {
                    tip = "SEO Insight: $tip"
                }

                withContext(Dispatchers.Main) {
                    binding.seoTipContent.text = tip
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.seoTipContent.text = getFallbackTip()
                }
            }
        }
    }

    private fun callGemini(prompt: String): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val partsArray = org.json.JSONArray().put(JSONObject().put("text", prompt))
        val contentsArray = org.json.JSONArray().put(JSONObject().put("parts", partsArray))
        val jsonBody = JSONObject().put("contents", contentsArray)

        val body =
            RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$geminiApiKey")
            .post(body)
            .build()

        val responseString = client.newCall(request).execute().body?.string() ?: "{}"
        return JSONObject(responseString)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text", "{}") ?: "{}"
    }

    // ✅ Hardcoded fallback SEO tips
    private fun getFallbackTip(): String {
        val tips = listOf(
            "Use descriptive title tags under 60 characters.",
            "Optimize images with alt text for better rankings.",
            "Keep page load times under 3 seconds.",
            "Write meta descriptions under 160 characters.",
            "Use HTTPS to improve trust and SEO."
        )
        return "SEO Insight: " + tips.random()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
