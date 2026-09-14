export interface Ticket {id:number;ownerId:number;ownerNickname:string;title:string;orderNo:string|null;status:'OPEN'|'IN_PROGRESS'|'CLOSED';assignedTo:number|null;createdAt:string;updatedAt:string}
export interface SupportMessage {id:number;ticketId:number;authorId:number|null;authorKind:'USER'|'STAFF'|'AI';body:string;createdAt:string}
export interface Faq {topic:'FEES'|'PAYMENT'|'AFTERSALES'|'DELIVERY'|'ACCOUNT';title:string;answer:string}
export const ticketStatus={OPEN:'等待客服接待',IN_PROGRESS:'客服处理中',CLOSED:'已关闭'}
