import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { sendEmailMagicLink } from '@/modules/auth/pages/Login/api';

const EMAIL_PATTERN = /^[a-zA-Z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/;
const DEFAULT_COOLDOWN_SECONDS = 120;
const STORAGE_PREFIX = 'flyfish:email-magic-link:cooldown:';

const normalizeEmail = value => (value || '').trim().toLowerCase();

const isStorageAvailable = () => typeof window !== 'undefined' && !!window.localStorage;

const storageKey = email => `${STORAGE_PREFIX}${encodeURIComponent(email)}`;

const readCooldownUntil = email => {
  if (!isStorageAvailable() || !email) {
    return 0;
  }
  const value = Number(window.localStorage.getItem(storageKey(email)));
  if (!Number.isFinite(value) || value <= Date.now()) {
    window.localStorage.removeItem(storageKey(email));
    return 0;
  }
  return value;
};

const writeCooldownUntil = (email, seconds) => {
  if (!isStorageAvailable() || !email || seconds <= 0) {
    return 0;
  }
  const until = Date.now() + seconds * 1000;
  window.localStorage.setItem(storageKey(email), String(until));
  return until;
};

const secondsUntil = until => {
  if (!until || until <= Date.now()) {
    return 0;
  }
  return Math.max(1, Math.ceil((until - Date.now()) / 1000));
};

const pickCooldownSeconds = result => {
  const value = Number(result?.resendCooldownSeconds);
  if (Number.isFinite(value) && value > 0) {
    return Math.ceil(value);
  }
  return DEFAULT_COOLDOWN_SECONDS;
};

const parseCooldownFromError = error => {
  const message = error?.message || '';
  const matched = message.match(/(\d+)\s*秒/);
  return matched ? Number(matched[1]) : 0;
};

export function useEmailMagicLink({ emailEnabled, redirect }) {
  const emailLoginOpen = ref(false);
  const emailAddress = ref('');
  const emailSending = ref(false);
  const emailNotice = ref('');
  const emailNoticeType = ref('info');
  const emailCooldown = ref(0);

  let cooldownTimer = null;
  let cooldownEmail = '';
  let cooldownUntil = 0;

  const emailCanSend = computed(() => emailEnabled.value && !emailSending.value && emailCooldown.value <= 0);
  const emailSendButtonText = computed(() => (
    emailCooldown.value > 0 ? `${emailCooldown.value}s 后重发` : '发送验证邮件'
  ));

  const clearCooldownTimer = () => {
    if (cooldownTimer) {
      clearInterval(cooldownTimer);
      cooldownTimer = null;
    }
  };

  const refreshCooldown = () => {
    emailCooldown.value = secondsUntil(cooldownUntil);
    if (emailCooldown.value <= 0) {
      clearCooldownTimer();
    }
  };

  const startCooldown = (email, seconds) => {
    const normalizedEmail = normalizeEmail(email);
    clearCooldownTimer();
    cooldownEmail = normalizedEmail;
    cooldownUntil = writeCooldownUntil(normalizedEmail, seconds);
    refreshCooldown();
    if (emailCooldown.value > 0) {
      cooldownTimer = setInterval(refreshCooldown, 1000);
    }
  };

  const syncCooldownFromStorage = email => {
    const normalizedEmail = normalizeEmail(email);
    clearCooldownTimer();
    cooldownEmail = normalizedEmail;
    cooldownUntil = readCooldownUntil(normalizedEmail);
    refreshCooldown();
    if (emailCooldown.value > 0) {
      cooldownTimer = setInterval(refreshCooldown, 1000);
    }
  };

  const toggleEmailLogin = () => {
    if (!emailEnabled.value) {
      return;
    }
    emailLoginOpen.value = !emailLoginOpen.value;
    if (emailLoginOpen.value) {
      emailNotice.value = '';
      emailNoticeType.value = 'info';
      syncCooldownFromStorage(emailAddress.value);
    }
  };

  const sendEmailLogin = async () => {
    if (!emailEnabled.value || emailSending.value) {
      return;
    }
    const email = normalizeEmail(emailAddress.value);
    if (!EMAIL_PATTERN.test(email)) {
      emailNotice.value = '请输入正确的邮箱地址';
      emailNoticeType.value = 'error';
      return;
    }
    syncCooldownFromStorage(email);
    if (emailCooldown.value > 0) {
      emailNotice.value = `验证邮件已发送，请 ${emailCooldown.value} 秒后再试`;
      emailNoticeType.value = 'info';
      return;
    }
    emailSending.value = true;
    emailNotice.value = '';
    emailNoticeType.value = 'info';
    try {
      const result = await sendEmailMagicLink({
        email,
        redirect: redirect.value
      });
      startCooldown(email, pickCooldownSeconds(result));
      emailNotice.value = `验证邮件已发送至 ${result?.maskedEmail || email}`;
      emailNoticeType.value = 'success';
    } catch (e) {
      const cooldown = parseCooldownFromError(e);
      if (cooldown > 0) {
        startCooldown(email, cooldown);
      }
      emailNotice.value = e.message || '验证邮件发送失败，请稍后重试';
      emailNoticeType.value = 'error';
    } finally {
      emailSending.value = false;
    }
  };

  watch(emailAddress, value => {
    const email = normalizeEmail(value);
    if (email !== cooldownEmail) {
      syncCooldownFromStorage(email);
    }
  });

  onBeforeUnmount(clearCooldownTimer);

  return {
    emailLoginOpen,
    emailAddress,
    emailSending,
    emailNotice,
    emailNoticeType,
    emailCooldown,
    emailCanSend,
    emailSendButtonText,
    toggleEmailLogin,
    sendEmailLogin
  };
}
