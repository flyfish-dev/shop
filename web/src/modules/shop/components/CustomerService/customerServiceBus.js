const OPEN_EVENT = 'flyfish:customer-service-open';

export const openCustomerService = payload => {
  window.dispatchEvent(new CustomEvent(OPEN_EVENT, { detail: payload || {} }));
};

export const listenCustomerServiceOpen = handler => {
  const listener = event => handler(event.detail || {});
  window.addEventListener(OPEN_EVENT, listener);
  return () => window.removeEventListener(OPEN_EVENT, listener);
};
