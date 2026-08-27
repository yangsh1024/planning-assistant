import type { StreamEvent } from './api';

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
