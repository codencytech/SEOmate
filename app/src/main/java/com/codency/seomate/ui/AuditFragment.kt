package com.codency.seomate.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.codency.seomate.R
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

class AuditFragment : Fragment() {

    private lateinit var inputUrl: EditText
    private lateinit var btnAudit: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvScore: TextView
    private lateinit var tvIssues: TextView
    private lateinit var tvRecommendations: TextView

    private val geminiApiKey = "AIzaSyCGVmB8Ox0oXqEBNr3K_jF39ingEEmDfiU"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_audit, container, false)

        inputUrl = root.findViewById(R.id.inputUrlAudit)
        btnAudit = root.findViewById(R.id.btnRunAudit)
        progressBar = root.findViewById(R.id.progressBarAudit)
        tvScore = root.findViewById(R.id.tvAuditScore)
        tvIssues = root.findViewById(R.id.tvAuditIssues)
        tvRecommendations = root.findViewById(R.id.tvAuditRecommendations)

        btnAudit.setOnClickListener {
            val normalizedUrl = normalizeAndValidateUrl(inputUrl.text.toString().trim())
            if (normalizedUrl == null) {
                Toast.makeText(requireContext(), "Enter a valid website URL", Toast.LENGTH_SHORT).show()
            } else {
                runFullAudit(normalizedUrl)
            }
        }

        return root
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

    private fun runFullAudit(website: String) {
        progressBar.visibility = View.VISIBLE
        tvScore.text = ""
        tvIssues.text = ""
        tvRecommendations.text = ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val htmlContent = fetchWebsiteHtml(website)
                if (htmlContent.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Could not fetch website content", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                    }
                    return@launch
                }

                val prompt = """
You are an expert SEO auditor. 
Use the following scoring method for EVERY request to ensure consistent results:

**Scoring Rules:**
- Base score strictly between 0 and 100.
- Factors: title/meta tags, headings, schema markup, image optimization, link structure, keyword usage, page speed, accessibility.
- Each factor is equally weighted.
- Missing major elements significantly reduces the score.

Now, analyze the following HTML content and return ONLY valid JSON in this exact format:
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
- No markdown, code fences, or extra text.
- Keep each issue/recommendation under 100 characters.

HTML content:
$htmlContent
""".trimIndent()

                val auditResult = cleanGeminiJson(callGemini(prompt))
                val json = JSONObject(auditResult)

                val score = json.optInt("score", -1)
                val issues = json.optJSONArray("issues")
                val recs = json.optJSONArray("recommendations")

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (score in 0..100) {
                        tvScore.text = "Score: $score / 100"

                        val issuesText = StringBuilder("Issues:\n")
                        if (issues != null) {
                            for (i in 0 until issues.length()) {
                                issuesText.append("• ").append(issues.getString(i)).append("\n")
                            }
                        }
                        tvIssues.text = issuesText.toString()

                        val recsText = StringBuilder("Recommendations:\n")
                        if (recs != null) {
                            for (i in 0 until recs.length()) {
                                recsText.append("• ").append(recs.getString(i)).append("\n")
                            }
                        }
                        tvRecommendations.text = recsText.toString()
                    } else {
                        Toast.makeText(requireContext(), "Invalid score received", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
                .build()

            val partsArray = org.json.JSONArray().put(JSONObject().put("text", prompt))
            val contentsArray = org.json.JSONArray().put(JSONObject().put("parts", partsArray))
            val jsonBody = JSONObject().put("contents", contentsArray)

            val body = RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$geminiApiKey")
                .post(body)
                .build()

            val responseString = client.newCall(request).execute().body?.string() ?: "{}"
            Log.d("AuditFragment", "Gemini API raw response: $responseString")

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
}
