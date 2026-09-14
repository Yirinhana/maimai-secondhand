export type ArticleCategory = 'NOTICE' | 'GUIDE' | 'SAFETY' | 'ABOUT'
export type ArticleStatus = 'DRAFT' | 'PUBLISHED' | 'WITHDRAWN'
export interface OfficialArticle {
  id: number
  slug: string
  title: string
  summary: string
  category: ArticleCategory
  publishedAt: string | null
  updatedAt: string
  body?: string
  publisher?: string
  status?: ArticleStatus
}
export const categories: Record<ArticleCategory, string> = {
  NOTICE: '平台公告',
  GUIDE: '买卖指南',
  SAFETY: '交易安全',
  ABOUT: '关于麦麦',
}
export const statuses: Record<ArticleStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  WITHDRAWN: '已撤回',
}
export const articleDate = (date?: string | null) =>
  date
    ? new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      }).format(new Date(date))
    : '未发布'
