package com.moneybook.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneybook.app.data.AppDatabase
import com.moneybook.app.data.Record
import com.moneybook.app.data.RecordDao
import com.moneybook.app.data.RecordType
import com.moneybook.app.data.Template
import com.moneybook.app.data.TemplateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------- 品牌色 ----------

val BrandBlue = Color(0xFF2F6FEB)
val ExpenseRed = Color(0xFFE5484D)
val IncomeGreen = Color(0xFF12A594)
val InkGray = Color(0xFF6B7280)
val PageBg = Color(0xFFF4F6F8)

/** 流水默认最多显示条数，其余折叠进「更多」按钮 */
const val PAGE_SIZE = 10

// ---------- 分类 ----------

/** 分类适用的收支类型 */
enum class CatKind { EXPENSE, INCOME, BOTH }

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val kind: CatKind = CatKind.EXPENSE
)

/** 全部分类，顺序即快捷栏按钮顺序 */
val CATS = listOf(
    Category("food", "餐饮", "🍚"),
    Category("grocery", "买菜", "🥬"),
    Category("snack", "零食", "🍪"),
    Category("goods", "百货", "🛍️"),
    Category("medical", "医疗", "💊"),
    Category("bus", "大巴", "🚌"),
    Category("train", "火车", "🚄"),
    Category("fuel", "汽油", "⛽"),
    Category("toll", "路桥", "🛣️"),
    Category("taxi", "打车", "🚕"),
    Category("utility", "水电", "💡"),
    Category("gas", "燃气", "🔥"),
    Category("property", "物业", "🏢"),
    Category("heating", "取暖", "♨️"),
    Category("rent", "房租", "🏠"),
    Category("insurance", "保险", "🛡️"),
    Category("give", "给于", "🤝", CatKind.BOTH),
    Category("salary", "工资", "💰", CatKind.INCOME),
    Category("other", "其他", "📦", CatKind.BOTH)
)

/** 旧版本分类 id → 新分类 id（保证老数据照常显示） */
private val LEGACY_CAT = mapOf(
    "traffic" to "bus",
    "shopping" to "goods",
    "home" to "property",
    "fun" to "taxi",
    "study" to "other",
    "bonus" to "salary",
    "redpack" to "give",
    "parttime" to "salary",
    "invest" to "salary"
)

/** 该收支类型下可选的分类（专属分类在前，通用分类在后） */
fun catsOf(type: RecordType): List<Category> {
    val want = if (type == RecordType.EXPENSE) CatKind.EXPENSE else CatKind.INCOME
    return CATS.filter { it.kind == want } + CATS.filter { it.kind == CatKind.BOTH }
}

/** 该收支类型下的默认分类 */
fun defaultCatId(type: RecordType): String =
    if (type == RecordType.EXPENSE) "food" else "salary"

fun findCat(type: RecordType, id: String): Category {
    val key = LEGACY_CAT[id] ?: id
    return CATS.firstOrNull { it.id == key }
        ?: Category("other", "其他", "📦", CatKind.BOTH)
}

private val MONEY_FMT = DecimalFormat("#,##0.00")

fun fmtMoney(v: Double): String = MONEY_FMT.format(v)

fun weekdayCn(date: LocalDate): String = "周" + "日一二三四五六"[date.dayOfWeek.value % 7]

fun monthRecords(all: List<Record>, month: YearMonth): List<Record> =
    all.filter { YearMonth.from(LocalDate.parse(it.date)) == month }

fun yearRecords(all: List<Record>, year: Int): List<Record> =
    all.filter { LocalDate.parse(it.date).year == year }

// ---------- 入口 ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val dao = AppDatabase.get(this).recordDao()
        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = BrandBlue,
                    background = PageBg,
                    surface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = PageBg) {
                    AppRoot(dao)
                }
            }
        }
    }
}

// ---------- 主界面 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(dao: RecordDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allRecords by dao.observeAll().collectAsState(initial = emptyList())

    var viewMonth by remember { mutableStateOf(YearMonth.now()) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Record?>(null) }
    var exportTarget by remember { mutableStateOf<Int?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // 快捷模板
    var templates by remember { mutableStateOf(TemplateStore.load(context)) }
    var showTemplates by remember { mutableStateOf(false) }
    var editingTplIndex by remember { mutableStateOf(-1) }

    // 快捷栏带入的分类与收支类型
    var pendingCat by remember { mutableStateOf<String?>(null) }
    var pendingType by remember { mutableStateOf(RecordType.EXPENSE) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun saveTemplates(list: List<Template?>) {
        templates = list
        TemplateStore.save(context, list)
    }

    fun insertRecord(type: RecordType, catId: String, amount: Double, note: String, onDone: () -> Unit) {
        scope.launch {
            dao.insert(
                Record(
                    type = type,
                    categoryId = catId,
                    amount = Math.round(amount * 100) / 100.0,
                    note = note,
                    date = LocalDate.now().toString(),
                    createdAt = System.currentTimeMillis()
                )
            )
            onDone()
        }
    }

    // 导出 CSV（按年）：调用系统保存对话框（SAF，无需存储权限）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val year = exportTarget
        if (uri != null && year != null) {
            scope.launch {
                val rows = yearRecords(allRecords, year)
                val ok = writeCsv(context, uri, rows)
                toast(if (ok) "已导出 ${year}年 共 ${rows.size} 条记录" else "导出失败")
            }
        }
    }

    val inMonth = monthRecords(allRecords, viewMonth)
    val totalExpense = inMonth.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
    val totalIncome = inMonth.filter { it.type == RecordType.INCOME }.sumOf { it.amount }

    // 流水折叠
    val visibleRecords = if (expanded || inMonth.size <= PAGE_SIZE) inMonth else inMonth.take(PAGE_SIZE)
    val hiddenCount = inMonth.size - visibleRecords.size

    Scaffold(
        containerColor = PageBg,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                containerColor = BrandBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("记一笔", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MonthSwitcher(
                month = viewMonth,
                onPrev = { viewMonth = viewMonth.minusMonths(1); expanded = false },
                onNext = { viewMonth = viewMonth.plusMonths(1); expanded = false },
                onExport = {
                    if (yearRecords(allRecords, viewMonth.year).isEmpty()) {
                        toast("今年没有记录可导出")
                    } else {
                        exportTarget = viewMonth.year
                        exportLauncher.launch("记账本_${viewMonth.year}年.csv")
                    }
                }
            )

            SummaryCard(totalExpense, totalIncome)

            // 支出分类快捷栏（结余下方）：点分类 → 直接进入已选好分类的记一笔
            QuickBar(
                onQuick = { showTemplates = true },
                onCategory = { catId, type ->
                    pendingCat = catId
                    pendingType = type
                    showAdd = true
                }
            )

            Text(
                text = "流水 · ${inMonth.size} 笔",
                fontSize = 13.sp,
                color = InkGray,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp)
            )

            if (inMonth.isEmpty()) {
                EmptyHint(hasAny = allRecords.isNotEmpty())
            } else {
                RecordList(
                    records = visibleRecords,
                    hiddenCount = hiddenCount,
                    onMore = { expanded = true },
                    onItemLongClick = { pendingDelete = it }
                )
            }
        }
    }

    // 记一笔弹层（pendingCat 用于快捷栏带入分类：锁定分类，只填金额）
    if (showAdd) {
        AddRecordSheet(
            dao = dao,
            initialType = pendingType,
            initialCat = pendingCat ?: defaultCatId(pendingType),
            lockedCat = pendingCat != null,
            onDismiss = { showAdd = false; pendingCat = null },
            onSaved = { date, amount ->
                showAdd = false
                pendingCat = null
                viewMonth = YearMonth.from(date)
                expanded = false
                toast("已记下 ¥${fmtMoney(amount)}")
            }
        )
    }

    // 快捷模板弹层
    if (showTemplates) {
        TemplateSheet(
            templates = templates,
            onDismiss = { showTemplates = false },
            onUse = { tpl ->
                insertRecord(tpl.type, tpl.categoryId, tpl.amount, tpl.note) {
                    showTemplates = false
                    viewMonth = YearMonth.now()
                    expanded = false
                    val cat = findCat(tpl.type, tpl.categoryId)
                    toast("已记：${cat.name} ${tpl.name} ¥${fmtMoney(tpl.amount)}")
                }
            },
            onEdit = { index ->
                showTemplates = false
                editingTplIndex = index
            }
        )
    }

    // 模板编辑弹层
    if (editingTplIndex >= 0) {
        TemplateEditSheet(
            index = editingTplIndex,
            current = templates.getOrNull(editingTplIndex),
            onSave = { tpl ->
                val list = templates.toMutableList()
                while (list.size < TemplateStore.SLOT_COUNT) list.add(null)
                list[editingTplIndex] = tpl
                saveTemplates(list)
                editingTplIndex = -1
                showTemplates = true
                toast("模板已保存")
            },
            onClear = {
                val list = templates.toMutableList()
                while (list.size < TemplateStore.SLOT_COUNT) list.add(null)
                list[editingTplIndex] = null
                saveTemplates(list)
                editingTplIndex = -1
                showTemplates = true
                toast("已清除该模板位")
            },
            onDismiss = {
                editingTplIndex = -1
                showTemplates = true
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔记录？") },
            text = {
                val cat = findCat(target.type, target.categoryId)
                Text("${cat.emoji} ${cat.name}  ¥${fmtMoney(target.amount)}\n${target.date}")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        dao.delete(target)
                        pendingDelete = null
                        toast("已删除")
                    }
                }) { Text("删除", color = ExpenseRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

// ---------- 月份切换行 ----------

@Composable
fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, onExport: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(48.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上个月", tint = InkGray)
            }
            Text(
                text = "${month.year}年${month.monthValue}月",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下个月", tint = InkGray)
            }
        }
        IconButton(onClick = onExport) {
            Icon(Icons.Filled.Share, contentDescription = "导出今年 CSV", tint = InkGray)
        }
    }
}

// ---------- 汇总卡 ----------

@Composable
fun SummaryCard(expense: Double, income: Double) {
    val balance = income - expense
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("本月支出", fontSize = 13.sp, color = InkGray)
            Text(
                text = "¥${fmtMoney(expense)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("本月收入", fontSize = 12.sp, color = InkGray)
                    Text(
                        "¥${fmtMoney(income)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IncomeGreen
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("本月结余", fontSize = 12.sp, color = InkGray)
                    Text(
                        "¥${fmtMoney(balance)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (balance >= 0) Color(0xFF1B1F24) else ExpenseRed
                    )
                }
            }
        }
    }
}

// ---------- 快捷分类栏（收入结余下方，网格：一行 5 个） ----------

private data class QuickCell(
    val emoji: String,
    val label: String,
    val income: Boolean,
    val highlight: Boolean,
    val action: () -> Unit
)

@Composable
fun QuickBar(onQuick: () -> Unit, onCategory: (String, RecordType) -> Unit) {
    // 每个分类一个按钮：点一下直接打开记一笔，分类已选好；末尾是「快捷」模板按钮
    val cells = CATS.map { c ->
        val t = if (c.kind == CatKind.INCOME) RecordType.INCOME else RecordType.EXPENSE
        QuickCell(c.emoji, c.name, c.kind == CatKind.INCOME, false) { onCategory(c.id, t) }
    } + QuickCell("⚡", "快捷", false, true, onQuick)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .padding(horizontal = 16.dp)
    ) {
        cells.chunked(5).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { cell ->
                    QuickPill(
                        emoji = cell.emoji,
                        label = cell.label,
                        highlight = cell.highlight,
                        income = cell.income,
                        onClick = cell.action,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 末行不满 5 个时补空位，保持每格等宽
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPill(
    emoji: String,
    label: String,
    highlight: Boolean,
    income: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                when {
                    highlight -> BrandBlue
                    income -> Color(0xFFEEFAF7)
                    else -> Color.White
                },
                RoundedCornerShape(13.dp)
            )
            .combinedClickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            maxLines = 1,
            color = if (highlight) Color.White else if (income) IncomeGreen else InkGray,
            fontWeight = if (highlight || income) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ---------- 快捷模板弹层 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateSheet(
    templates: List<Template?>,
    onDismiss: () -> Unit,
    onUse: (Template) -> Unit,
    onEdit: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("⚡ 快捷记账", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "点模板立即记一笔 · 点 ✎ 编辑模板",
                fontSize = 12.5.sp,
                color = InkGray,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )
            // 4 行 × 2 列
            for (row in 0 until 4) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    for (col in 0 until 2) {
                        val index = row * 2 + col
                        Box(Modifier.weight(1f)) {
                            TemplateCard(
                                index = index,
                                tpl = templates.getOrNull(index),
                                onUse = onUse,
                                onEdit = onEdit
                            )
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TemplateCard(index: Int, tpl: Template?, onUse: (Template) -> Unit, onEdit: (Int) -> Unit) {
    if (tpl == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .background(Color.Transparent, RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { onEdit(index) }),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("＋ 设置模板", fontSize = 13.sp, color = InkGray)
        }
        return
    }
    val cat = findCat(tpl.type, tpl.categoryId)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F8FA), RoundedCornerShape(14.dp))
            .combinedClickable(onClick = { onUse(tpl) })
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat.emoji, fontSize = 15.sp)
                Spacer(Modifier.width(5.dp))
                Text(
                    tpl.name.ifBlank { cat.name },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                (if (tpl.type == RecordType.EXPENSE) "-" else "+") + "¥" + fmtMoney(tpl.amount),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (tpl.type == RecordType.EXPENSE) ExpenseRed else IncomeGreen,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (tpl.note.isNotBlank()) {
                Text(
                    tpl.note,
                    fontSize = 11.sp,
                    color = InkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            "✎",
            fontSize = 12.sp,
            color = InkGray,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.White, RoundedCornerShape(7.dp))
                .combinedClickable(onClick = { onEdit(index) })
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ---------- 模板编辑弹层 ----------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateEditSheet(
    index: Int,
    current: Template?,
    onSave: (Template) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember(current) { mutableStateOf(current?.type ?: RecordType.EXPENSE) }
    var catId by remember(current) { mutableStateOf(current?.categoryId ?: "food") }
    var name by remember(current) { mutableStateOf(current?.name ?: "") }
    var amountText by remember(current) { mutableStateOf(current?.amount?.let { fmtMoney(it) } ?: "") }
    var note by remember(current) { mutableStateOf(current?.note ?: "") }

    val amount = amountText.replace(",", "").toDoubleOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "编辑模板 ${index + 1}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 收 / 支
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == RecordType.EXPENSE,
                    onClick = { type = RecordType.EXPENSE; catId = defaultCatId(RecordType.EXPENSE) },
                    label = { Text("支出", fontWeight = FontWeight.SemiBold, color = if (type == RecordType.EXPENSE) ExpenseRed else InkGray) }
                )
                FilterChip(
                    selected = type == RecordType.INCOME,
                    onClick = { type = RecordType.INCOME; catId = defaultCatId(RecordType.INCOME) },
                    label = { Text("收入", fontWeight = FontWeight.SemiBold, color = if (type == RecordType.INCOME) IncomeGreen else InkGray) }
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 10) name = it },
                    label = { Text("名称，如：早餐") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { s ->
                        val cleaned = s.replace(",", "").filter { it.isDigit() || it == '.' }
                        val parts = cleaned.split('.')
                        val fixed = when {
                            parts.size > 2 -> parts[0] + "." + parts.drop(1).joinToString("").take(2)
                            parts.size == 2 && parts[1].length > 2 -> parts[0] + "." + parts[1].take(2)
                            else -> cleaned
                        }
                        if (fixed.replace(".", "").length <= 9) amountText = fixed
                    },
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                catsOf(type).forEach { cat ->
                    FilterChip(
                        selected = cat.id == catId,
                        onClick = { catId = cat.id },
                        label = { Text("${cat.emoji} ${cat.name}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandBlue.copy(alpha = 0.12f),
                            selectedLabelColor = BrandBlue
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 30) note = it },
                label = { Text("备注（可空）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onClear,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEF2F2), contentColor = ExpenseRed),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.weight(0.7f)
                ) { Text("清除此位") }
                Button(
                    onClick = {
                        val amt = amount ?: return@Button
                        if (amt <= 0) return@Button
                        val cat = findCat(type, catId)
                        onSave(
                            Template(
                                name = name.trim().ifBlank { cat.name },
                                amount = Math.round(amt * 100) / 100.0,
                                type = type,
                                categoryId = catId,
                                note = note.trim()
                            )
                        )
                    },
                    enabled = amount != null && amount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("保存模板", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ---------- 流水列表 ----------

@Composable
fun RecordList(
    records: List<Record>,
    hiddenCount: Int,
    onMore: () -> Unit,
    onItemLongClick: (Record) -> Unit
) {
    val grouped = records.groupBy { it.date }.toSortedMap(compareByDescending { it })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        grouped.forEach { (date, items) ->
            val dayExpense = items.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
            val dayIncome = items.filter { it.type == RecordType.INCOME }.sumOf { it.amount }
            val d = LocalDate.parse(date)

            item(key = "head_$date") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${d.monthValue}月${d.dayOfMonth}日 ${weekdayCn(d)}",
                        fontSize = 12.5.sp,
                        color = InkGray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        buildString {
                            if (dayExpense > 0) append("支出 ¥${fmtMoney(dayExpense)}")
                            if (dayExpense > 0 && dayIncome > 0) append("  ·  ")
                            if (dayIncome > 0) append("收入 ¥${fmtMoney(dayIncome)}")
                        },
                        fontSize = 12.sp,
                        color = InkGray
                    )
                }
            }

            item(key = "group_$date") {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        items.forEachIndexed { index, r ->
                            RecordRow(
                                record = r,
                                showDivider = index > 0,
                                onLongClick = { onItemLongClick(r) }
                            )
                        }
                    }
                }
            }
        }

        if (hiddenCount > 0) {
            item(key = "more_btn") {
                TextButton(
                    onClick = onMore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = BrandBlue)
                    Spacer(Modifier.width(4.dp))
                    Text("展开其余 $hiddenCount 条", color = BrandBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordRow(record: Record, showDivider: Boolean, onLongClick: () -> Unit) {
    val cat = findCat(record.type, record.categoryId)
    Column {
        if (showDivider) {
            Box(
                Modifier
                    .padding(start = 60.dp)
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(Color(0xFFEEF1F4))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFF2F4F7), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(cat.emoji, fontSize = 17.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cat.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (record.note.isNotBlank()) {
                    Text(
                        record.note,
                        fontSize = 12.5.sp,
                        color = InkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = (if (record.type == RecordType.EXPENSE) "-" else "+") + "¥" + fmtMoney(record.amount),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (record.type == RecordType.EXPENSE) ExpenseRed else IncomeGreen
            )
        }
    }
}

// ---------- 空状态 ----------

@Composable
fun EmptyHint(hasAny: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🧾", fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            if (hasAny) "这个月还没有记录\n换个月份看看？" else "还没有任何记录\n点右下角「记一笔」或上方快捷按钮开始",
            fontSize = 14.sp,
            color = InkGray,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ---------- 记一笔弹层 ----------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRecordSheet(
    dao: RecordDao,
    initialType: RecordType,
    initialCat: String,
    lockedCat: Boolean = false,
    onDismiss: () -> Unit,
    onSaved: (LocalDate, Double) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by remember { mutableStateOf(initialType) }
    var catId by remember { mutableStateOf(initialCat) }
    var locked by remember { mutableStateOf(lockedCat) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp)) {

            if (locked) {
                // 从分类按钮进入：分类已锁定，只填金额
                val lockC = findCat(type, catId)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(BrandBlue.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(lockC.emoji, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (type == RecordType.INCOME) "收入 · " else "") + lockC.name + "（只填金额即可）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandBlue,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { locked = false }) {
                        Text("更换", fontSize = 12.sp, color = InkGray)
                    }
                }
            } else {
            // 收 / 支 切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = type == RecordType.EXPENSE,
                    onClick = {
                        if (type != RecordType.EXPENSE) {
                            type = RecordType.EXPENSE
                            catId = defaultCatId(RecordType.EXPENSE)
                        }
                    },
                    label = {
                        Text(
                            "支出",
                            fontWeight = FontWeight.SemiBold,
                            color = if (type == RecordType.EXPENSE) ExpenseRed else InkGray
                        )
                    }
                )
                FilterChip(
                    selected = type == RecordType.INCOME,
                    onClick = {
                        if (type != RecordType.INCOME) {
                            type = RecordType.INCOME
                            catId = defaultCatId(RecordType.INCOME)
                        }
                    },
                    label = {
                        Text(
                            "收入",
                            fontWeight = FontWeight.SemiBold,
                            color = if (type == RecordType.INCOME) IncomeGreen else InkGray
                        )
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { s ->
                    val cleaned = s.filter { it.isDigit() || it == '.' }
                    val parts = cleaned.split('.')
                    val fixed = when {
                        parts.size > 2 -> parts[0] + "." + parts.drop(1).joinToString("").take(2)
                        parts.size == 2 && parts[1].length > 2 -> parts[0] + "." + parts[1].take(2)
                        else -> cleaned
                    }
                    if (fixed.replace(".", "").length <= 9) amountText = fixed
                },
                label = { Text("金额") },
                prefix = { Text("¥", fontWeight = FontWeight.SemiBold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // 分类
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                catsOf(type).forEach { cat ->
                    FilterChip(
                        selected = cat.id == catId,
                        onClick = { catId = cat.id },
                        label = { Text("${cat.emoji} ${cat.name}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandBlue.copy(alpha = 0.12f),
                            selectedLabelColor = BrandBlue
                        )
                    )
                }
            }
            } // end else（未锁定时才显示收/支切换与分类选择）

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 40) note = it },
                    label = { Text("备注（可空）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = { showDatePicker = true }) {
                    Text("${date.monthValue}月${date.dayOfMonth}日", color = BrandBlue)
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: return@Button
                    if (amt <= 0) return@Button
                    scope.launch {
                        dao.insert(
                            Record(
                                type = type,
                                categoryId = catId,
                                amount = Math.round(amt * 100) / 100.0,
                                note = note.trim(),
                                date = date.toString(),
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        onSaved(date, amt)
                    }
                },
                enabled = amount != null && amount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("保存", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

// ---------- CSV 导出 ----------

suspend fun writeCsv(
    context: Context,
    uri: Uri,
    rows: List<Record>
): Boolean = withContext(Dispatchers.IO) {
    try {
        if (rows.isEmpty()) return@withContext false
        val sorted = rows.sortedWith(compareBy({ it.date }, { it.createdAt }))

        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM：Excel/WPS 直接打开不乱码
        sb.append("日期,星期,类型,分类,备注,金额\r\n")
        val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
        sorted.forEach { r ->
            val d = LocalDate.parse(r.date, dateFmt)
            val cat = findCat(r.type, r.categoryId)
            val note = r.note.replace("\"", "\"\"")
            sb.append(r.date).append(',')
                .append(weekdayCn(d)).append(',')
                .append(if (r.type == RecordType.EXPENSE) "支出" else "收入").append(',')
                .append(cat.name).append(',')
                .append('"').append(note).append('"').append(',')
                .append(if (r.type == RecordType.EXPENSE) "-" else "")
                .append(String.format("%.2f", r.amount))
                .append("\r\n")
        }
        val totalExpense = rows.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
        val totalIncome = rows.filter { it.type == RecordType.INCOME }.sumOf { it.amount }
        sb.append("\r\n")
        sb.append("合计,,,,收入,${String.format("%.2f", totalIncome)}\r\n")
        sb.append("结余,,,,,${String.format("%.2f", totalIncome - totalExpense)}\r\n")

        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(sb.toString().toByteArray(Charsets.UTF_8))
        } ?: return@withContext false
        true
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
