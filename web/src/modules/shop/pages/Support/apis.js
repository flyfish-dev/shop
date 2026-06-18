import { get, post } from '@/network/request.js';

export const getTickets = params => get('/portal/tickets', { params, credential: true });

export const createTicket = body => post('/portal/tickets', { body, credential: true });

export const getTicket = ticketNo => get(`/portal/tickets/${ticketNo}`, { credential: true });

export const replyTicket = (ticketNo, body) => post(`/portal/tickets/${ticketNo}/messages`, {
  body,
  credential: true
});

export const getManagedTickets = params => get('/portal/tickets/managements', { params, credential: true });

export const getManagedTicket = ticketNo => get(`/portal/tickets/managements/${ticketNo}`, { credential: true });

export const replyManagedTicket = (ticketNo, body) => post(`/portal/tickets/managements/${ticketNo}/messages`, {
  body,
  credential: true
});

export const resolveManagedTicket = ticketNo => post(`/portal/tickets/managements/${ticketNo}/resolve`, {
  credential: true
});
