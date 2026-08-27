import { describe, expect, it } from 'vitest';
import { parseSseBuffer } from './sse';

describe('parseSseBuffer', () => {
  it('keeps a fragmented event for the next network chunk', () => {
    const first = parseSseBuffer('event: delta\ndata: {"text":"你');
    expect(first.events).toEqual([]);

    const second = parseSseBuffer(first.remainder + '好"}\n\n');
    expect(second.events).toEqual([{ event: 'delta', data: { text: '你好' } }]);
    expect(second.remainder).toBe('');
  });

  it('parses multiple CRLF events without exposing unknown text', () => {
    const result = parseSseBuffer('event: message_start\r\ndata: {"messageId":"1"}\r\n\r\nevent: done\r\ndata: {"messageId":"1"}\r\n\r\n');
    expect(result.events.map(item => item.event)).toEqual(['message_start', 'done']);
  });
});
