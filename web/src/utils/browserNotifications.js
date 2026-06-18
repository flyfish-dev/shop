const DEFAULT_ICON = '/favicon.ico';
const AUTO_CLOSE_MS = 8000;

export const browserNotificationSupported = () => {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return false;
  }
  return window.isSecureContext || window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
};

export const browserNotificationPermission = () => {
  if (!browserNotificationSupported()) {
    return 'unsupported';
  }
  return window.Notification.permission;
};

export const requestBrowserNotificationPermission = async () => {
  if (!browserNotificationSupported()) {
    return 'unsupported';
  }
  if (window.Notification.permission !== 'default') {
    return window.Notification.permission;
  }
  try {
    return await window.Notification.requestPermission();
  } catch (e) {
    return window.Notification.permission;
  }
};

export const showBrowserNotification = options => {
  if (!browserNotificationSupported() || window.Notification.permission !== 'granted') {
    return null;
  }

  try {
    const notification = new window.Notification(options.title, {
      body: options.body,
      icon: options.icon || DEFAULT_ICON,
      tag: options.tag,
      renotify: Boolean(options.tag),
      silent: false
    });
    notification.onclick = event => {
      event.preventDefault();
      window.focus();
      options.onClick?.();
      notification.close();
    };
    window.setTimeout(() => notification.close(), AUTO_CLOSE_MS);
    return notification;
  } catch (e) {
    return null;
  }
};
