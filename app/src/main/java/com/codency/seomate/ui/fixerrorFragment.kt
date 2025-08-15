package com.codency.seomate.ui

import android.os.Bundle
import android.text.TextUtils
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.TimeUnit

class fixerrorFragment : Fragment() {

    private lateinit var inputUrl: EditText
    private lateinit var btnCheckErrors: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var errorsContainer: LinearLayout

    private val geminiApiKey = "AIzaSyCGVmB8Ox0oXqEBNr3K_jF39ingEEmDfiU"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d("FixErrors", "Inflating FixErrors layout...")
        val view = inflater.inflate(R.layout.fragment_fixerror, container, false)
        Log.d("FixErrors", "Layout inflated successfully.")

        try {
            inputUrl = view.findViewById(R.id.inputUrl)
            btnCheckErrors = view.findViewById(R.id.btnCheckErrors)
            progressBar = view.findViewById(R.id.progressBar)
            errorsContainer = view.findViewById(R.id.errorsContainer)
        } catch (e: Exception) {
            Log.e("FixErrors", "View binding failed: ${e.message}", e)
            throw e
        }

        btnCheckErrors.setOnClickListener {
            val rawUrl = inputUrl.text.toString().trim()
            if (rawUrl.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a website URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fullUrl = ensureHttpScheme(rawUrl)
            val normalizedDomain = normalizeDomain(rawUrl)
            Log.d("FixErrors", "Raw: $rawUrl | Full URL: $fullUrl | Normalized: $normalizedDomain")

            checkErrors(fullUrl)
        }

        return view
    }

    private fun normalizeDomain(url: String): String {
        return url
            .replaceFirst("^https?://".toRegex(RegexOption.IGNORE_CASE), "")
            .replaceFirst("^www\\.".toRegex(RegexOption.IGNORE_CASE), "")
            .split("/")[0]
            .trim()
            .lowercase()
    }

    private fun ensureHttpScheme(url: String): String {
        return if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "https://$url"
    }

    private fun checkErrors(fullUrl: String) {
        progressBar.visibility = View.VISIBLE
        errorsContainer.visibility = View.GONE
        errorsContainer.removeAllViews()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val html = fetchWebsiteHtml(fullUrl)
                if (html.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Could not fetch website content", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val prompt = """
                    You are an expert technical SEO auditor.
                    Analyze this HTML and return ONLY JSON in the structure:
                    {
                      "errors": [
                        {"title": "...", "detail": "...", "priority": "High|Medium|Low"}
                      ]
                    }
                    HTML: $html
                """.trimIndent()

                val rawResponse = callGemini(prompt)
                Log.d("FixErrors", "Gemini raw: $rawResponse")

                val cleaned = cleanGeminiJson(rawResponse)
                Log.d("FixErrors", "Cleaned JSON: $cleaned")

                val json = JSONObject(cleaned)
                val errors = json.optJSONArray("errors") ?: JSONArray()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    errorsContainer.visibility = View.VISIBLE

                    if (errors.length() == 0) {
                        addInfoRow("No critical errors found 🎉", "Your page looks solid.", "Low")
                        return@withContext
                    }

                    for (i in 0 until errors.length()) {
                        val item = errors.optJSONObject(i) ?: continue
                        addErrorRow(
                            item.optString("title", "Issue"),
                            item.optString("detail", "No details."),
                            item.optString("priority", "Medium")
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("FixErrors", "Error in checkErrors: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to analyze errors", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchWebsiteHtml(url: String): String {
        return try {
            try { URL(url) } catch (e: MalformedURLException) { return "" }
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build()

            val req = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return ""
                resp.body?.string() ?: ""
            }
        } catch (e: Exception) {
            Log.e("FixErrors", "fetchWebsiteHtml failed: ${e.message}", e)
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

            val partsArray = JSONArray().put(JSONObject().put("text", prompt))
            val contentsArray = JSONArray().put(JSONObject().put("parts", partsArray))
            val jsonBody = JSONObject().put("contents", contentsArray)

            val body = RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString())
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$geminiApiKey")
                .post(body)
                .build()

            val response = client.newCall(req).execute().body?.string() ?: "{}"
            JSONObject(response)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "{}") ?: "{}"
        } catch (e: Exception) {
            Log.e("FixErrors", "callGemini failed: ${e.message}", e)
            "{}"
        }
    }

    private fun cleanGeminiJson(raw: String) =
        raw.replace("```json", "").replace("```", "").trim()

    private fun addErrorRow(title: String, detail: String, priority: String) {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundResource(R.drawable.rounded_card)
        }

        val tvTitle = TextView(ctx).apply {
            text = "• $title"
            textSize = 16f
            setTextColor(resources.getColor(R.color.black, null))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 2
        }

        val tvDetail = TextView(ctx).apply {
            text = detail
            textSize = 14f
            setTextColor(resources.getColor(R.color.black, null))
            setPadding(0, 6, 0, 0)
        }

        val tvPriority = TextView(ctx).apply {
            text = priority.uppercase()
            textSize = 12f
            setPadding(20, 8, 20, 8)
            setTextColor(resources.getColor(android.R.color.white, null))
            background = resources.getDrawable(
                when (priority.lowercase()) {
                    "high" -> R.drawable.bg_chip_red
                    "low" -> R.drawable.bg_chip_gray
                    else -> R.drawable.bg_chip_orange
                },
                null
            )
        }

        val chipWrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
            addView(tvPriority)
        }

        row.addView(tvTitle)
        row.addView(tvDetail)
        row.addView(chipWrap)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(16, 12, 16, 0) }

        errorsContainer.addView(row, params)
    }

    private fun addInfoRow(title: String, detail: String, priority: String) {
        addErrorRow(title, detail, priority)
    }
}
