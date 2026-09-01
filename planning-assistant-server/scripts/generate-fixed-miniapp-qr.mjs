#!/usr/bin/env node
import { writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

const FIXED_LOGIN_PAGE = 'pages/auth/web-login/web-login';
const QR_WIDTH = 430;

/**
 * 生成固定指向网页登录确认页的小程序码。
 *
 * 调用微信 getwxacode（非 getwxacodeunlimit）接口。生成的 PNG 不携带登录请求或登录码，
 * 可上传到静态资源域名后配置为 APP_FIXED_QR_URL。
 */
export async function generateFixedMiniProgramQrCode({ appId, secret, outputPath, request = fetch }) {
  if (!appId || !secret) throw new Error('WECHAT_APPID 和 WECHAT_SECRET 均不能为空。');
  if (!outputPath) throw new Error('请提供二维码 PNG 输出路径。');

  const tokenUrl = new URL('https://api.weixin.qq.com/cgi-bin/token');
  tokenUrl.search = new URLSearchParams({
    grant_type: 'client_credential',
    appid: appId,
    secret,
  }).toString();
  const tokenResponse = await request(tokenUrl.toString());
  const token = await readJson(tokenResponse, '获取微信 access_token 失败');
  if (!token.access_token) throw new Error(`获取微信 access_token 失败：${safeWeChatError(token)}`);

  const qrResponse = await request(
    `https://api.weixin.qq.com/wxa/getwxacode?access_token=${encodeURIComponent(token.access_token)}`,
    {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ path: FIXED_LOGIN_PAGE, width: QR_WIDTH,env_version:'trial'}),
    },
  );
  const contentType = qrResponse.headers.get('content-type') || '';
  if (!qrResponse.ok || contentType.includes('application/json')) {
    const detail = await responseText(qrResponse);
    throw new Error(`生成固定小程序码失败：${detail || `HTTP ${qrResponse.status}`}`);
  }
  const image = Buffer.from(await qrResponse.arrayBuffer());
  await writeFile(outputPath, image);
}

async function readJson(response, prefix) {
  if (!response.ok) throw new Error(`${prefix}：${await responseText(response) || `HTTP ${response.status}`}`);
  try {
    return await response.json();
  } catch {
    throw new Error(`${prefix}：响应不是有效 JSON。`);
  }
}

async function responseText(response) {
  try {
    return await response.text();
  } catch {
    return '';
  }
}

function safeWeChatError(value) {
  const code = typeof value.errcode === 'number' ? `errcode=${value.errcode}` : '';
  const message = typeof value.errmsg === 'string' ? value.errmsg.replace(/[\r\n\t]/g, ' ').slice(0, 160) : '';
  return [code, message].filter(Boolean).join(', ') || '未返回 access_token';
}

async function main() {
  const outputPath = process.argv[2];
  if (!outputPath) {
    console.error('用法：WECHAT_APPID=... WECHAT_SECRET=... node scripts/generate-fixed-miniapp-qr.mjs <输出PNG路径>');
    process.exitCode = 1;
    return;
  }
  await generateFixedMiniProgramQrCode({
    appId: process.env.WECHAT_APPID,
    secret: process.env.WECHAT_SECRET,
    outputPath,
  });
  console.log(`固定小程序码已生成：${outputPath}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
