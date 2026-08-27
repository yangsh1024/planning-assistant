import { describe, expect, it } from 'vitest';
import { MARKDOWN_ALLOWED_ATTR, MARKDOWN_ALLOWED_TAGS } from './markdownPolicy';

describe('markdown policy', () => {
  it('does not allow remote media or interactive HTML', () => {
    expect(MARKDOWN_ALLOWED_TAGS).not.toContain('img');
    expect(MARKDOWN_ALLOWED_TAGS).not.toContain('form');
    expect(MARKDOWN_ALLOWED_TAGS).not.toContain('input');
    expect(MARKDOWN_ALLOWED_ATTR).not.toContain('src');
    expect(MARKDOWN_ALLOWED_ATTR).not.toContain('onerror');
  });
});
