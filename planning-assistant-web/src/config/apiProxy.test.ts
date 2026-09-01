import { describe, expect, it } from 'vitest';
import { resolveApiProxyTarget } from './apiProxy';

describe('resolveApiProxyTarget', () => {
  it('uses the backend address configured for the current Vite mode', () => {
    expect(resolveApiProxyTarget({ VITE_API_PROXY_TARGET: 'http://127.0.0.1:18082' }))
      .toBe('http://127.0.0.1:18082');
  });

  it('uses the default development backend when no address is configured', () => {
    expect(resolveApiProxyTarget({})).toBe('http://localhost:8080');
  });
});
