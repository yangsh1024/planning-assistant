/** 统一生成和展示年月，保证接口交互固定使用 yyyy-MM。 */
function formatYearMonth(year, month) {
  return `${year}-${String(month).padStart(2, '0')}`;
}

function toYearMonthLabel(yearMonth) {
  const [year, month] = yearMonth.split('-');
  return `${year}年${parseInt(month, 10)}月`;
}

module.exports = { formatYearMonth, toYearMonthLabel };
