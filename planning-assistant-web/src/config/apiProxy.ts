export const DEFAULT_API_PROXY_TARGET = 'http://localhost:8080';

/**
 * 开发服务器的 /api 代理仅在 Vite 运行时使用；生产静态文件仍通过同域 Nginx 转发 /api。
 */
export function resolveApiProxyTarget(env: Record<string, string | undefined>): string {
  return env.VITE_API_PROXY_TARGET || DEFAULT_API_PROXY_TARGET;
}
