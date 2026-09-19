import type { ApiError } from './api';

export function transportError(cause: unknown, method: string): ApiError {
  if (typeof navigator !== 'undefined' && navigator.onLine === false)
    return {
      code: 'OFFLINE',
      message:
        '当前网络已断开，请连接网络后重试。已填写的内容会保留在当前页面。',
    };
  const name =
    cause && typeof cause === 'object' && 'name' in cause
      ? cause.name
      : '';
  if (name === 'TimeoutError' || name === 'AbortError')
    return {
      code: 'TIMEOUT',
      message:
        method === 'GET'
          ? '加载时间有些长，请稍后重试。'
          : '暂未收到操作结果，请先刷新确认是否已完成，再决定是否重试。',
    };
  return {
    code: 'NETWORK_ERROR',
    message:
      method === 'GET'
        ? '连接暂时中断，请检查网络后重试。'
        : '连接暂时中断，操作结果尚未确认。请先查看最新状态，避免重复提交。',
  };
}

export function responseError(status: number): string {
  if (status === 401) return '登录已过期，请重新登录后继续。';
  if (status === 403)
    return '当前账号无法进行此操作，请确认登录身份或联系平台客服。';
  if (status === 404)
    return '内容已不存在或暂时不可见，请返回列表重新选择。';
  if (status === 413) return '文件太大，请压缩图片后重新上传。';
  if (status === 429) return '操作有些频繁，请稍等片刻再试。';
  if (status >= 500) return '服务暂时繁忙，请稍后重试。';
  return '操作未完成，请检查填写内容后重试。';
}
