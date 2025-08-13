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

class QuickScoreFragment : Fragment() {

    private var _binding: FragmentQuickscoreBinding? = null
    private val binding get() = _binding!!

    private val geminiApiKey = "AIzaSyCGVmB8Ox0oXqEBNr3K_jF39ingEEmDfiU"

    // In-memory cache to store results during app session
    private val scoreCache = mutableMapOf<String, Pair<Int, String>>()

    // Flag to prevent multiple simultaneous requests
    private var isChecking = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuickscoreBinding.inflate(inflater, container, false)
        Log.d("QuickScore", "Fragment view created")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("QuickScore", "onViewCreated: UI ready")

        binding.btnFullAudit.visibility = View.GONE
        binding.resultContainer.visibility = View.GONE

        binding.btnCheckScore.setOnClickListener {
            if (isChecking) {
                Toast.makeText(requireContext(), "Please wait for the current check to finish.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userInput = binding.inputUrl.text.toString().trim()
            Log.d("QuickScore", "Check Score clicked. Input: $userInput")

            val normalizedUrl = normalizeAndValidateUrl(userInput)
            if (normalizedUrl == null) {
                showError("Please enter a valid website URL")
                return@setOnClickListener
            }
            clearError()

            isChecking = true
            binding.btnCheckScore.isEnabled = false

            fetchSeoDataWithGemini(normalizedUrl)
        }
    }

    private fun normalizeAndValidateUrl(input: String): String? {
        val urlString = if (input.startsWith("http://", true) || input.startsWith("https://", true)) {
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

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.resultContainer.visibility = View.VISIBLE
        binding.tvScore.text = "Error"
        binding.tvSummary.text = message
        binding.btnFullAudit.visibility = View.GONE
        // Re-enable button in case of error
        isChecking = false
        binding.btnCheckScore.isEnabled = true
    }

    private fun clearError() {
        binding.resultContainer.visibility = View.GONE
        binding.btnFullAudit.visibility = View.GONE
    }

    private fun fetchSeoDataWithGemini(website: String) {
        Log.d("QuickScore", "Starting SEO check for: $website")

        scoreCache[website]?.let { cached ->
            Log.d("QuickScore", "Returning cached score for $website")
            binding.progressBar.visibility = View.GONE
            binding.resultContainer.visibility = View.VISIBLE
            binding.tvScore.text = "Score: ${cached.first} / 100"
            binding.tvSummary.text = cached.second
            binding.btnFullAudit.visibility = View.VISIBLE
            // Reset checking flags since we showed cached result
            isChecking = false
            binding.btnCheckScore.isEnabled = true
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.resultContainer.visibility = View.GONE
        binding.tvScore.text = "Score: --"
        binding.tvSummary.text = "Summary will appear here..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var htmlContent = fetchWebsiteHtml(website)
                Log.d("QuickScore", "Fetched HTML length: ${htmlContent.length}")

                if (htmlContent.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showError("Could not fetch website content")
                    }
                    return@launch
                }

                val seoHtml = extractSeoRelevantHtml(htmlContent)
                Log.d("QuickScore", "SEO relevant HTML: $seoHtml")

                val trimmedSeoHtml = if (seoHtml.length > 3000) {
                    seoHtml.take(3000) + "\n<!-- truncated -->"
                } else seoHtml

                val prompt = """
                    You are an expert SEO analyst AI.
                    Analyze the following HTML content for SEO quality.

                    Instructions:
                    - Return ONLY a JSON object with two keys:
                      {
                        "score": <an integer between 0 and 100>,
                        "summary": "<a brief summary of SEO improvements>"
                      }
                    - Base the score on consistent SEO best practices.
                    - Avoid randomness or opinion; be factual and stable.
                    - Focus on SEO-relevant tags: title, meta description, meta keywords, headings (h1, h2), and main content paragraphs.
                    - Ignore scripts, styles, and dynamic content.
                    - Provide a concise summary that can help improve SEO.

                    HTML Content:
                    $trimmedSeoHtml
                """.trimIndent()

                val maxRetries = 3
                var attempt = 0
                var json: JSONObject? = null
                while (attempt < maxRetries && json == null) {
                    val rawResult = cleanGeminiJson(callGemini(prompt))
                    Log.d("QuickScore", "Gemini raw response attempt $attempt: $rawResult")
                    try {
                        json = JSONObject(rawResult)
                    } catch (e: Exception) {
                        Log.e("QuickScore", "JSON parse failed on attempt $attempt: ${e.message}")
                        attempt++
                        if (attempt == maxRetries) {
                            withContext(Dispatchers.Main) {
                                showError("Failed to parse response. Please try again.")
                            }
                            return@launch
                        }
                    }
                }

                val score = json?.optInt("score", -1) ?: -1
                val summary = json?.optString("summary", "No summary available") ?: "No summary available"

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
                Log.e("QuickScore", "Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showError("Error: ${e.message}")
                }
            }
        }
    }

    private fun extractSeoRelevantHtml(html: String): String {
        val titleRegex = "<title>(.*?)</title>".toRegex(RegexOption.IGNORE_CASE)
        val metaDescriptionRegex = "<meta\\s+name=[\"']description[\"']\\s+content=[\"'](.*?)[\"']\\s*/?>".toRegex(RegexOption.IGNORE_CASE)
        val metaKeywordsRegex = "<meta\\s+name=[\"']keywords[\"']\\s+content=[\"'](.*?)[\"']\\s*/?>".toRegex(RegexOption.IGNORE_CASE)
        val h1Regex = "<h1.*?>(.*?)</h1>".toRegex(RegexOption.IGNORE_CASE)
        val h2Regex = "<h2.*?>(.*?)</h2>".toRegex(RegexOption.IGNORE_CASE)
        val pRegex = "<p.*?>(.*?)</p>".toRegex(RegexOption.IGNORE_CASE)

        val title = titleRegex.find(html)?.groups?.get(1)?.value ?: ""
        val metaDescription = metaDescriptionRegex.find(html)?.groups?.get(1)?.value ?: ""
        val metaKeywords = metaKeywordsRegex.find(html)?.groups?.get(1)?.value ?: ""

        val h1s = h1Regex.findAll(html).map { it.groups[1]?.value ?: "" }.toList()
        val h2s = h2Regex.findAll(html).map { it.groups[1]?.value ?: "" }.toList()
        val ps = pRegex.findAll(html).map { it.groups[1]?.value ?: "" }.take(3).toList()

        return buildString {
            appendLine("<title>$title</title>")
            appendLine("""<meta name="description" content="$metaDescription" />""")
            if (metaKeywords.isNotEmpty()) {
                appendLine("""<meta name="keywords" content="$metaKeywords" />""")
            }
            for (h1 in h1s) appendLine("<h1>$h1</h1>")
            for (h2 in h2s) appendLine("<h2>$h2</h2>")
            for (p in ps) appendLine("<p>$p</p>")
        }
    }

    private fun fetchWebsiteHtml(url: String): String {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            Log.e("QuickScore", "HTML fetch failed: ${e.message}", e)
            ""
        }
    }

    private fun callGemini(prompt: String): String {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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

            val partsArray = org.json.JSONArray()
            partsArray.put(JSONObject().put("text", prompt))

            val contentsArray = org.json.JSONArray()
            contentsArray.put(JSONObject().put("parts", partsArray))

            val jsonBody = JSONObject()
            jsonBody.put("contents", contentsArray)

            val body = RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                jsonBody.toString()
            )

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
            Log.e("QuickScore", "Gemini API call failed: ${e.message}", e)
            "{}"
        }
    }

    private fun cleanGeminiJson(raw: String): String {
        return raw.replace("```json", "")
            .replace("```", "")
            .trim()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
