package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.local.CustomerEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.ProductEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class StatementRow(
    val ts: Long,
    val kind: String, // "open", "sale", "pay", "add"
    val saleId: String?,
    val invoiceNumber: Int?,
    val isPaidSale: Boolean,
    val total: Double,
    val label: String,
    val debit: Double,
    val credit: Double,
    val balance: Double
)

data class ReportTableData(
    val title: String,
    val fileName: String,
    val columns: List<ReportColumn>,
    val rows: List<List<String>>,
    val totals: List<String>
)

data class ReportColumn(
    val header: String,
    val widthRatio: Float,
    val alignCenter: Boolean = false
)

object PdfReportGenerator {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 34f

    private fun tajawalTypeface(context: Context): Typeface {
        return ResourcesCompat.getFont(context, R.font.tajawal) ?: Typeface.DEFAULT
    }

    fun generateCustomerStatementPdf(
        context: Context,
        shopName: String,
        currency: String,
        customer: CustomerEntity,
        rows: List<StatementRow>,
        totalPurchases: Double,
        totalPaid: Double
    ): ByteArray {
        val doc = PdfDocument()
        val tf = tajawalTypeface(context)
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = 10.5f
            color = Color.parseColor("#0E1B3A")
        }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(tf, Typeface.BOLD)
            textSize = 11f
            color = Color.parseColor("#0E1B3A")
        }

        val pageRect = RectF(0f, 0f, PAGE_W.toFloat(), PAGE_H.toFloat())
        val contentRight = PAGE_W - MARGIN
        val contentWidth = PAGE_W - 2 * MARGIN

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        fun drawFooter(c: Canvas, pNum: Int) {
            regular.color = Color.parseColor("#8A93A8")
            regular.textAlign = Paint.Align.CENTER
            regular.textSize = 9f
            c.drawText("كاشيرك الذكي · صفحة $pNum", PAGE_W / 2f, PAGE_H - 22f, regular)
        }

        fun drawTableHeader(c: Canvas, topY: Float): Float {
            val bg = Paint().apply { color = Color.parseColor("#EEF0F5") }
            c.drawRect(MARGIN, topY, contentRight, topY + 26f, bg)
            bold.textSize = 10f
            bold.color = Color.parseColor("#44506B")
            bold.textAlign = Paint.Align.RIGHT
            c.drawText("التاريخ", contentRight - 6f, topY + 17f, bold)
            c.drawText("البيان", contentRight - 82f, topY + 17f, bold)
            bold.textAlign = Paint.Align.CENTER
            c.drawText("مدين", MARGIN + 150f, topY + 17f, bold)
            c.drawText("دائن", MARGIN + 92f, topY + 17f, bold)
            c.drawText("الرصيد", MARGIN + 34f, topY + 17f, bold)
            return topY + 26f
        }

        // Navy Header Band
        val navyPaint = Paint().apply { color = Color.parseColor("#0E2147") }
        val goldPaint = Paint().apply { color = Color.parseColor("#DCB54E") }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 96f, navyPaint)
        canvas.drawRect(0f, 96f, PAGE_W.toFloat(), 99f, goldPaint)

        bold.color = Color.WHITE
        bold.textSize = 20f
        bold.textAlign = Paint.Align.RIGHT
        canvas.drawText(shopName.ifBlank { "المحل" }, contentRight, 44f, bold)

        bold.color = Color.parseColor("#DCB54E")
        bold.textSize = 13f
        canvas.drawText("كشف حساب زبون", contentRight, 68f, bold)

        bold.textAlign = Paint.Align.LEFT
        canvas.drawText("كاشيرك الذكي", MARGIN, 44f, bold)
        regular.color = Color.parseColor("#C4CDE2")
        regular.textSize = 10f
        regular.textAlign = Paint.Align.LEFT
        canvas.drawText("تاريخ الكشف: ${Formatters.shortDate(System.currentTimeMillis())}", MARGIN, 68f, regular)

        y = 128f
        bold.color = Color.parseColor("#0E1B3A")
        bold.textSize = 18f
        bold.textAlign = Paint.Align.RIGHT
        canvas.drawText(customer.name, contentRight, y, bold)
        y += 16f

        regular.color = Color.parseColor("#6B7385")
        regular.textSize = 11f
        regular.textAlign = Paint.Align.RIGHT
        if (customer.phone.isNotBlank()) {
            y += 14f
            canvas.drawText("الهاتف: ${customer.phone}", contentRight, y, regular)
        }
        if (customer.note.isNotBlank()) {
            y += 14f
            canvas.drawText("ملاحظات: ${customer.note}", contentRight, y, regular)
        }

        y += 18f
        val boxGap = 10f
        val boxW = (contentWidth - 2 * boxGap) / 3f
        val tiles = listOf(
            Triple("الرصيد المستحق", "${Formatters.money(customer.debt)} $currency", true),
            Triple("إجمالي المشتريات", "${Formatters.money(totalPurchases)} $currency", false),
            Triple("إجمالي المدفوع", "${Formatters.money(totalPaid)} $currency", false)
        )
        tiles.forEachIndexed { idx, (lbl, valStr, hero) ->
            val bx = contentRight - boxW * (idx + 1) - boxGap * idx
            val r = RectF(bx, y, bx + boxW, y + 54f)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (hero) Color.parseColor("#0E2147") else Color.parseColor("#F4F2EE")
            }
            canvas.drawRoundRect(r, 10f, 10f, fill)
            regular.color = if (hero) Color.parseColor("#C4CDE2") else Color.parseColor("#6B7385")
            regular.textSize = 9.5f
            regular.textAlign = Paint.Align.RIGHT
            canvas.drawText(lbl, bx + boxW - 10f, y + 18f, regular)
            bold.color = if (hero) Color.parseColor("#DCB54E") else Color.parseColor("#0E1B3A")
            bold.textSize = 14f
            bold.textAlign = Paint.Align.RIGHT
            canvas.drawText(valStr, bx + boxW - 10f, y + 40f, bold)
        }

        y += 72f
        y = drawTableHeader(canvas, y)

        if (rows.isEmpty()) {
            regular.color = Color.parseColor("#6B7385")
            regular.textAlign = Paint.Align.CENTER
            regular.textSize = 11f
            canvas.drawText("لا توجد حركات", PAGE_W / 2f, y + 28f, regular)
            y += 42f
        } else {
            val linePaint = Paint().apply { color = Color.parseColor("#E5E1D6") }
            for (row in rows) {
                val wrapped = wrapText(row.label, regular.apply { textSize = 10f }, 195f)
                val rowH = maxOf(28f, wrapped.size * 14f + 12f)
                if (y + rowH > PAGE_H - 60f) {
                    drawFooter(canvas, pageNum)
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                    canvas = page.canvas
                    y = MARGIN
                    y = drawTableHeader(canvas, y)
                }
                val base = y + 18f
                regular.textSize = 9.5f
                regular.color = Color.parseColor("#44506B")
                regular.textAlign = Paint.Align.RIGHT
                canvas.drawText(Formatters.shortDate(row.ts), contentRight - 6f, base, regular)

                regular.color = Color.parseColor("#0E1B3A")
                wrapped.forEachIndexed { idx, line ->
                    canvas.drawText(line, contentRight - 82f, base + idx * 14f, regular)
                }

                regular.textAlign = Paint.Align.CENTER
                regular.color = if (row.debit > 0 && row.credit == 0.0) Color.parseColor("#B3261E") else Color.parseColor("#0E1B3A")
                canvas.drawText(if (row.debit > 0) Formatters.money(row.debit) else "-", MARGIN + 150f, base, regular)

                regular.color = if (row.credit > 0) Color.parseColor("#1F7A47") else Color.parseColor("#0E1B3A")
                canvas.drawText(if (row.credit > 0) Formatters.money(row.credit) else "-", MARGIN + 92f, base, regular)

                bold.textSize = 10f
                bold.textAlign = Paint.Align.CENTER
                bold.color = Color.parseColor("#0E1B3A")
                canvas.drawText(Formatters.money(row.balance), MARGIN + 34f, base, bold)

                canvas.drawLine(MARGIN, y + rowH - 1f, contentRight, y + rowH - 1f, linePaint)
                y += rowH
            }
        }

        if (y + 52f > PAGE_H - 50f) {
            drawFooter(canvas, pageNum)
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            y = MARGIN
        }
        y += 14f
        canvas.drawRoundRect(RectF(MARGIN, y, contentRight, y + 38f), 9f, 9f, navyPaint)
        bold.color = Color.WHITE
        bold.textSize = 12f
        bold.textAlign = Paint.Align.RIGHT
        canvas.drawText("الرصيد المستحق", contentRight - 14f, y + 24f, bold)
        bold.color = Color.parseColor("#DCB54E")
        bold.textSize = 15f
        bold.textAlign = Paint.Align.LEFT
        canvas.drawText("${Formatters.money(customer.debt)} $currency", MARGIN + 14f, y + 25f, bold)

        drawFooter(canvas, pageNum)
        doc.finishPage(page)

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    fun generateTableReportPdf(
        context: Context,
        shopName: String,
        data: ReportTableData
    ): ByteArray {
        val doc = PdfDocument()
        val tf = tajawalTypeface(context)
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = 10f
            color = Color.parseColor("#0E1B3A")
        }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(tf, Typeface.BOLD)
            textSize = 10.5f
            color = Color.parseColor("#0E1B3A")
        }

        val contentRight = PAGE_W - MARGIN
        val contentWidth = PAGE_W - 2 * MARGIN
        val totalWeight = data.columns.sumOf { it.widthRatio.toDouble() }.toFloat().coerceAtLeast(1f)
        val edges = mutableListOf<Pair<Float, Float>>()
        var cx = contentRight
        for (col in data.columns) {
            val w = contentWidth * (col.widthRatio / totalWeight)
            edges.add(cx to (cx - w))
            cx -= w
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        fun drawFooter(c: Canvas, pNum: Int) {
            regular.color = Color.parseColor("#8A93A8")
            regular.textAlign = Paint.Align.CENTER
            regular.textSize = 9f
            c.drawText("كاشيرك الذكي · صفحة $pNum", PAGE_W / 2f, PAGE_H - 22f, regular)
        }

        fun drawCell(text: String, colIdx: Int, baselineY: Float, paint: Paint) {
            val (r, l) = edges[colIdx]
            val col = data.columns[colIdx]
            val maxW = (r - l) - 10f
            val fitted = fitText(text, paint, maxW)
            if (col.alignCenter) {
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(fitted, (r + l) / 2f, baselineY, paint)
            } else {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(fitted, r - 6f, baselineY, paint)
            }
        }

        fun drawHeaderRow(topY: Float): Float {
            val bg = Paint().apply { color = Color.parseColor("#EEF0F5") }
            canvas.drawRect(MARGIN, topY, contentRight, topY + 26f, bg)
            bold.textSize = 10f
            bold.color = Color.parseColor("#44506B")
            data.columns.forEachIndexed { idx, col -> drawCell(col.header, idx, topY + 17f, bold) }
            return topY + 26f
        }

        val navyPaint = Paint().apply { color = Color.parseColor("#0E2147") }
        val goldPaint = Paint().apply { color = Color.parseColor("#DCB54E") }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 94f, navyPaint)
        canvas.drawRect(0f, 94f, PAGE_W.toFloat(), 97f, goldPaint)

        bold.color = Color.WHITE
        bold.textSize = 20f
        bold.textAlign = Paint.Align.RIGHT
        canvas.drawText(shopName.ifBlank { "المحل" }, contentRight, 42f, bold)
        bold.color = Color.parseColor("#DCB54E")
        bold.textSize = 13f
        canvas.drawText(data.title, contentRight, 66f, bold)
        bold.textAlign = Paint.Align.LEFT
        canvas.drawText("كاشيرك الذكي", MARGIN, 42f, bold)
        regular.color = Color.parseColor("#C4CDE2")
        regular.textSize = 10f
        regular.textAlign = Paint.Align.LEFT
        canvas.drawText("التاريخ: ${Formatters.shortDate(System.currentTimeMillis())}", MARGIN, 66f, regular)

        y = 118f
        y = drawHeaderRow(y)

        val linePaint = Paint().apply { color = Color.parseColor("#E5E1D6") }
        if (data.rows.isEmpty()) {
            regular.color = Color.parseColor("#6B7385")
            regular.textAlign = Paint.Align.CENTER
            canvas.drawText("لا توجد بيانات", PAGE_W / 2f, y + 28f, regular)
            y += 42f
        } else {
            for (row in data.rows) {
                if (y + 28f > PAGE_H - 72f) {
                    drawFooter(canvas, pageNum)
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                    canvas = page.canvas
                    y = MARGIN
                    y = drawHeaderRow(y)
                }
                regular.color = Color.parseColor("#0E1B3A")
                regular.textSize = 10f
                row.forEachIndexed { idx, value -> drawCell(value, idx, y + 18f, regular) }
                canvas.drawLine(MARGIN, y + 27f, contentRight, y + 27f, linePaint)
                y += 28f
            }
        }

        if (y + 42f > PAGE_H - 50f) {
            drawFooter(canvas, pageNum)
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            y = MARGIN
        }
        canvas.drawRect(MARGIN, y + 6f, contentRight, y + 36f, navyPaint)
        bold.color = Color.parseColor("#DCB54E")
        bold.textSize = 10.5f
        data.totals.forEachIndexed { idx, value ->
            if (value.isNotBlank()) drawCell(value, idx, y + 25f, bold)
        }

        drawFooter(canvas, pageNum)
        doc.finishPage(page)

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    fun buildInventoryReportData(products: List<ProductEntity>): ReportTableData {
        val totalCost = products.sumOf { it.cost * it.stock }
        val totalSell = products.sumOf { it.price * it.stock }
        val totalStock = products.sumOf { it.stock }
        return ReportTableData(
            title = "كشف الجرد الكامل",
            fileName = "كشف الجرد.pdf",
            columns = listOf(
                ReportColumn("الصنف", 3.0f, false),
                ReportColumn("العدد", 1.1f, true),
                ReportColumn("التكلفة", 1.4f, true),
                ReportColumn("إجمالي التكلفة", 1.7f, true),
                ReportColumn("سعر البيع", 1.4f, true),
                ReportColumn("قيمة البيع", 1.6f, true)
            ),
            rows = products.map { p ->
                listOf(
                    p.name,
                    p.stock.toString(),
                    Formatters.money(p.cost),
                    Formatters.money(p.cost * p.stock),
                    Formatters.money(p.price),
                    Formatters.money(p.price * p.stock)
                )
            },
            totals = listOf(
                "الإجمالي (${products.size} صنف)",
                totalStock.toString(),
                "",
                Formatters.money(totalCost),
                "",
                Formatters.money(totalSell)
            )
        )
    }

    fun buildExpensesReportData(expenses: List<ExpenseEntity>): ReportTableData {
        val sorted = expenses.sortedBy { it.timestampMillis }
        return ReportTableData(
            title = "كشف المصروفات",
            fileName = "كشف المصروفات.pdf",
            columns = listOf(
                ReportColumn("التاريخ", 2.2f, false),
                ReportColumn("البيان", 5.4f, false),
                ReportColumn("المبلغ", 2.4f, true)
            ),
            rows = sorted.map { e ->
                listOf(Formatters.shortDate(e.timestampMillis), e.title, Formatters.money(e.amount))
            },
            totals = listOf("الإجمالي (${sorted.size} مصروف)", "", Formatters.money(sorted.sumOf { it.amount }))
        )
    }

    fun buildAllCustomersReportData(
        customers: List<CustomerEntity>,
        totalsLookup: (CustomerEntity) -> Pair<Double, Double>
    ): ReportTableData {
        var sumBuys = 0.0
        var sumPaid = 0.0
        val rows = customers.map { c ->
            val (buys, paid) = totalsLookup(c)
            sumBuys += buys
            sumPaid += paid
            listOf(
                c.name,
                c.phone.ifBlank { "-" },
                Formatters.money(buys),
                Formatters.money(paid),
                Formatters.money(c.debt)
            )
        }
        return ReportTableData(
            title = "كشف حساب جميع الزبائن",
            fileName = "كشف جميع الزبائن.pdf",
            columns = listOf(
                ReportColumn("الزبون", 3.2f, false),
                ReportColumn("الهاتف", 2.0f, false),
                ReportColumn("المشتريات", 1.6f, true),
                ReportColumn("المدفوع", 1.6f, true),
                ReportColumn("المستحق", 1.6f, true)
            ),
            rows = rows,
            totals = listOf(
                "الإجمالي (${customers.size} زبون)",
                "",
                Formatters.money(sumBuys),
                Formatters.money(sumPaid),
                Formatters.money(customers.sumOf { it.debt })
            )
        )
    }

    fun sharePdf(context: Context, fileName: String, bytes: ByteArray) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "")
        val file = File(dir, safeName)
        FileOutputStream(file).use { it.write(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة / حفظ ملف PDF"))
    }

    fun printPdf(context: Context, jobName: String, bytes: ByteArray) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        out.write(bytes)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(jobName, adapter, null)
    }

    private fun wrapText(text: String, paint: Paint, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        for (w in text.split(" ")) {
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(candidate) > maxW && line.isNotEmpty()) {
                out.add(line)
                line = w
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) out.add(line)
        return if (out.isEmpty()) listOf("") else out
    }

    private fun fitText(text: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var s = text
        while (s.length > 1 && paint.measureText("$s…") > maxW) {
            s = s.dropLast(1)
        }
        return "$s…"
    }
}
