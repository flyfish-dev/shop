import { get, post } from '@/network/request';

export function getQrCode(params = {}, options = {}) {
  return get('/wx/qr-codes', {
    ...options,
    params
  })
}

export function getResult(code, options = {}) {
  return get(`/wx/qr-codes/${code}`, options);
}

export function sendEmailMagicLink(body, options = {}) {
  return post('/email/magic-links', {
    ...options,
    body
  });
}
