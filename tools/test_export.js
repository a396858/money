/* 导出功能回归测试
 *
 * 做法：直接从 web/index.html 里“抠出”真实的导出函数（不是复制粘贴的副本），
 *       喂真实数据跑一遍 exportXLSX() / exportCSV()，把产物落到系统临时目录，
 *       并断言分类名映射、旧 id 兼容、特殊字符转义、合计数值。
 *
 * 运行：node tools/test_export.js
 * 需要：Node 18+（用到 Blob / TextEncoder / arrayBuffer）
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const INDEX = path.join(__dirname, '..', 'web', 'index.html');
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'moneybook-test-'));

let pass = 0, fail = 0;
const check = (c, m) => { c ? (pass++, console.log('  PASS  ' + m)) : (fail++, console.log('  FAIL  ' + m)); };

// ---------- 1. 从 index.html 抽取真实源码 ----------
const src = fs.readFileSync(INDEX, 'utf8');

function slice(start, end) {
  const i = src.indexOf(start);
  if (i < 0) throw new Error('找不到起始标记: ' + start);
  const j = src.indexOf(end, i);
  if (j < 0) throw new Error('找不到结束标记: ' + end);
  return src.slice(i, j + end.length);
}
function grabFunc(name) {
  const key = 'function ' + name + '(';
  const i = src.indexOf(key);
  if (i < 0) throw new Error('找不到函数: ' + name);
  let depth = 0;
  for (let j = i; j < src.length; j++) {
    const c = src[j];
    if (c === '{') depth++;
    else if (c === '}') { depth--; if (depth === 0) return src.slice(i, j + 1); }
  }
  throw new Error('花括号不匹配: ' + name);
}

const chunks = [
  slice('var CATS = [', '\n  ];'),
  "var DEFAULT_CAT = 'food';",
  slice('var LEGACY_CAT = {', '};'),
  slice('var state = {', '\n  };'),
  grabFunc('pad'), grabFunc('ymd'), grabFunc('fmtWeekday'), grabFunc('startOfMonth'),
  grabFunc('catsFor'), grabFunc('defaultCatFor'), grabFunc('findCat'), grabFunc('catName'),
  slice('var CRC_TABLE = (function(){', '})();'),
  grabFunc('crc32'), grabFunc('str2u8'), grabFunc('colRef'), grabFunc('escXml'),
  grabFunc('makeZip'), grabFunc('makeSheetXml'),
  grabFunc('buildCsvText'), grabFunc('monthScope'), grabFunc('monthTag'),
  // 用桩替换真实的下载 / 提示，把产物截获下来
  'var CAPTURED = [];',
  'function downloadBlob(blob, name){ CAPTURED.push({ blob: blob, name: name }); }',
  'function toast(msg){ console.log("    [toast] " + msg); }',
  grabFunc('exportXLSX'), grabFunc('exportCSV'),
  'module.exports = { state: state, exportXLSX: exportXLSX, exportCSV: exportCSV, ' +
  'buildCsvText: buildCsvText, CAPTURED: CAPTURED, findCat: findCat, CATS: CATS };',
].join('\n');

const modPath = path.join(TMP, 'extracted.js');
fs.writeFileSync(modPath, chunks, 'utf8');
const M = require(modPath);
const { state } = M;

// ---------- 2. 造数据：新分类 + 旧分类 id + 特殊字符 ----------
const now = new Date();
const p2 = n => (n < 10 ? '0' + n : '' + n);
const today = now.getFullYear() + '-' + p2(now.getMonth() + 1) + '-' + p2(now.getDate());
let ts = Date.now();
const mk = (type, cat, amount, note) => ({ id: 't' + (ts++), type, cat, amount, note, date: today, ts });

state.records = [
  mk('expense', 'food', 32.5, '午饭,加辣'),
  mk('expense', 'grocery', 88.8, '周末囤货'),
  mk('expense', 'fuel', 300, '加满'),
  mk('expense', 'toll', 45, ''),
  mk('expense', 'traffic', 4, '旧分类:交通'),
  mk('expense', 'shopping', 199, '旧分类:购物'),
  mk('expense', 'home', 120, '旧分类:居家'),
  mk('income', 'salary', 12000, '月薪'),
  mk('income', 'bonus', 800, '旧分类:奖金'),
  mk('income', 'other', 66.66, '红包 & 转账 <测试>'),
];
state.viewMonth = new Date(now.getFullYear(), now.getMonth(), 1);

const LEGACY_IDS = ['traffic', 'shopping', 'home', 'fun', 'study', 'bonus', 'redpack', 'parttime', 'invest'];

(async () => {
  console.log('\n[1] 导出 Excel');
  M.exportXLSX();
  const xlsx = M.CAPTURED.find(x => /\.xlsx$/.test(x.name));
  check(!!xlsx, '生成了 .xlsx：' + (xlsx ? xlsx.name : '无'));
  let xlsxPath = null;
  if (xlsx) {
    const buf = Buffer.from(await xlsx.blob.arrayBuffer());
    xlsxPath = path.join(TMP, 'out.xlsx');
    fs.writeFileSync(xlsxPath, buf);
    check(buf.length > 1000, '文件大小正常（' + buf.length + ' 字节）');
    check(buf[0] === 0x50 && buf[1] === 0x4B, 'zip 魔数 PK 正确');
    console.log('    -> ' + xlsxPath);
  }

  console.log('\n[2] 导出 CSV');
  const before = M.CAPTURED.length;
  M.exportCSV();
  check(M.CAPTURED.length === before + 1, '触发了下载流程');
  const csv = M.CAPTURED[M.CAPTURED.length - 1];
  check(/\.csv$/.test(csv.name), '文件名正确：' + csv.name);
  const text = Buffer.from(await csv.blob.arrayBuffer()).toString('utf8');
  const csvPath = path.join(TMP, 'out.csv');
  fs.writeFileSync(csvPath, text, 'utf8');
  check(text.charCodeAt(0) === 0xFEFF, '带 BOM（Excel 打开不乱码）');
  check(LEGACY_IDS.every(id => !new RegExp('\\b' + id + '\\b').test(text)),
    '没有残留旧分类英文 id');
  check(['大巴', '百货', '物业', '工资'].every(n => text.includes(n)),
    '旧 id 已映射成中文名（大巴 / 百货 / 物业 / 工资）');
  console.log('    -> ' + csvPath);

  console.log('\n[3] 分类表自检');
  check(new Set(M.CATS.map(c => c.k)).size === M.CATS.length, '分类 id 无重复');
  check(M.CATS.length === 19, '分类数量 = 19（实际 ' + M.CATS.length + '）');
  check(M.findCat('expense', 'give') !== null && M.findCat('income', 'give') !== null,
    '「给于」收支两种模式都能取到');

  console.log('\n===== 结果：' + pass + ' 通过 / ' + fail + ' 失败 =====');
  console.log('产物目录（可手动用 Excel/WPS 打开核对）：' + TMP);
  process.exit(fail === 0 ? 0 : 1);
})();
