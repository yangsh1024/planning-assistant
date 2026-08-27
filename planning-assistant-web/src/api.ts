export type Result<T> = { code: number; message: string; data: T };
export type Session = { sessionId: string; title: string; updatedAt: string };
export type AgentAction = { actionId:string; type:string; summary:string; payload:Record<string,unknown>; result?:Record<string,unknown>; status:'PENDING_CONFIRMATION'|'EXECUTING'|'CANCELLED'|'EXPIRED'|'STALE'|'EXECUTED'|'FAILED'; expiresAt:string };
export type Message = { messageId: string; role: 'USER'|'ASSISTANT'; content: string; action?:AgentAction; status: string; createdAt: string };
const csrf = () => document.cookie.split('; ').find(v => v.startsWith('XSRF-TOKEN='))?.split('=')[1] || '';
function unauthorized():never { window.dispatchEvent(new Event('agent-unauthorized')); throw new Error('UNAUTHORIZED'); }
export async function request<T>(url:string, options:RequestInit = {}):Promise<T> { const headers = new Headers(options.headers); if (options.method && options.method !== 'GET') headers.set('X-CSRF-TOKEN', csrf()); headers.set('Content-Type','application/json'); const response=await fetch(url,{...options,headers,credentials:'same-origin'}); if(response.status===401) unauthorized(); const body=await response.json() as Result<T>; if(!response.ok || body.code!==200) throw new Error(body.message); return body.data; }
export const sessions=()=>request<Session[]>('/api/agent/sessions');
export const messages=(id:string)=>request<Message[]>(`/api/agent/sessions/${id}/messages`);
export async function exchangeTicket(ticket:string) { return request<void>('/api/web-auth/sso/exchange',{method:'POST',body:JSON.stringify({ticket})}); }
export const confirmAction=(id:string)=>request<AgentAction>(`/api/agent/actions/${id}/confirm`,{method:'POST',body:'{}'});
export const cancelAction=(id:string)=>request<AgentAction>(`/api/agent/actions/${id}/cancel`,{method:'POST',body:'{}'});
export type StreamEvent = { event:string; data:Record<string, unknown> };
import { parseSseBuffer } from './sse';
export async function chat(body:{sessionId?:string;message:string;thinkingEnabled:boolean;retryUserMessageId?:string}, onEvent:(event:StreamEvent)=>void) { const headers=new Headers({'Content-Type':'application/json','X-CSRF-TOKEN':csrf()}); const response=await fetch('/api/agent/chat',{method:'POST',headers,credentials:'same-origin',body:JSON.stringify(body)}); if(response.status===401) unauthorized(); if(!response.ok){let message='请求失败';try{message=((await response.json()) as Result<unknown>).message||message;}catch{}throw new Error(message);}if(!response.body)throw new Error('响应流不可用'); const reader=response.body.getReader(); const decoder=new TextDecoder(); let buffer=''; while(true){const {done,value}=await reader.read(); if(done) break; buffer+=decoder.decode(value,{stream:true}); const parsed=parseSseBuffer(buffer);buffer=parsed.remainder;parsed.events.forEach(onEvent); } }
