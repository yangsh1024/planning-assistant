import type { StreamEvent } from './api';

/**
 * 从累计的网络分块中解析完整 SSE 事件。
 * <ol><li>统一换行</li><li>提取事件</li><li>保留残片</li></ol>
 * @param buffer 已接收但尚未完全消费的文本
 * @return 完整事件与等待后续分块的剩余文本
 */
export function parseSseBuffer(buffer: string): { events: StreamEvent[]; remainder: string } {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const blocks = normalized.split('\n\n');
  const remainder = blocks.pop() || '';
  const events: StreamEvent[] = [];
  for (const block of blocks) {
    const event = block.match(/^event:\s*(.+)$/m)?.[1];
    const dataLines = block.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart());
    if (event && dataLines.length) events.push({ event, data: JSON.parse(dataLines.join('\n')) as Record<string, unknown> });
  }
  return { events, remainder };
}
