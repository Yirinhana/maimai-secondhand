export interface CommunityPage<T> {items:T[];total:number;page:number;size:number;totalPages:number}
export interface Favorite {id:number;productId:number;productTitle:string;productPriceCents:number;productStatus:string;isPublicVisible:boolean;collectedAt:string}
export interface Follow {id:number;sellerId:number;sellerNickname:string;followedAt:string}
export interface Footprint {id:number;productId:number;productTitle:string;productPriceCents:number;productStatus:string;viewedAt:string}
export interface Demand {id:number;authorId:number;authorNickname:string;title:string;description:string;budgetMinCents:number;budgetMaxCents:number;categoryId:number|null;region:string;status:string;isClosed:boolean;createdAt:string;updatedAt:string;reviewReason:string|null}
export interface DemandReply {id:number;demandId:number;authorId:number;authorNickname:string;content:string;status:string;createdAt:string;reviewReason:string|null}
export interface Rating {id:number;orderId:number;reviewerId:number;rateeId:number;reviewerNickname:string;rating:number;comment:string|null;refundStatus:string;createdAt:string}
export interface Report {id:number;reporterId:number;resourceType:string;resourceId:number;reason:string;status:string;processAction:string|null;processNote:string|null;createdAt:string}
export const moderationText:Record<string,string>={PENDING:'等待审核',PUBLISHED:'已发布',APPROVED:'已通过',REJECTED:'未通过',HIDDEN:'已隐藏',CLOSED:'已关闭',PENDING_REVIEW:'等待审核',RESOLVED:'已处理',OPEN:'待处理'}
