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
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class KeywordFragment : Fragment() {

    private lateinit var inputUrl: EditText
    private lateinit var inputKeywords: EditText
    private lateinit var btnTrack: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var resultContainer: LinearLayout
    private lateinit var keywordsList: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_keyword, container, false)

        inputUrl = view.findViewById(R.id.inputUrl)
        inputKeywords = view.findViewById(R.id.inputKeywords)
        btnTrack = view.findViewById(R.id.btnTrackKeywords)
        progressBar = view.findViewById(R.id.progressBar)
        resultContainer = view.findViewById(R.id.resultContainer)
        keywordsList = view.findViewById(R.id.keywordsList)

        btnTrack.setOnClickListener {
            val url = inputUrl.text.toString().trim()
            val keywords = inputKeywords.text.toString().trim()

            if (url.isEmpty() || keywords.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter both URL and keywords", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val normalizedUrl = normalizeDomain(url)
            Log.d("KeywordTracker", "Normalized domain: $normalizedUrl")

            trackKeywords(normalizedUrl, keywords)
        }


        return view
    }

    // ✅ Function to clean and normalize the domain
    private fun normalizeDomain(url: String): String {
        return url
            .replaceFirst("^https?://".toRegex(), "") // remove http or https
            .replaceFirst("^www\\.".toRegex(), "")    // remove www.
            .split("/")[0] // keep only domain name
            .trim()
    }

    private fun trackKeywords(siteUrl: String, keywords: String) {
        progressBar.visibility = View.VISIBLE
        resultContainer.visibility = View.GONE
        keywordsList.removeAllViews()

        val apiKey = "f561beb1926ccbcb59891bc3cda421ea3345f8897d2c8c18ba6e350a70be2767"
        val keywordArray = keywords.split(",").map { it.trim() }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resultsList = mutableListOf<org.json.JSONObject>()

                //Single API request for all keywords (comma-separated)
                val queryParam = keywordArray.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
                val apiUrl = "https://serpapi.com/search.json?engine=google&q=$queryParam&api_key=$apiKey&gl=in&hl=en"

                Log.d("KeywordTracker", "API URL: $apiUrl")

                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connect()

                val response = conn.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(response)
                val organicResults = json.optJSONArray("organic_results") ?: JSONArray()

                for (keyword in keywordArray) {
                    var positionFound: Int? = null
                    for (i in 0 until organicResults.length()) {
                        val result = organicResults.getJSONObject(i)
                        val link = result.optString("link", "")
                        if (link.contains(siteUrl, ignoreCase = true)) {
                            positionFound = i + 1
                            break
                        }
                    }

                    val obj = org.json.JSONObject()
                    obj.put("keyword", keyword)
                    obj.put("position", positionFound ?: -1)
                    obj.put("volume", "N/A")
                    resultsList.add(obj)
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    resultContainer.visibility = View.VISIBLE

                    for (item in resultsList) {
                        val row = layoutInflater.inflate(R.layout.item_keyword_result, keywordsList, false)
                        row.findViewById<TextView>(R.id.tvKeyword).text = item.getString("keyword")
                        val pos = item.getInt("position")
                        row.findViewById<TextView>(R.id.tvPosition).text =
                            if (pos == -1) "Not in Top 10" else "Pos: $pos"
                        row.findViewById<TextView>(R.id.tvVolume).text = "Vol: ${item.getString("volume")}"
                        keywordsList.addView(row)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to fetch rankings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}
