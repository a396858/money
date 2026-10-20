package com.moneybook.app

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 极简 OOXML(.xlsx) 写入器：只用 JDK 自带的 java.util.zip，不引第三方库。
 *
 * 只做一件事：把二维表写成单工作表的 xlsx。
 * - String → inlineStr 单元格
 * - Number → 数值单元格（Excel 里能直接 SUM）
 * - null   → 跳过该格
 */
object XlsxWriter {

    private const val XML_HEAD =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private const val CONTENT_TYPES = XML_HEAD +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "</Types>"

    private const val ROOT_RELS = XML_HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private const val WORKBOOK_RELS = XML_HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "</Relationships>"

    fun write(out: OutputStream, rows: List<List<Any?>>, sheetName: String = "流水") {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            put(zip, "[Content_Types].xml", CONTENT_TYPES)
            put(zip, "_rels/.rels", ROOT_RELS)
            put(zip, "xl/workbook.xml", workbook(sheetName))
            put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put(zip, "xl/worksheets/sheet1.xml", sheet(rows))
        }
    }

    private fun put(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun workbook(sheetName: String): String = XML_HEAD +
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"" +
        " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
        "<sheets><sheet name=\"" + esc(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
        "</workbook>"

    private fun sheet(rows: List<List<Any?>>): String {
        val sb = StringBuilder()
        sb.append(XML_HEAD)
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        // 列宽：日期 / 星期 / 类型 / 分类 / 备注 / 金额
        sb.append("<cols>")
        sb.append("<col min=\"1\" max=\"1\" width=\"12\" customWidth=\"1\"/>")
        sb.append("<col min=\"2\" max=\"2\" width=\"6\" customWidth=\"1\"/>")
        sb.append("<col min=\"3\" max=\"3\" width=\"7\" customWidth=\"1\"/>")
        sb.append("<col min=\"4\" max=\"4\" width=\"10\" customWidth=\"1\"/>")
        sb.append("<col min=\"5\" max=\"5\" width=\"28\" customWidth=\"1\"/>")
        sb.append("<col min=\"6\" max=\"6\" width=\"12\" customWidth=\"1\"/>")
        sb.append("</cols>")
        sb.append("<sheetData>")
        rows.forEachIndexed { i, row ->
            val r = i + 1
            sb.append("<row r=\"").append(r).append("\">")
            row.forEachIndexed { j, cell ->
                if (cell != null) {
                    val ref = colRef(j) + r
                    when (cell) {
                        is Number -> sb.append("<c r=\"").append(ref).append("\"><v>")
                            .append(num(cell.toDouble())).append("</v></c>")
                        else -> sb.append("<c r=\"").append(ref)
                            .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(esc(cell.toString())).append("</t></is></c>")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun num(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    private fun colRef(j: Int): String {
        var n = j + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun esc(s: String): String = buildString {
        s.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}
