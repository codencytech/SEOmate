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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

class CompetitorFragment : Fragment() {

    private lateinit var radioGroupMode: RadioGroup
    private lateinit var radioManual: RadioButton
    private lateinit var radioDiscover: RadioButton
    private lateinit var inputUrl: EditText
    private lateinit var inputCompetitors: EditText
    private lateinit var btnCompare: Button
    private lateinit var resultsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_competitor, container, false)

        radioGroupMode = v.findViewById(R.id.rgMode)
        radioManual = v.findViewById(R.id.rbManual)
        radioDiscover = v.findViewById(R.id.rbDiscover)
        inputUrl = v.findViewById(R.id.inputMyUrl)
        inputCompetitors = v.findViewById(R.id.inputCompetitors)
        btnCompare = v.findViewById(R.id.btnCompare)
        resultsContainer = v.findViewById(R.id.resultsContainer)
        progressBar = v.findViewById(R.id.progressBar)

        // Default mode → Manual
        updateUIForMode(false)

        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            updateUIForMode(checkedId == R.id.rbDiscover)
        }

        btnCompare.setOnClickListener {
            if (radioDiscover.isChecked) {
                discoverCompetitors()
            } else {
                manualCompare()
            }
        }

        return v
    }

    private fun updateUIForMode(isDiscoverMode: Boolean) {
        if (isDiscoverMode) {
            inputCompetitors.visibility = View.GONE
            btnCompare.text = "Discover Competitors"
        } else {
            inputCompetitors.visibility = View.VISIBLE
            btnCompare.text = "Compare Websites"
        }
        resultsContainer.removeAllViews()
    }

    private fun manualCompare() {
        val url1 = inputUrl.text.toString().trim()
        val url2 = inputCompetitors.text.toString().trim()

        if (url1.isEmpty() || url2.isEmpty()) {
            toast("Enter both URLs")
            return
        }

        resultsContainer.removeAllViews()
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            val report1 = fetchBasicMetrics(url1)
            val report2 = fetchBasicMetrics(url2)

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                showComparison(url1, report1, url2, report2)
            }
        }
    }

    private fun discoverCompetitors() {
        val url = inputUrl.text.toString().trim()
        if (url.isEmpty()) {
            toast("Enter a website URL")
            return
        }

        resultsContainer.removeAllViews()
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            val domain = normalizeDomain(url)
            val queries = listOf(
                "related:$domain",
                "similar:$domain",
                "alternatives to $domain",
                "sites like $domain"
            )

            val foundDomains = mutableSetOf<String>()

            for (q in queries) {
                try {
                    val req = Request.Builder()
                        .url("https://duckduckgo.com/html/?q=${q}")
                        .header("User-Agent", "Mozilla/5.0 (Android)")
                        .get()
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val html = resp.body?.string() ?: ""
                        val links = Regex("""<a[^>]+href=["'](http[^"']+)""")
                            .findAll(html)
                            .map { it.groupValues[1] }
                            .toList()

                        for (l in links) {
                            try {
                                val d = normalizeDomain(l)
                                if (d.isNotEmpty() && d != domain) {
                                    foundDomains.add(d)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Competitor", "Error fetching: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (foundDomains.isEmpty()) {
                    toast("No competitors found. Try Manual Compare.")
                } else {
                    resultsContainer.removeAllViews()
                    foundDomains.take(10).forEach { d ->
                        val tv = TextView(requireContext()).apply {
                            text = "• $d"
                            textSize = 14f
                            setPadding(8, 8, 8, 8)
                        }
                        resultsContainer.addView(tv)
                    }
                }
            }
        }
    }

    // ---- Basic Metrics ----
    data class Metrics(val textLength: Int, val linkCount: Int)

    private fun fetchBasicMetrics(url: String): Metrics {
        return try {
            val fullUrl = ensureHttp(url)
            val req = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val html = resp.body?.string() ?: ""
                val textOnly = html.replace(Regex("<[^>]+>"), " ")
                val links = Regex("<a\\b", RegexOption.IGNORE_CASE).findAll(html).count()
                Metrics(textOnly.trim().length, links)
            }
        } catch (e: Exception) {
            Metrics(0, 0)
        }
    }

    private fun showComparison(url1: String, m1: Metrics, url2: String, m2: Metrics) {
        resultsContainer.removeAllViews()

        val table = TableLayout(requireContext())
        table.layoutParams = TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            TableLayout.LayoutParams.WRAP_CONTENT
        )

        fun addRow(label: String, v1: String, v2: String) {
            val row = TableRow(requireContext())
            row.addView(TextView(requireContext()).apply {
                text = label
                setPadding(8, 8, 8, 8)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            row.addView(TextView(requireContext()).apply {
                text = v1
                setPadding(8, 8, 8, 8)
            })
            row.addView(TextView(requireContext()).apply {
                text = v2
                setPadding(8, 8, 8, 8)
            })
            table.addView(row)
        }

        // Add header row
        val headerRow = TableRow(requireContext())
        headerRow.addView(TextView(requireContext()).apply {
            text = "Metric"
            setPadding(8, 8, 8, 8)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        headerRow.addView(TextView(requireContext()).apply {
            text = "Your Site"
            setPadding(8, 8, 8, 8)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        headerRow.addView(TextView(requireContext()).apply {
            text = "Competitor"
            setPadding(8, 8, 8, 8)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        table.addView(headerRow)

        // Add divider
        val divider = View(requireContext())
        divider.layoutParams = TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            1
        ).apply {
            setMargins(0, 8, 0, 8)
        }
        divider.setBackgroundColor(resources.getColor(android.R.color.darker_gray))
        table.addView(divider)

        // Add data rows
        addRow("URL", normalizeDomain(url1), normalizeDomain(url2))
        addRow("Text Size", "${m1.textLength} chars", "${m2.textLength} chars")
        addRow("Links Found", m1.linkCount.toString(), m2.linkCount.toString())

        resultsContainer.addView(table)
    }

    // ---- Helpers ----
    private fun ensureHttp(url: String): String {
        return if (url.startsWith("http")) url else "https://$url"
    }

    private fun normalizeDomain(url: String): String {
        return try {
            URI(ensureHttp(url)).host
                .replaceFirst("^www\\.".toRegex(), "")
                .lowercase(Locale.US)
        } catch (e: Exception) {
            url
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}