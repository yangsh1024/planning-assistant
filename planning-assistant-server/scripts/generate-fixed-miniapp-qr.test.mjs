import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { generateFixedMiniProgramQrCode } from './generate-fixed-miniapp-qr.mjs';

const PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

test('generates a fixed login-page QR PNG without calling the unlimited-code endpoint', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'planning-fixed-qr-'));
  const outputPath = join(directory, 'fixed-qr.png');
  const calls = [];
  const request = async (url, options = {}) => {
    calls.push({ url, options });
    if (url.includes('/cgi-bin/token')) return jsonResponse({ access_token: 'test-token', expires_in: 7200 });
    if (url.includes('/wxa/getwxacode')) return binaryResponse(PNG, 'image/png');
    throw new Error(`unexpected URL: ${url}`);
  };

  try {
    await generateFixedMiniProgramQrCode({
      appId: 'test-appid',
      secret: 'test-secret',
      outputPath,
      request,
    });

    assert.deepEqual(await readFile(outputPath), PNG);
    assert.equal(calls.length, 2);
    assert.match(calls[1].url, /\/wxa\/getwxacode\?/);
    assert.doesNotMatch(calls[1].url, /getwxacodeunlimit/);
    assert.deepEqual(JSON.parse(calls[1].options.body), {
      page: 'pages/auth/web-login/web-login',
      width: 430,
    });
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

function jsonResponse(value) {
  return {
    ok: true,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => value,
    text: async () => JSON.stringify(value),
  };
}

function binaryResponse(value, contentType) {
  return {
    ok: true,
    headers: new Headers({ 'content-type': contentType }),
    arrayBuffer: async () => value,
    text: async () => '',
  };
}
