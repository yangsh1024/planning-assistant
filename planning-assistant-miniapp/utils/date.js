/** Shared date helpers. Year-month values always use yyyy-MM. */
function formatYearMonth(year, month) {
  return `${year}-${String(month).padStart(2, '0')}`;
}

function toYearMonthLabel(yearMonth) {
  const [year, month] = yearMonth.split('-');
  return `${year}年${parseInt(month, 10)}月`;
}

module.exports = { formatYearMonth, toYearMonthLabel };
