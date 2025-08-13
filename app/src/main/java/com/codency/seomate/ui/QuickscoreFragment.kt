package com.codency.seomate.ui.quickscore

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.codency.seomate.databinding.FragmentQuickscoreBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.TimeUnit

class QuickScoreFragment : Fragment() {

    private var _binding: FragmentQuickscoreBinding? = null
    private val binding get() = _binding!!

    private val geminiApiKey = "AIzaSyCGVmB8Ox0oXqEBNr3K_jF39ingEEmDfiU"

    private val scoreCache = mutableMapOf<String, Pair<Int, String>>()
    private var isChecking = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuickscoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnFullAudit.visibility = View.GONE
        binding.resultContainer.visibility = View.GONE

        binding.btnCheckScore.setOnClickListener {
            if (isChecking) {
                Toast.makeText(
                    requireContext(),
                    "Please wait for the current check to finish.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val userInput = binding.inputUrl.text.toString().trim()
            val normalizedUrl = normalizeAndValidateUrl(userInput)
            if (normalizedUrl == null) {
                showError("Enter a valid website URL")
                return@setOnClickListener
            }

            clearError()
            isChecking = true
            binding.btnCheckScore.isEnabled = false

            fetchSeoDataWithGemini(normalizedUrl)
        }

        binding.btnFullAudit.setOnClickListener {
            val userInput = binding.inputUrl.text.toString().trim()
            val normalizedUrl = normalizeAndValidateUrl(userInput)
            if (normalizedUrl == null) {
                Toast.makeText(requireContext(), "Enter a valid website URL", Toast.LENGTH_SHORT)
                    .show()
            } else {
                runFullAudit(normalizedUrl)
            }
        }
    }

    private fun normalizeAndValidateUrl(input: String): String? {
        val urlString =
            if (input.startsWith("http://", true) || input.startsWith("https://", true)) {
                input
            } else {
                "https://$input"
            }
        return try {
            val url = URL(urlString)
            if (url.protocol != "http" && url.protocol != "https") return null
            if (url.host.isNullOrBlank()) return null
            url.toString()
        } catch (e: MalformedURLException) {
            null
        }
    }

    private fun fetchSeoDataWithGemini(website: String) {
        scoreCache[website]?.let { cached ->
            binding.progressBar.visibility = View.GONE
            binding.resultContainer.visibility = View.VISIBLE
            binding.tvScore.text = "Score: ${cached.first} / 100"
            binding.tvSummary.text = cached.second
            binding.btnFullAudit.visibility = View.VISIBLE
            isChecking = false
            binding.btnCheckScore.isEnabled = true
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.resultContainer.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val htmlContent = fetchWebsiteHtml(website)
                if (htmlContent.isEmpty()) {
                    withContext(Dispatchers.Main) { showError("Could not fetch website content") }
                    return@launch
                }

                // SAME HTML as Full Audit, but prompt only for score + summary
                val prompt = """
                    You are an expert SEO analyst AI.
                    Analyze the following HTML content and return ONLY a JSON object:
                    {
                      "score": <0-100>,
                      "summary": "<short advice for improvement>"
                    }
                    Keep the score calculation identical to a full SEO audit.
                    HTML Content:
                    $htmlContent
                """.trimIndent()

                val rawResult = cleanGeminiJson(callGemini(prompt))
                val json = JSONObject(rawResult)

                val score = json.optInt("score", -1)
                val summary = json.optString("summary", "No summary available")

                withContext(Dispatchers.Main) {
                    if (score in 0..100) {
                        scoreCache[website] = Pair(score, summary)
                        binding.progressBar.visibility = View.GONE
                        binding.resultContainer.visibility = View.VISIBLE
                        binding.tvScore.text = "Score: $score / 100"
                        binding.tvSummary.text = summary
                        binding.btnFullAudit.visibility = View.VISIBLE
                    } else {
                        showError("Invalid score received")
                    }
                    isChecking = false
                    binding.btnCheckScore.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showError("Error: ${e.message}") }
            }
        }
    }

    private fun runFullAudit(website: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.resultContainer.visibility = View.GONE
        binding.tvScore.text = "Full Audit..."
        binding.tvSummary.text = ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val htmlContent = fetchWebsiteHtml(website)
                if (htmlContent.isEmpty()) {
                    withContext(Dispatchers.Main) { showError("Could not fetch website content") }
                    return@launch
                }

                val prompt = """
                You are an expert SEO auditor.
                Analyze the following HTML and return ONLY valid JSON in this exact format:
                {
                  "score": <integer between 0 and 100>,
                  "issues": [
                    "<short description of issue 1>",
                    "<short description of issue 2>",
                    "<short description of issue 3>"
                  ],
                  "recommendations": [
                    "<short actionable suggestion 1>",
                    "<short actionable suggestion 2>",
                    "<short actionable suggestion 3>"
                  ]
                }
                Rules:
                - The "score" MUST be calculated using the same method as in Check Score.
                - Do not include markdown, code fences, or extra text.
                - "issues" describe the main problems found in the HTML.
                - "recommendations" are fixes for those problems.
                - Keep each point short (max 100 characters).

                HTML content:
                $htmlContent
            """.trimIndent()

                val rawResult = cleanGeminiJson(callGemini(prompt))
                val json = JSONObject(rawResult)

                val score = json.optInt("score", -1)
                val issues = json.optJSONArray("issues")
                val recs = json.optJSONArray("recommendations")

                val issuesList = mutableListOf<String>()
                val recsList = mutableListOf<String>()

                issues?.let {
                    for (i in 0 until it.length()) {
                        issuesList.add(it.optString(i))
                    }
                }

                recs?.let {
                    for (i in 0 until it.length()) {
                        recsList.add(it.optString(i))
                    }
                }

                val finalReport = StringBuilder()
                finalReport.append("Issues:\n")
                issuesList.forEach { finalReport.append("- $it\n") }
                finalReport.append("\nRecommendations:\n")
                recsList.forEach { finalReport.append("- $it\n") }

                withContext(Dispatchers.Main) {
                    if (score in 0..100) {
                        binding.progressBar.visibility = View.GONE
                        binding.resultContainer.visibility = View.VISIBLE
                        binding.tvScore.text = "Score: $score / 100"
                        binding.tvSummary.text = finalReport.toString()
                        binding.btnFullAudit.visibility = View.VISIBLE
                    } else {
                        showError("Invalid score received")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showError("Error: ${e.message}") }
            }
        }
    }

    private fun fetchWebsiteHtml(url: String): String {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().body?.string() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun callGemini(prompt: String): String {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        return try {
                            java.net.InetAddress.getAllByName(hostname)
                                .filter { it is java.net.Inet4Address }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                })
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
            Log.d("QuickScore", "Gemini API raw response: $responseString")

            JSONObject(responseString)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "{}") ?: "{}"
        } catch (e: Exception) {
            "{}"
        }
    }

    private fun cleanGeminiJson(raw: String): String {
        return raw.replace("```json", "").replace("```", "").trim()
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.resultContainer.visibility = View.VISIBLE
        binding.tvScore.text = "Error"
        binding.tvSummary.text = message
        binding.btnFullAudit.visibility = View.GONE
        isChecking = false
        binding.btnCheckScore.isEnabled = true
    }

    private fun clearError() {
        binding.resultContainer.visibility = View.GONE
        binding.btnFullAudit.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
