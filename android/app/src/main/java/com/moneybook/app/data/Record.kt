package com.moneybook.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条账目记录
 * @param date 日期，格式 yyyy-MM-dd（字符串存储，便于分组与排序）
 */
@Entity(tableName = "records")
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RecordType,
    val categoryId: String,
    val amount: Double,
    val note: String,
    val date: String,
    val createdAt: Long
)

enum class RecordType { EXPENSE, INCOME }
