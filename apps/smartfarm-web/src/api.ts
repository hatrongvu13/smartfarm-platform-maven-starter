export type Me = {userId:string;tenantId:string;roles:string[];permissions:string[]}
export type Tokens = {accessToken:string;refreshToken:string;expiresIn?:number;tokenType?:string}
export type Task = {taskId:string;farmId:string;title:string;type?:string;status:string;assigneeId?:string;dueAt?:number;assignedAt?:number;acceptDeadlineAt?:number;acceptedAt?:number;reportDueAt?:number;reportedAt?:number;completedAt?:number}
export type Animal = {animalId:string;farmId:string;tagCode:string;status:string;species?:string;barnId?:string;batchId?:string;birthDate?:number}
export type Schedule = {scheduleId:string;farmId:string;title:string;type?:string;cronExpression:string;timeZone:string;enabled:boolean;nextRunAt?:number}
export type OrderLine = {itemId:string;quantity:string;unit:string;unitPriceMinor:number;warehouseId?:string;currency?:string}
export type Order = {orderId:string;farmId:string;batchId?:string;status:string;totalMinor:number;currency:string;createdAt:string;failureReason?:string;lines:OrderLine[]}
export type Report = {jobId:string;farmId:string;type:string;format:string;status:string;createdAt:string;completedAt?:string;errorCode?:string}
export type Download = {url:string;contentType:string;expiresAt:string}
export class HttpError extends Error {constructor(public status:number,message:string){super(message);this.name='HttpError'}}
export type TokenReader=()=>string|null
let readToken:TokenReader=()=>null
export function setTokenReader(reader:TokenReader){readToken=reader}
export async function api<T>(path:string,options:RequestInit={},authenticated=true):Promise<T>{
 const headers=new Headers(options.headers)
 if(options.body&&!headers.has('Content-Type'))headers.set('Content-Type','application/json')
 if(authenticated){const token=readToken();if(!token)throw new HttpError(401,'Phiên đăng nhập đã hết');headers.set('Authorization',`Bearer ${token}`)}
 const res=await fetch(path,{...options,headers,cache:'no-store'})
 if(!res.ok){let message=`HTTP ${res.status}`;try{const body=await res.json() as {message?:string;error?:string;detail?:string};message=body.message||body.detail||body.error||message}catch{ /* no JSON body */ }throw new HttpError(res.status,message)}
 if(res.status===204)return undefined as T
 return res.json() as Promise<T>
}
export function get<T>(path:string){return api<T>(path)}
export function post<T>(path:string,body?:unknown,key?:string){return api<T>(path,{method:'POST',headers:key?{'Idempotency-Key':key}:undefined,body:body===undefined?undefined:JSON.stringify(body)})}
export function put<T>(path:string){return api<T>(path,{method:'PUT'})}
export function del<T>(path:string){return api<T>(path,{method:'DELETE'})}
export function params(base:string,values:Record<string,string|number|undefined>){const url=new URL(base,window.location.origin);for(const [k,v] of Object.entries(values))if(v!==undefined&&String(v).trim())url.searchParams.set(k,String(v));return url.pathname+url.search}
export function newKey(){return crypto.randomUUID()}
export async function gql<T>(query:string,variables:Record<string,unknown>={}){
 const response=await post<{data?:T;errors?:{message:string}[]}>('/graphql',{query,variables})
 if(response.errors?.length)throw new Error(response.errors.map(e=>e.message).join('; '))
 if(!response.data)throw new Error('GraphQL không trả dữ liệu')
 return response.data
}
export function time(value?:number|string|null){if(value==null||value==='')return '—';const d=new Date(value);return Number.isNaN(d.getTime())?'—':d.toLocaleString('vi-VN')}
export function money(value?:number,currency='VND'){if(value==null)return '—';return new Intl.NumberFormat('vi-VN',{style:'currency',currency,maximumFractionDigits:0}).format(value)}
