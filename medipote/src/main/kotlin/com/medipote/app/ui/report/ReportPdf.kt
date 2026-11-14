package com.medipote.app.ui.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.medipote.app.domain.DoctorReport
import com.medipote.app.domain.ReportBlock
import java.io.File
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Draws a [DoctorReport] on A4 pages. */
object ReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 42f
    private const val CONTENT = W - 2 * M
    private val ACCENT = Color.rgb(0x1F, 0x7A, 0x66)
    private val MUTED = Color.rgb(0x5F, 0x66, 0x6B)
    private val LINE = Color.rgb(0xD5, 0xDB, 0xDE)
    private val TINT = Color.rgb(0xEE, 0xF4, 0xF2)
    private val SECOND = Color.rgb(0x6A, 0x4C, 0x9C)

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.rgb(0x1B, 0x1F, 0x22)) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
    }

    private val title = paint(19f, true)
    private val section = paint(13.5f, true, ACCENT)
    private val subtitle = paint(11f, true)
    private val body = paint(9.5f)
    private val bodyBold = paint(9.5f, true)
    private val muted = paint(8.5f, color = MUTED)

    private fun layout(text: String, p: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.5f, 1f)
            .build()

    /** Where pages are drawn: a PDF document on the phone, bitmaps in tests. */
    interface Pages {
        fun start(number: Int): Canvas
        fun finish()
    }

    private class PdfPages(val doc: PdfDocument) : Pages {
        private var page: PdfDocument.Page? = null
        override fun start(number: Int): Canvas = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create()).also { page = it }.canvas
        override fun finish() {
            page?.let(doc::finishPage)
            page = null
        }
    }

    /** Draws pages into bitmaps (A4 at [scale] pixels per point), white background. */
    class BitmapPages(private val scale: Float = 1.5f) : Pages {
        val bitmaps = mutableListOf<android.graphics.Bitmap>()
        override fun start(number: Int): Canvas {
            val b = android.graphics.Bitmap.createBitmap((W * scale).toInt(), (H * scale).toInt(), android.graphics.Bitmap.Config.ARGB_8888)
            b.eraseColor(Color.WHITE)
            bitmaps += b
            return Canvas(b).apply { scale(scale, scale) }
        }
        override fun finish() = Unit
    }

    private class Writer(val pages: Pages, val footer: String) {
        private var current: Canvas? = null
        var number = 0
        var y = 0f
        val canvas: Canvas get() = current!!

        fun newPage() {
            if (current != null) finish()
            number++
            current = pages.start(number)
            y = M
        }

        private fun finish() {
            val c = canvas
            val f = layout("$footer · page $number", muted, CONTENT)
            c.save()
            c.translate(M, H - M + 10)
            f.draw(c)
            c.restore()
            pages.finish()
            current = null
        }

        fun close() {
            if (current != null) finish()
        }

        /** Starts a new page when [height] doesn't fit in what's left of this one. */
        fun need(height: Float) {
            if (current == null || y + height > H - M - 10) newPage()
        }

        fun draw(l: StaticLayout, x: Float = M) {
            canvas.save()
            canvas.translate(x, y)
            l.draw(canvas)
            canvas.restore()
        }
    }

    fun render(report: DoctorReport, file: File) {
        val doc = PdfDocument()
        try {
            draw(report, PdfPages(doc))
            file.parentFile?.mkdirs()
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    fun draw(report: DoctorReport, pages: Pages) {
        val w = Writer(pages, "MédiPote · données saisies par la personne, à interpréter par un professionnel de santé")
        w.newPage()
        // Header
        val t = layout(report.title, title, CONTENT)
        w.draw(t)
        w.y += t.height + 4
        val p = layout(report.period, bodyBold, CONTENT)
        w.draw(p)
        w.y += p.height + 2
        val g = layout(report.generated, muted, CONTENT)
        w.draw(g)
        w.y += g.height + 8
        w.canvas.drawRect(M, w.y, M + CONTENT, w.y + 2, Paint().apply { color = ACCENT })
        w.y += 14

        for (s in report.sections) {
            val head = layout(s.title, section, CONTENT)
            w.need(head.height + 40f)
            w.draw(head)
            w.y += head.height + 6
            for (b in s.blocks) block(w, b)
            w.y += 10
        }
        w.close()
    }

    private fun block(w: Writer, b: ReportBlock) {
        when (b) {
            is ReportBlock.Subtitle -> {
                val l = layout(b.text, subtitle, CONTENT)
                w.need(l.height + 30f)
                w.y += 2
                w.draw(l)
                w.y += l.height + 4
            }
            is ReportBlock.Paragraph -> {
                val l = layout(b.text, if (b.muted) muted else body, CONTENT)
                w.need(l.height.toFloat())
                w.draw(l)
                w.y += l.height + 4
            }
            is ReportBlock.Facts -> {
                val labelW = CONTENT * 0.36f
                for ((label, value) in b.items) {
                    val ll = layout(label, muted, labelW - 8)
                    val vl = layout(value, body, CONTENT - labelW)
                    val h = maxOf(ll.height, vl.height).toFloat()
                    w.need(h + 4)
                    w.draw(ll)
                    w.draw(vl, M + labelW)
                    w.y += h + 4
                }
                w.y += 2
            }
            is ReportBlock.Table -> table(w, b)
            is ReportBlock.Chart -> chart(w, b)
        }
    }

    private fun table(w: Writer, t: ReportBlock.Table) {
        val total = t.weights.sum()
        val widths = t.weights.map { CONTENT * it / total }
        val pad = 4f
        fun row(cells: List<String>, p: TextPaint, fill: Int?) {
            val layouts = cells.mapIndexed { i, c -> layout(c, p, widths[i] - 2 * pad) }
            val h = layouts.maxOf { it.height } + 2 * pad
            w.need(h)
            fill?.let { w.canvas.drawRect(M, w.y, M + CONTENT, w.y + h, Paint().apply { color = it }) }
            var x = M
            layouts.forEachIndexed { i, l ->
                w.canvas.save()
                w.canvas.translate(x + pad, w.y + pad)
                l.draw(w.canvas)
                w.canvas.restore()
                x += widths[i]
            }
            w.canvas.drawLine(M, w.y + h, M + CONTENT, w.y + h, Paint().apply { color = LINE; strokeWidth = 0.6f })
            w.y += h
        }
        row(t.headers, bodyBold, TINT)
        t.rows.forEach { row(it, body, null) }
        w.y += 8
    }

    private fun chart(w: Writer, c: ReportBlock.Chart) {
        val h = 110f
        w.need(h + 26)
        val canvas = w.canvas
        val top = w.y
        val left = M
        val right = M + CONTENT - 40
        val values = c.points.flatMap { it.second }
        var lo = values.min()
        var hi = values.max()
        c.band?.let { lo = minOf(lo, it.start); hi = maxOf(hi, it.endInclusive) }
        val pad = ((hi - lo) * 0.1).coerceAtLeast(0.5)
        lo -= pad
        hi += pad
        fun y(v: Double) = (top + h * (1 - (v - lo) / (hi - lo))).toFloat()
        val t0 = c.points.first().first
        val span = Duration.between(t0, c.points.last().first).toMinutes().toFloat().coerceAtLeast(1f)
        fun x(i: Int) = left + (right - left) * Duration.between(t0, c.points[i].first).toMinutes() / span
        c.band?.let { canvas.drawRect(left, y(it.endInclusive), right, y(it.start), Paint().apply { color = Color.rgb(0xE3, 0xF3, 0xEA) }) }
        val grid = Paint().apply { color = LINE; strokeWidth = 0.6f }
        canvas.drawLine(left, top + h, right, top + h, grid)
        canvas.drawLine(left, top, right, top, grid)
        val seriesCount = c.points.maxOf { it.second.size }
        for (s in 0 until seriesCount) {
            val color = if (s == 0) ACCENT else SECOND
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 1.4f; style = Paint.Style.STROKE }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val path = Path()
            var started = false
            c.points.forEachIndexed { i, (_, v) ->
                val value = v.getOrNull(s) ?: return@forEachIndexed
                if (!started) { path.moveTo(x(i), y(value)); started = true } else path.lineTo(x(i), y(value))
                canvas.drawCircle(x(i), y(value), 1.8f, dot)
            }
            canvas.drawPath(path, line)
        }
        val label = paint(7.5f, color = MUTED)
        canvas.drawText(c.format(hi - pad), right + 4, top + 8, label)
        canvas.drawText(c.format(lo + pad), right + 4, top + h, label)
        val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
        canvas.drawText(c.points.first().first.format(fmt), left, top + h + 11, label)
        val end = c.points.last().first.format(fmt)
        canvas.drawText(end, right - label.measureText(end), top + h + 11, label)
        w.y += h + 22
    }
}
