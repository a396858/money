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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.json.JSONArray

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

// ---------- 自定义分类（最后一行 5 个，名字最多 2 个字，存在本地） ----------

const val CUSTOM_COUNT = 5
fun customCatId(i: Int): String = "x" + (i + 1)

/** 当前生效的自定义分类名（进程级，App 启动时从本地加载） */
var CUSTOM_CATS: List<String> = List(CUSTOM_COUNT) { "" }

/** 自定义分类持久化：SharedPreferences + JSON */
object CustomCatStore {
    private const val PREFS = "moneybook"
    private const val KEY = "customcats.v1"

    fun load(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return List(CUSTOM_COUNT) { "" }
        return try {
            val arr = JSONArray(raw)
            List(CUSTOM_COUNT) { i ->
                if (i < arr.length()) arr.optString(i, "").take(2) else ""
            }
        } catch (e: Exception) {
            List(CUSTOM_COUNT) { "" }
        }
    }

    fun save(context: Context, names: List<String>) {
        val arr = JSONArray()
        for (i in 0 until CUSTOM_COUNT) arr.put(names.getOrElse(i) { "" }.take(2))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}

/** 全部分类 = 19 个固定分类 + 5 个自定义分类（顺序即快捷栏按钮顺序） */
fun allCats(): List<Category> =
    CATS + CUSTOM_CATS.mapIndexed { i, n ->
        Category(customCatId(i), n, "🏷️", CatKind.BOTH)
    }

/** 该收支类型下可选的分类（专属分类在前，通用分类在后；没起名的自定义分类不出现） */
fun catsOf(type: RecordType): List<Category> {
    val want = if (type == RecordType.EXPENSE) CatKind.EXPENSE else CatKind.INCOME
    return allCats().filter { it.kind == want } +
        allCats().filter { it.kind == CatKind.BOTH && !(it.id.startsWith("x") && it.name.isBlank()) }
}

/** 该收支类型下的默认分类 */
fun defaultCatId(type: RecordType): String =
    if (type == RecordType.EXPENSE) "food" else "salary"

fun findCat(type: RecordType, id: String): Category {
    val key = LEGACY_CAT[id] ?: id
    val hit = allCats().firstOrNull { it.id == key }
    // 起了名的分类照常返回；没起名（或找不到）的自定义分类回落到「其他」
    return if (hit != null && !(hit.id.startsWith("x") && hit.name.isBlank())) hit
    else Category("other", "其他", "📦", CatKind.BOTH)
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
    var pendingDelete by remember { mutableStateOf<Record?>(null) }
    var exportTarget by remember { mutableStateOf<Int?>(null) }
    var showYearPick by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // 记一笔（常驻首页顶部，打开即可输入）
    var entryType by remember { mutableStateOf(RecordType.EXPENSE) }
    var entryCatId by remember { mutableStateOf(defaultCatId(RecordType.EXPENSE)) }
    var amountText by remember { mutableStateOf("") }
    var entryNote by remember { mutableStateOf("") }
    var entryDate by remember { mutableStateOf(LocalDate.now()) }

    // 快捷模板
    var templates by remember { mutableStateOf(TemplateStore.load(context)) }
    var showTemplates by remember { mutableStateOf(false) }
    var editingTplIndex by remember { mutableStateOf(-1) }

    // 自定义分类（最后一行 5 个，先加载进进程级变量，保证 findCat 立即可用）
    remember { CUSTOM_CATS = CustomCatStore.load(context); true }
    var customCats by remember { mutableStateOf(CUSTOM_CATS) }
    var showCatEdit by remember { mutableStateOf(false) }

    // 流水弹层 / 日期选择
    var showRecords by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

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

    // 本月（当前自然月）收支，用于顶部小字
    val curMonthRecords = monthRecords(allRecords, YearMonth.now())
    val curExpense = curMonthRecords.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
    val curIncome = curMonthRecords.filter { it.type == RecordType.INCOME }.sumOf { it.amount }

    // 总收入 / 总支出（全部记录累计，不受月份影响）
    val totalExpense = allRecords.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
    val totalIncome = allRecords.filter { it.type == RecordType.INCOME }.sumOf { it.amount }

    // 最近三条记录（按日期 + 时间倒序）
    val recent3 = allRecords
        .sortedWith(compareByDescending<Record> { it.date }.thenByDescending { it.createdAt })
        .take(3)

    // 有记录的年份（倒序）+ 该年条数，导出时选
    val exportYears = allRecords
        .groupingBy { LocalDate.parse(it.date).year }
        .eachCount()
        .toList()
        .sortedByDescending { it.first }

    Scaffold(containerColor = PageBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 顶部小字：日期 + 本月收支 + 总收入总支出
            HeaderBar(
                date = entryDate,
                expense = curExpense,
                income = curIncome,
                totalExpense = totalExpense,
                totalIncome = totalIncome,
                onDateClick = { showDatePicker = true },
                onExport = {
                    if (allRecords.isEmpty()) {
                        toast("还没有记录可导出")
                    } else {
                        showYearPick = true
                    }
                }
            )

            // 分类上方：最近三条记录（点一下看全部流水）
            RecentStrip(
                records = recent3,
                onClick = { showRecords = true }
            )

            // 中间：分类（纯文字，一行 5 个，第 5 行可自定义名字）
            // 分类与键盘按 2:3 分剩余空间——分类变矮，键盘长高填补空白
            QuickBar(
                currentCatId = entryCatId,
                customNames = customCats,
                onQuick = { showTemplates = true },
                onCategory = { catId, kind ->
                    entryCatId = catId
                    when (kind) {
                        CatKind.EXPENSE -> entryType = RecordType.EXPENSE
                        CatKind.INCOME -> entryType = RecordType.INCOME
                        CatKind.BOTH -> Unit    // 通用分类保持当前收支
                    }
                },
                onEditCustom = { showCatEdit = true },
                modifier = Modifier.weight(2f)
            )

            // 底部：类型/备注 + 金额 + 数字键盘（弹性高度）
            EntryCard(
                modifier = Modifier.weight(3f),
                type = entryType,
                catId = entryCatId,
                amountText = amountText,
                note = entryNote,
                onTypeChange = { t ->
                    if (entryType != t) {
                        entryType = t
                        entryCatId = defaultCatId(t)
                    }
                },
                onKey = { k -> amountText = applyKey(amountText, k) },
                onNoteChange = { if (it.length <= 40) entryNote = it },
                onSave = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        toast("请输入金额")
                    } else {
                        val cat = findCat(entryType, entryCatId)
                        scope.launch {
                            dao.insert(
                                Record(
                                    type = entryType,
                                    categoryId = entryCatId,
                                    amount = Math.round(amt * 100) / 100.0,
                                    note = entryNote.trim(),
                                    date = entryDate.toString(),
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            viewMonth = YearMonth.from(entryDate)
                            expanded = false
                            // 保留分类与日期，清空金额和备注，方便接着记下一笔
                            amountText = ""
                            entryNote = ""
                            toast("已记下 ${cat.name} ¥${fmtMoney(amt)}")
                        }
                    }
                }
            )
        }
    }

    // 选择导出年份
    if (showYearPick) {
        YearPickDialog(
            years = exportYears,
            currentYear = viewMonth.year,
            onPick = { y ->
                showYearPick = false
                exportTarget = y
                exportLauncher.launch("记账本_${y}年.csv")
            },
            onDismiss = { showYearPick = false }
        )
    }

    // 自定义分类编辑弹窗
    if (showCatEdit) {
        CatEditDialog(
            names = customCats,
            onSave = { next ->
                customCats = next
                CUSTOM_CATS = next
                CustomCatStore.save(context, next)
                showCatEdit = false
                // 当前选中的自定义分类被清空时，回落到默认分类
                if (entryCatId.startsWith("x") && findCat(entryType, entryCatId).id != entryCatId) {
                    entryCatId = defaultCatId(entryType)
                }
            },
            onDismiss = { showCatEdit = false }
        )
    }

    // 流水弹层（点「最近三条」打开）
    if (showRecords) {
        RecordListSheet(
            month = viewMonth,
            records = inMonth,
            hasAny = allRecords.isNotEmpty(),
            expanded = expanded,
            onMore = { expanded = true },
            onPrev = { viewMonth = viewMonth.minusMonths(1); expanded = false },
            onNext = { viewMonth = viewMonth.plusMonths(1); expanded = false },
            onDismiss = { showRecords = false },
            onItemLongClick = { pendingDelete = it }
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

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = entryDate
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        entryDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = dpState)
        }
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

// ---------- 月份切换行（流水弹层内） ----------

@Composable
fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
}

// ---------- 分类网格（一行 5 个，纯文字，撑满中部剩余空间） ----------

private data class QuickCell(
    val label: String,
    val income: Boolean,
    val highlight: Boolean,
    val selected: Boolean,
    val action: () -> Unit,
    val longAction: (() -> Unit)? = null
)

@Composable
fun QuickBar(
    currentCatId: String,
    customNames: List<String>,
    onQuick: () -> Unit,
    onCategory: (String, CatKind) -> Unit,
    onEditCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 前 4 行：19 个固定分类（点一下选中）+「快捷」模板按钮；
    // 第 5 行：5 个自定义分类（点一下选中；空位点「＋」改名，已起名长按改名）
    val cells = CATS.map { c ->
        QuickCell(
            label = c.name,
            income = c.kind == CatKind.INCOME,
            highlight = false,
            selected = c.id == currentCatId,
            action = { onCategory(c.id, c.kind) }
        )
    } + QuickCell("快捷", false, true, false, onQuick) +
        customNames.mapIndexed { i, n ->
            if (n.isBlank()) {
                QuickCell("＋", false, false, false, action = { onEditCustom() })
            } else {
                QuickCell(
                    label = n,
                    income = false,
                    highlight = false,
                    selected = customCatId(i) == currentCatId,
                    action = { onCategory(customCatId(i), CatKind.BOTH) },
                    longAction = onEditCustom
                )
            }
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)
    ) {
        cells.chunked(5).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { cell ->
                    QuickPill(
                        label = cell.label,
                        highlight = cell.highlight,
                        income = cell.income,
                        selected = cell.selected,
                        onClick = cell.action,
                        onLongClick = cell.longAction,
                        modifier = Modifier.weight(1f).fillMaxHeight()
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
    label: String,
    highlight: Boolean,
    income: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                when {
                    highlight -> BrandBlue
                    selected -> Color(0xFFEEF4FF)
                    income -> Color(0xFFEEFAF7)
                    else -> Color.White
                },
                RoundedCornerShape(12.dp)
            )
            .then(
                if (selected) Modifier.border(1.5.dp, BrandBlue, RoundedCornerShape(12.dp))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = if (label == "＋") 18.sp else 13.sp,
            maxLines = 1,
            color = when {
                highlight -> Color.White
                selected -> BrandBlue
                income -> IncomeGreen
                label == "＋" -> Color(0xFFB3BCC7)
                else -> InkGray
            },
            fontWeight = if (highlight || income || selected) FontWeight.SemiBold else FontWeight.Normal
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

// ---------- 顶部两行小字（日期 / 本月收支） ----------

@Composable
fun HeaderBar(
    date: LocalDate,
    expense: Double,
    income: Double,
    totalExpense: Double,
    totalIncome: Double,
    onDateClick: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 10.dp, top = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val today = LocalDate.now()
            Text(
                text = (if (date == today) "今天" else "${date.monthValue}月${date.dayOfMonth}日") +
                    " " + weekdayCn(date) + " ▾",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onDateClick)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "导出",
                fontSize = 13.sp,
                color = InkGray,
                modifier = Modifier
                    .clickable(onClick = onExport)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Row(Modifier.padding(start = 4.dp, top = 1.dp)) {
            Text("本月支出 ", fontSize = 12.5.sp, color = InkGray)
            Text(
                "¥${fmtMoney(expense)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed
            )
            Spacer(Modifier.width(14.dp))
            Text("本月收入 ", fontSize = 12.5.sp, color = InkGray)
            Text(
                "¥${fmtMoney(income)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = IncomeGreen
            )
        }
        // 第三行：总收入 / 总支出（全部记录累计，字略小）
        Row(Modifier.padding(start = 4.dp, top = 0.dp)) {
            Text("总支出 ", fontSize = 11.5.sp, color = InkGray)
            Text(
                "¥${fmtMoney(totalExpense)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFC0565B)
            )
            Spacer(Modifier.width(14.dp))
            Text("总收入 ", fontSize = 11.5.sp, color = InkGray)
            Text(
                "¥${fmtMoney(totalIncome)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0E8B7D)
            )
        }
    }
}

// ---------- 分类上方：最近三条记录（点一下看全部流水） ----------

@Composable
fun RecentStrip(
    records: List<Record>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(0.5.dp, Color(0xFFE8EBEF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        if (records.isEmpty()) {
            Text(
                "还没有记录 · 选分类、输金额就能记第一笔",
                fontSize = 11.5.sp,
                color = InkGray,
                maxLines = 1
            )
        } else {
            Column {
                records.forEach { r ->
                    val d = LocalDate.parse(r.date)
                    val cat = findCat(r.type, r.categoryId)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 1.dp)
                    ) {
                        Text(
                            cat.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1B1F24),
                            maxLines = 1
                        )
                        if (r.note.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                r.note,
                                fontSize = 11.sp,
                                color = InkGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${d.monthValue}月${d.dayOfMonth}日",
                            fontSize = 10.5.sp,
                            color = InkGray
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (if (r.type == RecordType.EXPENSE) "-" else "+") + "¥" + fmtMoney(r.amount),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (r.type == RecordType.EXPENSE) ExpenseRed else IncomeGreen
                        )
                    }
                }
            }
        }
    }
}

// ---------- 选择导出年份 ----------

@Composable
fun YearPickDialog(
    years: List<Pair<Int, Int>>,
    currentYear: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选年份导出", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "有记录的年份都在这里，点一年导出该年全部流水（CSV）。",
                    fontSize = 12.sp,
                    color = InkGray
                )
                Spacer(Modifier.height(10.dp))
                years.forEach { (y, n) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(y) }
                            .padding(horizontal = 10.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${y}年", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (y == currentYear) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "当前",
                                fontSize = 10.sp,
                                color = BrandBlue,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEEF4FF))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("$n 条", fontSize = 12.sp, color = InkGray)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ---------- 自定义分类编辑弹窗 ----------

@Composable
fun CatEditDialog(
    names: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val drafts = remember(names) { mutableStateListOf<String>().apply { addAll(names) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义分类", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "最后一行 5 个按钮的名字，最多 2 个字；留空显示「＋」，点「＋」或长按也能改。",
                    fontSize = 12.sp,
                    color = InkGray
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until CUSTOM_COUNT) {
                        OutlinedTextField(
                            value = drafts.getOrElse(i) { "" },
                            onValueChange = { v ->
                                if (v.length <= 2 && i < drafts.size) drafts[i] = v
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(drafts.toList()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ---------- 流水弹层（月份切换 + 当月流水，可滚动） ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListSheet(
    month: YearMonth,
    records: List<Record>,
    hasAny: Boolean,
    expanded: Boolean,
    onMore: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onItemLongClick: (Record) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PageBg,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            MonthSwitcher(month = month, onPrev = onPrev, onNext = onNext)

            if (records.isEmpty()) {
                EmptyHint(hasAny = hasAny)
            } else {
                val visible = if (expanded || records.size <= PAGE_SIZE) records else records.take(PAGE_SIZE)
                val hidden = records.size - visible.size
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    RecordList(
                        records = visible,
                        hiddenCount = hidden,
                        onMore = onMore,
                        onItemLongClick = onItemLongClick
                    )
                }
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

    // 外层页面已可滚动，这里用普通 Column，避免嵌套滚动冲突
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        grouped.forEach { (date, items) ->
            val dayExpense = items.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
            val dayIncome = items.filter { it.type == RecordType.INCOME }.sumOf { it.amount }
            val d = LocalDate.parse(date)

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

        if (hiddenCount > 0) {
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
            if (hasAny) "这个月还没有记录\n换个月份看看？" else "还没有任何记录\n在首页输入金额就能记第一笔",
            fontSize = 14.sp,
            color = InkGray,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ---------- 记一笔（常驻首页顶部） ----------

/** 数字键盘按键处理（与网页版规则一致：最多 9 位整数 + 2 位小数） */
fun applyKey(current: String, k: String): String {
    var s = current
    when (k) {
        "del" -> if (s.isNotEmpty()) s = s.dropLast(1)
        "." -> if (!s.contains('.')) s = (if (s.isEmpty()) "0" else s) + "."
        "00" -> if (s.isNotEmpty() && s != "0") {
            s += "00"
            if (s.contains('.')) {
                val i = s.substringBefore('.')
                val d = s.substringAfter('.').take(2)
                s = "$i.$d"
            }
        }
        else -> {
            val dec = if (s.contains('.')) s.substringAfter('.') else ""
            if ((!s.contains('.') || dec.length < 2) && s.replace(".", "").length < 9) {
                s = if (s == "0") k else s + k
            }
        }
    }
    return s
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryCard(
    modifier: Modifier = Modifier,
    type: RecordType,
    catId: String,
    amountText: String,
    note: String,
    onTypeChange: (RecordType) -> Unit,
    onKey: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val cat = findCat(type, catId)
    val amount = amountText.toDoubleOrNull()
    val hasAmount = amount != null && amount > 0

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxHeight()) {
            // 收 / 支 + 备注
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = type == RecordType.EXPENSE,
                    onClick = { onTypeChange(RecordType.EXPENSE) },
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
                    onClick = { onTypeChange(RecordType.INCOME) },
                    label = {
                        Text(
                            "收入",
                            fontWeight = FontWeight.SemiBold,
                            color = if (type == RecordType.INCOME) IncomeGreen else InkGray
                        )
                    }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    placeholder = { Text("备注（可空）", fontSize = 13.sp, color = Color(0xFFAAB2BC)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // 金额 + 当前分类
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text("¥", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = InkGray)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (hasAmount) fmtMoney(amount!!) else "0.00",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !hasAmount -> Color(0xFFC8CED6)
                        type == RecordType.INCOME -> IncomeGreen
                        else -> ExpenseRed
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = Color(0xFFEEF4FF),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        cat.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 自带数字键盘（不弹系统输入法，高度弹性撑满剩余空间）
            Keypad(
                onKey = onKey,
                onSave = onSave,
                saveEnabled = hasAmount,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 内置数字键盘：左侧 0-9 / . / 00，右上角退格，右侧长条保存；高度弹性（weight 撑满） */
@Composable
fun Keypad(
    onKey: (String) -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val gap = 1.dp
    val digitRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("0", "00", ".")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFE4E8EE))
    ) {
        Column(modifier = Modifier.weight(3f).fillMaxHeight()) {
            digitRows.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    r.forEach { k ->
                        Keybox(
                            label = k,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onKey(k) }
                        )
                    }
                }
                // 行间隙（最后一行后不加）
                if (r != digitRows.last()) Spacer(Modifier.height(gap))
            }
        }
        Spacer(Modifier.width(gap))
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Keybox(
                label = "⌫",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onClick = { onKey("del") }
            )
            Spacer(Modifier.height(gap))
            Keybox(
                label = "保存",
                enabled = saveEnabled,
                isSave = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(3f),
                onClick = onSave
            )
        }
    }
}

@Composable
private fun Keybox(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSave: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                when {
                    isSave && enabled -> BrandBlue
                    isSave -> Color(0xFFC3CBD6)
                    else -> Color(0xFFFBFCFD)
                }
            )
            .clickable(enabled = !isSave || enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = if (isSave) 16.sp else 20.sp,
            fontWeight = if (isSave) FontWeight.SemiBold else FontWeight.Medium,
            color = when {
                isSave -> Color.White
                label == "⌫" -> InkGray
                else -> Color(0xFF1B1F24)
            }
        )
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
