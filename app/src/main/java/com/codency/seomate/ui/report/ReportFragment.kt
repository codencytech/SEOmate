package com.codency.seomate.ui.report

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ReportFragment : Fragment() {

    private val TAG = "GenReport"

    private lateinit var inputUrl: EditText
    private lateinit var btnGenerate: Button
    private lateinit var btnPreview: Button
    private lateinit var progressBar: ProgressBar
    private var spinnerFormat: Spinner? = null // optional (PDF default)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "Inflating Report layout...")
        val v = inflater.inflate(R.layout.fragment_report, container, false)

        inputUrl = v.findViewById(R.id.inputUrl)
        spinnerFormat = v.findViewById(R.id.spinnerExport)
        btnGenerate = v.findViewById(R.id.btnGenerateReport)
        btnPreview = v.findViewById(R.id.btnPreviewReport)
        progressBar = v.findViewById(R.id.progressBar)

        btnGenerate.setOnClickListener { onGenerate(previewOnly = false) }
        btnPreview.setOnClickListener { onGenerate(previewOnly = true) }

        Log.d(TAG, "Layout inflated successfully.")
        return v
    }

    private fun onGenerate(previewOnly: Boolean) {
        val rawUrl = inputUrl.text.toString().trim()

        if (rawUrl.isEmpty()) {
            toast("Please enter website URL")
            return
        }

        val normalizedDomain = normalizeDomain(rawUrl)
        val fullUrl = ensureHttpScheme(rawUrl)
        Log.d(TAG, "URL in: $rawUrl | full: $fullUrl | domain: $normalizedDomain")

        val chosenFormat = (spinnerFormat?.selectedItem?.toString() ?: "PDF").uppercase(Locale.US)

        progressBar.visibility = View.VISIBLE
        btnGenerate.isEnabled = false
        btnPreview.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1) Fetch HTML
                val html = fetchWebsiteHtml(fullUrl)
                Log.d(TAG, "Fetched HTML length: ${html.length}")

                // 2) Basic local metrics
                val metrics = computeBasicSeoMetrics(html)

                // 3) Build report text
                val now = Date()
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(now)

                val title = extractTitle(html)
                val metaDesc = extractMetaDescription(html)
                val h1Count = countTag(html, "h1")
                val imgCount = countTag(html, "img")

                val reportTitle = "SEO Report - $normalizedDomain"
                val reportHeader =
                    "Website: $fullUrl\nDate: $dateStr\nKeywords: None"
                val quickStats =
                    listOf(
                        "Page Title: ${if (title.isEmpty()) "N/A" else truncate(title, 100)}",
                        "Meta Description: ${if (metaDesc.isEmpty()) "N/A" else truncate(metaDesc, 140)}",
                        "H1 Count: $h1Count",
                        "Image Count: $imgCount",
                        "Text Size (chars): ${metrics.textLength}",
                        "Links Found: ${metrics.linkCount}"
                    )

                when (chosenFormat) {
                    "CSV" -> {
                        val csv = buildCsv(reportTitle, reportHeader, quickStats)
                        val name = "SEOmate_Report_${normalizedDomain}_${timeStamp()}.csv"
                        val uri = saveToDownloads(name, "text/csv", csv.toByteArray())
                        withContext(Dispatchers.Main) {
                            doneUI("CSV saved to Downloads.", previewOnly, uri, "text/csv")
                        }
                    }
                    "TXT" -> {
                        val txt = buildTxt(reportTitle, reportHeader, quickStats)
                        val name = "SEOmate_Report_${normalizedDomain}_${timeStamp()}.txt"
                        val uri = saveToDownloads(name, "text/plain", txt.toByteArray())
                        withContext(Dispatchers.Main) {
                            doneUI("TXT saved to Downloads.", previewOnly, uri, "text/plain")
                        }
                    }
                    else -> {
                        // PDF (default)
                        val pdfBytes = buildPdf(reportTitle, reportHeader, quickStats)
                        val name = "SEOmate_Report_${normalizedDomain}_${timeStamp()}.pdf"
                        val uri = saveToDownloads(name, "application/pdf", pdfBytes)
                        withContext(Dispatchers.Main) {
                            doneUI("PDF saved to Downloads.", previewOnly, uri, "application/pdf")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generate failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnGenerate.isEnabled = true
                    btnPreview.isEnabled = true
                    toast("Failed to generate report")
                }
            }
        }
    }

    // -------------------------
    // Networking & parsing
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

    data class SeoBasics(
        val textLength: Int,
        val linkCount: Int
    )

    private fun computeBasicSeoMetrics(html: String): SeoBasics {
        val textOnly = html.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
        val linkCount = Regex("<a\\b", RegexOption.IGNORE_CASE).findAll(html).count()
        return SeoBasics(textLength = textOnly.trim().length, linkCount = linkCount)
    }

    private fun extractTitle(html: String): String {
        val m = Regex(
            "<title>(.*?)</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)
        return m?.groupValues?.getOrNull(1)?.trim()?.replace("\\s+".toRegex(), " ") ?: ""
    }

    private fun extractMetaDescription(html: String): String {
        val r = Regex(
            "<title>(.*?)</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)
        return r?.groupValues?.getOrNull(1)?.trim()?.replace("\\s+".toRegex(), " ") ?: ""
    }

    private fun countTag(html: String, tag: String): Int {
        return Regex("<$tag\\b", RegexOption.IGNORE_CASE).findAll(html).count()
    }

    // -------------------------
    // CSV / TXT builders
    // -------------------------
    private fun buildCsv(title: String, header: String, stats: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("\"$title\"")
        header.split("\n").forEach { sb.appendLine("\"$it\"") }
        sb.appendLine()
        sb.appendLine("Metric,Value")
        stats.forEach {
            val parts = it.split(":", limit = 2)
            val key = parts.getOrNull(0)?.trim() ?: it
            val value = parts.getOrNull(1)?.trim() ?: ""
            sb.appendLine("\"$key\",\"$value\"")
        }
        sb.appendLine()
        sb.appendLine("\"Report generated by SEOmate\"")
        return sb.toString()
    }

    private fun buildTxt(title: String, header: String, stats: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine(title)
        sb.appendLine(header)
        sb.appendLine()
        sb.appendLine("Quick Stats")
        stats.forEach { sb.appendLine("- $it") }
        sb.appendLine()
        sb.appendLine("Report generated by SEOmate")
        return sb.toString()
    }

    // -------------------------
    // PDF builder (footer on EVERY page)
    // -------------------------
    private fun buildPdf(title: String, header: String, stats: List<String>): ByteArray {
        val pdf = android.graphics.pdf.PdfDocument()

        // A4 at ~72dpi
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36
        val footerText = "Report generated by SEOmate"

        // Paints
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
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.DKGRAY
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var pageNum = 1
        var page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas

        fun finishPage() {
            val footerY = pageHeight - margin / 2f
            canvas.drawLine(margin.toFloat(), footerY - 10, (pageWidth - margin).toFloat(), footerY - 10, linePaint)
            canvas.drawText(footerText, margin.toFloat(), footerY, footerPaint)
            pdf.finishPage(page)
        }

        var cursorY = margin.toFloat()
        val contentWidth = pageWidth - margin * 2

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
            val lineHeight = paint.textSize + 4
            var line = StringBuilder()
            for (w in words) {
                val test = if (line.isEmpty()) w else "${line} $w"
                if (paint.measureText(test) <= maxWidth) {
                    if (line.isEmpty()) line.append(w) else line.append(" ").append(w)
                } else {
                    newPageIfNeeded(1, lineHeight)
                    canvas.drawText(line.toString(), margin.toFloat(), cursorY, paint)
                    cursorY += lineHeight
                    line = StringBuilder(w)
                }
            }
            if (line.isNotEmpty()) {
                newPageIfNeeded(1, lineHeight)
                canvas.drawText(line.toString(), margin.toFloat(), cursorY, paint)
                cursorY += lineHeight
            }
        }

        // Title
        canvas.drawText(title, margin.toFloat(), cursorY, titlePaint)
        cursorY += 24f

        // Header block
        drawWrappedText(header, bodyPaint, contentWidth)
        cursorY += 6f
        canvas.drawLine(margin.toFloat(), cursorY, (pageWidth - margin).toFloat(), cursorY, linePaint)
        cursorY += 14f

        // Section: Quick Stats
        canvas.drawText("Quick Stats", margin.toFloat(), cursorY, sectionPaint)
        cursorY += 18f
        for (item in stats) {
            drawWrappedText("• $item", bodyPaint, contentWidth)
        }

        // End
        finishPage()

        val bos = ByteArrayOutputStream()
        pdf.writeTo(bos)
        pdf.close()
        return bos.toByteArray()
    }

    // -------------------------
    // Save & preview helpers
    // -------------------------
    private fun saveToDownloads(fileName: String, mime: String, bytes: ByteArray): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                // make sure it appears in Downloads
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

    private fun doneUI(message: String, preview: Boolean, uri: Uri, mime: String) {
        progressBar.visibility = View.GONE
        btnGenerate.isEnabled = true
        btnPreview.isEnabled = true
        toast(message)

        if (preview) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                toast("No app found to open the file.")
            }
        }
    }

    // -------------------------
    // Utils
    // -------------------------
    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.substring(0, max - 1) + "…"

    private fun timeStamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
