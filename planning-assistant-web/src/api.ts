export type Result<T> = { code: number; message: string; data: T };
export type Session = { sessionId: string; title: string; updatedAt: string };
export type AgentAction = { actionId:string; type:string; summary:string; payload:Record<string,unknown>; result?:Record<string,unknown>; status:'PENDING_CONFIRMATION'|'EXECUTING'|'CANCELLED'|'EXPIRED'|'STALE'|'EXECUTED'|'FAILED'; expiresAt:string };
export type Message = { messageId: string; role: 'USER'|'ASSISTANT'; content: string; action?:AgentAction; status: string; createdAt: string };
// Web 身份依赖 HttpOnly Cookie；此处只读取配套的 CSRF 校验值。
/**
 * 读取同域 CSRF Cookie 的公开校验值。
 * <ol><li>读取 Cookie</li><li>提取令牌</li></ol>
 *
 * @return 当前浏览器的 CSRF Token；不存在时为空字符串
 */
const csrf = () => document.cookie.split('; ').find(v => v.startsWith('XSRF-TOKEN='))?.split('=')[1] || '';

/**
 * 通知页面当前 Web 会话已失效。
 * <ol><li>派发事件</li><li>中断请求</li></ol>
 *
 * @throws Error 始终抛出未认证错误以终止当前调用
 */
function unauthorized():never { window.dispatchEvent(new Event('agent-unauthorized')); throw new Error('UNAUTHORIZED'); }
/**
 * 调用同域 REST 接口并展开统一响应体。
 * <ol><li>附加防护</li><li>处理认证</li><li>提取数据</li></ol>
 * @param url 接口路径
 * @param options 请求选项
 * @return 响应中的业务数据
 */
export async function request<T>(url:string, options:RequestInit = {}):Promise<T> { const headers = new Headers(options.headers); if (options.method && options.method !== 'GET') headers.set('X-CSRF-TOKEN', csrf()); headers.set('Content-Type','application/json'); const response=await fetch(url,{...options,headers,credentials:'same-origin'}); if(response.status===401) unauthorized(); const body=await response.json() as Result<T>; if(!response.ok || body.code!==200) throw new Error(body.message); return body.data; }
export const sessions=()=>request<Session[]>('/api/agent/sessions');
export const messages=(id:string)=>request<Message[]>(`/api/agent/sessions/${id}/messages`);
/**
 * 用 URL fragment 中的一次性票据交换 Web Cookie。
 * <ol><li>提交票据</li><li>建立会话</li></ol>
 *
 * @param ticket 小程序生成的短时票据
 * @return Cookie 写入完成后的 Promise
 */
export async function exchangeTicket(ticket:string) { return request<void>('/api/web-auth/sso/exchange',{method:'POST',body:JSON.stringify({ticket})}); }
export const confirmAction=(id:string)=>request<AgentAction>(`/api/agent/actions/${id}/confirm`,{method:'POST',body:'{}'});
export const cancelAction=(id:string)=>request<AgentAction>(`/api/agent/actions/${id}/cancel`,{method:'POST',body:'{}'});
export type StreamEvent = { event:string; data:Record<string, unknown> };
import { parseSseBuffer } from './sse';
/**
 * 发起 POST SSE 对话并逐个交付公开事件。
 * <ol><li>发送消息</li><li>拼接分块</li><li>分发事件</li></ol>
 * @param body 本轮对话请求
 * @param onEvent SSE 事件处理函数
 * @return 请求完成后的 Promise
 */
export async function chat(body:{sessionId?:string;message:string;thinkingEnabled:boolean;retryUserMessageId?:string}, onEvent:(event:StreamEvent)=>void) { const headers=new Headers({'Content-Type':'application/json','X-CSRF-TOKEN':csrf()}); const response=await fetch('/api/agent/chat',{method:'POST',headers,credentials:'same-origin',body:JSON.stringify(body)}); if(response.status===401) unauthorized(); if(!response.ok){let message='请求失败';try{message=((await response.json()) as Result<unknown>).message||message;}catch{}throw new Error(message);}if(!response.body)throw new Error('响应流不可用'); const reader=response.body.getReader(); const decoder=new TextDecoder(); let buffer=''; while(true){const {done,value}=await reader.read(); if(done) break; buffer+=decoder.decode(value,{stream:true}); const parsed=parseSseBuffer(buffer);buffer=parsed.remainder;parsed.events.forEach(onEvent); } }
