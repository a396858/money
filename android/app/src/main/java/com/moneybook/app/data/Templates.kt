package com.moneybook.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一键快捷记账模板 */
data class Template(
    val name: String,
    val amount: Double,
    val type: RecordType,
    val categoryId: String,
    val note: String
)

/** 模板持久化：SharedPreferences + JSON，固定 8 个槽位 */
object TemplateStore {

    private const val PREFS = "moneybook"
    private const val KEY = "templates.v1"
    const val SLOT_COUNT = 8

    fun load(context: Context): List<Template?> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return defaultTemplates()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Template?>()
            for (i in 0 until SLOT_COUNT) {
                if (i < arr.length() && !arr.isNull(i)) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Template(
                            name = o.getString("name"),
                            amount = o.getDouble("amount"),
                            type = RecordType.valueOf(o.getString("type")),
                            categoryId = o.optString("cat", "other"),
                            note = o.optString("note", "")
                        )
                    )
                } else {
                    list.add(null)
                }
            }
            list
        } catch (e: Exception) {
            defaultTemplates()
        }
    }

    fun save(context: Context, templates: List<Template?>) {
        val arr = JSONArray()
        for (i in 0 until SLOT_COUNT) {
            val t = templates.getOrNull(i)
            if (t == null) arr.put(JSONObject.NULL)
            else arr.put(
                JSONObject()
                    .put("name", t.name)
                    .put("amount", t.amount)
                    .put("type", t.type.name)
                    .put("cat", t.categoryId)
                    .put("note", t.note)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun defaultTemplates(): List<Template?> = listOf(
        Template("早餐", 8.0, RecordType.EXPENSE, "food", ""),
        Template("午餐", 25.0, RecordType.EXPENSE, "food", ""),
        Template("晚餐", 30.0, RecordType.EXPENSE, "food", ""),
        Template("买菜", 50.0, RecordType.EXPENSE, "grocery", ""),
        Template("打车", 30.0, RecordType.EXPENSE, "taxi", ""),
        Template("加油", 300.0, RecordType.EXPENSE, "fuel", ""),
        null,
        null
    )
}
