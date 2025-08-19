package com.codency.seomate.ui.report

import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ReportFragment : Fragment() {

    private val TAG = "GenReport"

    private lateinit var inputUrl: EditText
    private var inputCompetitor: EditText? = null   // optional field, used if present
    private lateinit var btnGenerate: Button
    private lateinit var progressBar: ProgressBar

    private val geminiApiKey = "AIzaSyCGVmB8Ox0oXqEBNr3K_jF39ingEEmDfiU"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "Inflating Report layout...")
        val v = inflater.inflate(R.layout.fragment_report, container, false)

        inputUrl = v.findViewById(R.id.inputUrl)
        inputCompetitor = v.findViewById(R.id.inputCompetitor)
        btnGenerate = v.findViewById(R.id.btnGenerateReport)
        progressBar = v.findViewById(R.id.progressBar)

        btnGenerate.setOnClickListener { onGenerate() }

        Log.d(TAG, "Layout inflated successfully.")
        return v
    }

    private fun onGenerate() {
        val rawUrl = inputUrl.text.toString().trim()
        if (rawUrl.isEmpty()) {
            toast("Please enter website URL")
            return
        }

        val normalizedDomain = normalizeDomain(rawUrl)
        val fullUrl = ensureHttpScheme(rawUrl)
        val competitorUrlRaw = inputCompetitor?.text?.toString()?.trim().orEmpty()
        val competitorUrl = if (competitorUrlRaw.isNotEmpty()) ensureHttpScheme(competitorUrlRaw) else ""

        Log.d(TAG, "URL in: $rawUrl | full: $fullUrl | domain: $normalizedDomain | competitor: $competitorUrl")

        progressBar.visibility = View.VISIBLE
        btnGenerate.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1) Fetch website HTML (and competitor HTML if provided)
                val html = fetchWebsiteHtml(fullUrl)
                val compHtml = if (competitorUrl.isNotEmpty()) fetchWebsiteHtml(competitorUrl) else ""

                if (html.isBlank()) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        btnGenerate.isEnabled = true
                        toast("Could not fetch website content")
                    }
                    return@launch
                }

                // 2) Ask AI to analyze full HTML and return structured JSON
                val aiJsonString = callGemini(buildPrompt(fullUrl, html, competitorUrl, compHtml))
                Log.d(TAG, "Gemini raw text: $aiJsonString")
                val clean = cleanGeminiJson(aiJsonString)
                val parsed = safeParseJson(clean)

                // 3) Compose PDF sections
                val reportTitle = "SEO Report - $normalizedDomain"
                val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val header = "Website: $fullUrl\nDate: $nowStr"

                val pdfBytes = buildPdf(
                    title = reportTitle,
                    header = header,
                    ai = parsed
                )

                val name = "SEOmate_Report_${normalizedDomain}_${timeStamp()}.pdf"
                val uri = saveToDownloads(name, "application/pdf", pdfBytes)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnGenerate.isEnabled = true
                    toast("PDF saved to Downloads")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Generate failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnGenerate.isEnabled = true
                    toast("Failed to generate report")
                }
            }
        }
    }

    // -------------------------
    // Networking & helpers
    // -------------------------
    private fun ensureHttpScheme(url: String): String {
        return if (url.startsWith("http://", true) || url.startsWith("https://", true)) url
        else "https://$url"
    }

    private fun normalizeDomain(url: String): String {
        return url
            .replaceFirst("^https?://".toRegex(RegexOption.IGNORE_CASE), "")
            .replaceFirst("^www\\.".toRegex(RegexOption.IGNORE_CASE), "")
            .split("/")[0]
            .trim()
            .lowercase(Locale.US)
    }

    private fun fetchWebsiteHtml(fullUrl: String): String {
        return try {
            try { URL(fullUrl) } catch (_: MalformedURLException) { return "" }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) SEOmate/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) "" else (resp.body?.string() ?: "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchWebsiteHtml: ${e.message}", e)
            ""
        }
    }

    // -------------------------
    // AI integration (Gemini)
    // -------------------------
    private fun buildPrompt(url: String, html: String, compUrl: String, compHtml: String): String {
        // Short, strict JSON instruction. Keeps responses compact & parseable.
        return """
You are an expert technical SEO auditor.

Analyze the following website HTML${if (compUrl.isNotEmpty()) " and compare against competitor HTML" else ""}.
Return ONLY valid JSON in EXACTLY this structure (no markdown, no extra text):

{
  "score": 0-100,
  "issues": [
    { "title": "...", "impact": "...", "priority": "High|Medium|Low" }
  ],
  "recommendations": [
    "Concise recommendation ...",
    "..."
  ],
  "fixes": [
    { "title": "...", "how_to_fix": "One or two sentences" }
  ],
  "competitor": {
    "note": "Short summary of category & key differences",
    "items": [
      { "name": "CompetitorName", "url": "https://...", "comparison": "1 short line" }
    ]
  }
}

Rules:
- JSON only. No code fences. No markdown.
- Keep titles ≤ 60 chars; keep lines concise.
- Be realistic; do not invent data not inferable from HTML.
- If competitor HTML not provided, infer 2–4 well-known competitors from context/category and add them in "items".

WEBSITE_URL: $url
WEBSITE_HTML:
$html

${if (compUrl.isNotEmpty()) "COMPETITOR_URL: $compUrl\nCOMPETITOR_HTML:\n$compHtml" else ""}
        """.trimIndent()
    }

    private fun callGemini(prompt: String): String {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                // Prefer IPv4 like your other screens
                .dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        return try {
                            java.net.InetAddress.getAllByName(hostname).filter { it is java.net.Inet4Address }
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

            client.newCall(req).execute().use { resp ->
                val responseString = resp.body?.string().orEmpty()
                Log.d(TAG, "Gemini API raw response: $responseString")
                JSONObject(responseString)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "{}")
                    ?: "{}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "callGemini failed: ${e.message}", e)
            "{}"
        }
    }

    private fun cleanGeminiJson(raw: String): String {
        return raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "", ignoreCase = true)
            .trim()
    }

    private fun safeParseJson(s: String): JSONObject {
        return try { JSONObject(s) } catch (_: Exception) {
            // Provide a safe shell to avoid crashes even if AI returns malformed JSON.
            JSONObject().apply {
                put("score", 50)
                put("issues", JSONArray())
                put("recommendations", JSONArray())
                put("fixes", JSONArray())
                put("competitor", JSONObject().apply {
                    put("note", "No competitor data parsed.")
                    put("items", JSONArray())
                })
            }
        }
    }

    // -------------------------
    // PDF builder (footer on EVERY page)
    // -------------------------
    private fun buildPdf(title: String, header: String, ai: JSONObject): ByteArray {
        val pdf = android.graphics.pdf.PdfDocument()

        // A4 ~ 72dpi
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            color = Color.BLACK
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = Color.BLACK
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            color = Color.BLACK
        }
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.DKGRAY
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22000000") // light transparent gray
            textSize = 50f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        var pageNum = 1
        var page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var cursorY = margin.toFloat()
        val contentWidth = pageWidth - margin * 2

        fun drawWatermark() {
            canvas.save()
            canvas.rotate(-30f, (pageWidth / 2).toFloat(), (pageHeight / 2).toFloat())
            canvas.drawText("Report by SEOmate", (pageWidth / 2).toFloat(), (pageHeight / 2).toFloat(), watermarkPaint)
            canvas.restore()
        }

        fun finishPage() {
            drawWatermark() // watermark on every page
            pdf.finishPage(page)
        }

        fun newPageIfNeeded(linesNeeded: Int = 1, lineHeight: Float = 16f) {
            val needed = linesNeeded * lineHeight
            if (cursorY + needed + 40 > (pageHeight - margin)) {
                finishPage()
                pageNum += 1
                page = pdf.startPage(
                    android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                )
                canvas = page.canvas
                cursorY = margin.toFloat()
            }
        }

        fun drawWrappedText(text: String, paint: Paint, maxWidth: Int) {
            val words = text.split(" ")
            val lh = paint.textSize + 4
            var line = StringBuilder()
            for (w in words) {
                val test = if (line.isEmpty()) w else "${line} $w"
                if (paint.measureText(test) <= maxWidth) {
                    if (line.isEmpty()) line.append(w) else line.append(" ").append(w)
                } else {
                    newPageIfNeeded(1, lh)
                    canvas.drawText(line.toString(), margin.toFloat(), cursorY, paint)
                    cursorY += lh
                    line = StringBuilder(w)
                }
            }
            if (line.isNotEmpty()) {
                newPageIfNeeded(1, lh)
                canvas.drawText(line.toString(), margin.toFloat(), cursorY, paint)
                cursorY += lh
            }
        }

        fun drawSectionTitle(titleText: String) {
            newPageIfNeeded(1, 20f)
            canvas.drawText(titleText, margin.toFloat(), cursorY, sectionPaint)
            cursorY += 20f
            canvas.drawLine(margin.toFloat(), cursorY, (pageWidth - margin).toFloat(), cursorY, linePaint)
            cursorY += 12f
        }

        // Title
        canvas.drawText(title, margin.toFloat(), cursorY, titlePaint)
        cursorY += 28f

        // Header block
        drawWrappedText(header, bodyPaint, contentWidth)
        cursorY += 16f

        // SCORE
        val score = ai.optInt("score", 50)
        drawSectionTitle("Score")
        drawWrappedText("$score / 100", bodyPaint.apply { textSize = 14f }, contentWidth)
        bodyPaint.textSize = 12f
        cursorY += 10f

        // ISSUES
        drawSectionTitle("Issues")
        val issues = ai.optJSONArray("issues") ?: JSONArray()
        if (issues.length() == 0) {
            drawWrappedText("No critical issues detected.", bodyPaint, contentWidth)
        } else {
            for (i in 0 until issues.length()) {
                val item = issues.optJSONObject(i) ?: continue
                val titleT = item.optString("title").ifBlank { "Issue" }
                val impact = item.optString("impact")
                val priority = item.optString("priority", "Medium").uppercase(Locale.US)

                drawWrappedText("• $titleT", bodyPaint, contentWidth)
                if (impact.isNotBlank()) drawWrappedText("  – $impact", bodyPaint, contentWidth)
                drawWrappedText("  [$priority]", chipPaint, contentWidth)
                cursorY += 6f
            }
        }
        cursorY += 10f

        // RECOMMENDATIONS
        drawSectionTitle("Recommendations")
        val recs = ai.optJSONArray("recommendations") ?: JSONArray()
        if (recs.length() == 0) {
            drawWrappedText("No recommendations available.", bodyPaint, contentWidth)
        } else {
            for (i in 0 until recs.length()) {
                val line = recs.optString(i)
                drawWrappedText("• $line", bodyPaint, contentWidth)
            }
        }
        cursorY += 10f

        // FIX ERRORS
        drawSectionTitle("Fix Errors")
        val fixes = ai.optJSONArray("fixes") ?: JSONArray()
        if (fixes.length() == 0) {
            drawWrappedText("No urgent fixes provided.", bodyPaint, contentWidth)
        } else {
            for (i in 0 until fixes.length()) {
                val fx = fixes.optJSONObject(i) ?: continue
                val t = fx.optString("title").ifBlank { "Fix" }
                val how = fx.optString("how_to_fix")
                drawWrappedText("• $t", bodyPaint, contentWidth)
                if (how.isNotBlank()) drawWrappedText("  – $how", bodyPaint, contentWidth)
            }
        }
        cursorY += 10f

        // COMPETITOR
        drawSectionTitle("Competitor")
        val competitorObj = ai.optJSONObject("competitor") ?: JSONObject()
        val compNote = competitorObj.optString("note")
        if (compNote.isNotBlank()) drawWrappedText(compNote, bodyPaint, contentWidth)
        val compItems = competitorObj.optJSONArray("items") ?: JSONArray()
        if (compItems.length() == 0) {
            drawWrappedText("No competitor items available.", bodyPaint, contentWidth)
        } else {
            for (i in 0 until compItems.length()) {
                val c = compItems.optJSONObject(i) ?: continue
                val name = c.optString("name", "Competitor")
                val url = c.optString("url", "")
                val comp = c.optString("comparison", "")
                drawWrappedText("• $name ${if (url.isNotBlank()) "($url)" else ""}", bodyPaint, contentWidth)
                if (comp.isNotBlank()) drawWrappedText("  – $comp", bodyPaint, contentWidth)
            }
        }

        // Finish last page
        finishPage()

        val bos = ByteArrayOutputStream()
        pdf.writeTo(bos)
        pdf.close()
        return bos.toByteArray()
    }


    // Save helpers
    private fun saveToDownloads(fileName: String, mime: String, bytes: ByteArray): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = requireContext().contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to create download entry")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            if (!downloads.exists()) downloads.mkdirs()
            val outFile = File(downloads, fileName)
            outFile.outputStream().use { it.write(bytes) }
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                outFile
            )
        }
    }

    // Utils
    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private fun timeStamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
