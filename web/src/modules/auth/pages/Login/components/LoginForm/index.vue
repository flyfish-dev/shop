<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  CheckCircleOutlined,
  DownOutlined,
  GithubOutlined,
  GoogleOutlined,
  LockOutlined,
  MailOutlined,
  SendOutlined,
  WechatOutlined,
  WindowsOutlined
} from '@ant-design/icons-vue';
import giteeLogo from '@/assets/gitee.svg';
import giteaLogo from '@/assets/gitea-text.svg';
import { getQrCode, getResult } from '@/modules/auth/pages/Login/api';
import { useRouter } from '@/router/use';
import useClientStore from '@/modules/auth/store/client.js';
import { PortalOauth } from '@/modules/auth/api.js';
import { useEmailMagicLink } from './useEmailMagicLink.js';
import WechatLoginPanel from './WechatLoginPanel.vue';

const { t, locale } = useI18n();
const router = useRouter();
const store = useClientStore();
const moreOpen = ref(false);
const wechatOpen = ref(false);
const qrCode = ref('');
const scene = ref('');
const message = ref('');
const simpleMode = ref(false);
const loading = ref(false);
const polling = ref(false);
const status = ref('IDLE');
const oauthProviders = ref({
  email: true,
  google: true,
  github: true,
  microsoft: true,
  wechat: true,
  gitea: true,
  gitee: true
});
let pollTimer = null;

const statusText = computed(() => {
  if (message.value && locale.value === 'zh-CN') {
    return message.value;
  }
  const key = simpleMode.value && status.value === 'WAITING'
    ? 'auth.wechat.statusReply'
    : `auth.wechat.status.${status.value}`;
  return t(key);
});
const hasOverlay = computed(() => ['LOADING', 'CONFIRMED', 'EXPIRED', 'ERROR'].includes(status.value));
const emailEnabled = computed(() => oauthProviders.value.email !== false);
const isChinese = computed(() => locale.value === 'zh-CN');
const providerCatalog = computed(() => ({
  google: { key: 'google', label: t('auth.providers.google'), icon: GoogleOutlined, className: 'google' },
  github: { key: 'github', label: t('auth.providers.github'), icon: GithubOutlined, className: 'github' },
  microsoft: { key: 'microsoft', label: t('auth.providers.microsoft'), icon: WindowsOutlined, className: 'microsoft' },
  wechat: { key: 'wechat', label: t('auth.providers.wechat'), icon: WechatOutlined, className: 'wechat' },
  gitee: { key: 'gitee', label: t('auth.providers.gitee'), image: giteeLogo, imageOnly: true, className: 'gitee' },
  gitea: { key: 'gitea', label: t('auth.providers.gitea'), image: giteaLogo, imageOnly: true, className: 'gitea' }
}));
const availableProviders = keys => keys
  .filter(key => oauthProviders.value[key] !== false)
  .map(key => providerCatalog.value[key]);
const primaryProviders = computed(() => availableProviders(
  isChinese.value
    ? ['gitee', 'github', 'gitea']
    : ['google', 'github', 'microsoft']
));
const secondaryProviders = computed(() => availableProviders(
  isChinese.value
    ? ['google', 'microsoft']
    : ['wechat', 'gitee', 'gitea']
));
const hasPrimaryProviders = computed(() => primaryProviders.value.length > 0);
const hasSecondaryProviders = computed(() => secondaryProviders.value.length > 0);

const clearPollTimer = () => {
  if (pollTimer) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
};

const schedulePoll = (delay = 1200) => {
  clearPollTimer();
  pollTimer = setTimeout(() => waitForResult(), delay);
};

const fetchQrCode = async () => {
  clearPollTimer();
  loading.value = true;
  polling.value = false;
  status.value = 'LOADING';
  try {
    const { simple, url, sceneId } = await getQrCode();
    simpleMode.value = simple;
    qrCode.value = url;
    scene.value = sceneId;
    message.value = '';
    status.value = 'WAITING';
    schedulePoll(500);
  } catch (e) {
    status.value = 'ERROR';
    message.value = '';
  } finally {
    loading.value = false;
  }
};

const rememberRedirect = () => {
  store.rememberOAuthLoginRedirect(store.redirection || '/');
};

const startOauth = provider => {
  if (!oauthProviders.value[provider]) {
    return;
  }
  rememberRedirect();
  window.location.assign(`/oauth/${provider}`);
};

const hideWechat = () => {
  wechatOpen.value = false;
  polling.value = false;
  clearPollTimer();
};

const showWechat = async ({ expandMore = !isChinese.value, remember = true } = {}) => {
  if (!oauthProviders.value.wechat) {
    return;
  }
  if (expandMore) {
    moreOpen.value = true;
  }
  wechatOpen.value = true;
  if (remember) {
    rememberRedirect();
  }
  if (!qrCode.value || ['EXPIRED', 'ERROR'].includes(status.value)) {
    await fetchQrCode();
  } else if (status.value !== 'CONFIRMED') {
    schedulePoll(300);
  }
};

const toggleWechat = () => {
  if (wechatOpen.value) {
    hideWechat();
    return;
  }
  showWechat({ expandMore: false });
};

const toggleMore = () => {
  moreOpen.value = !moreOpen.value;
  if (!moreOpen.value && !isChinese.value) {
    hideWechat();
  }
};

const loadOauthProviders = async () => {
  try {
    oauthProviders.value = {
      ...oauthProviders.value,
      ...(await PortalOauth.providers())
    };
  } catch (e) {
    oauthProviders.value = {
      email: true,
      google: false,
      github: false,
      microsoft: false,
      wechat: true,
      gitea: false,
      gitee: false
    };
  }
  if (isChinese.value && oauthProviders.value.wechat) {
    await showWechat({ expandMore: false, remember: false });
  }
};

const normalizeRedirect = value => {
  const redirect = (value || '').trim();
  return redirect && redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/';
};

const emailRedirect = computed(() => normalizeRedirect(store.redirection));
const {
  emailAddress,
  emailSending,
  emailNotice,
  emailNoticeType,
  emailCanSend,
  emailSendButtonText,
  sendEmailLogin
} = useEmailMagicLink({ emailEnabled, redirect: emailRedirect });

const completeLogin = async ({ token }) => {
  if (!token) {
    return;
  }
  clearPollTimer();
  status.value = 'CONFIRMED';
  message.value = '';
  await store.completeLogin(token, router, '/');
};

const waitForResult = async () => {
  if (!scene.value || status.value === 'CONFIRMED') {
    return;
  }
  polling.value = true;
  try {
    const response = await getResult(scene.value);
    const nextStatus = response?.status || 'WAITING';
    status.value = nextStatus;
    message.value = response?.statusText || '';
    if (nextStatus === 'CONFIRMED') {
      await completeLogin(response);
      return;
    }
    if (nextStatus !== 'EXPIRED') {
      schedulePoll(nextStatus === 'SCANNED' ? 1000 : 1500);
    }
  } catch (e) {
    status.value = 'ERROR';
    message.value = '';
  } finally {
    polling.value = false;
  }
};

watch(locale, value => {
  if (value === 'zh-CN' && oauthProviders.value.wechat) {
    showWechat({ expandMore: false, remember: false });
  } else {
    hideWechat();
  }
});

onMounted(loadOauthProviders);
onBeforeUnmount(clearPollTimer);
</script>

<template>
  <a-form class="login-form" :class="{ 'locale-zh': isChinese }" @submit.prevent="sendEmailLogin">
    <section v-if="isChinese && oauthProviders.wechat" class="wechat-priority">
      <button
        type="button"
        class="wechat-primary-button"
        :aria-expanded="wechatOpen"
        @click="toggleWechat"
      >
        <span class="wechat-mark"><wechat-outlined /></span>
        <span class="wechat-button-copy">
          <strong>{{ t('auth.wechat.title') }}</strong>
          <small>{{ t('auth.wechat.hint') }}</small>
        </span>
        <span class="recommended-label">{{ t('auth.recommended') }}</span>
        <down-outlined class="wechat-chevron" :class="{ rotated: wechatOpen }" />
      </button>
      <transition name="method-expand">
        <wechat-login-panel
          v-if="wechatOpen"
          :has-overlay="hasOverlay"
          :loading="loading"
          :polling="polling"
          :qr-code="qrCode"
          :scene="scene"
          :simple-mode="simpleMode"
          :status="status"
          :status-text="statusText"
          @reload="fetchQrCode"
        />
      </transition>
    </section>

    <section class="email-section" :aria-label="t('auth.email.title')">
      <label class="section-label" for="login-email">{{ t('auth.email.title') }}</label>
      <div class="email-row">
        <a-input
          id="login-email"
          v-model:value="emailAddress"
          :maxlength="128"
          allow-clear
          autocomplete="email"
          :placeholder="t('auth.email.placeholder')"
          :disabled="emailSending || !emailEnabled"
          @pressEnter="sendEmailLogin"
        >
          <template #prefix><mail-outlined /></template>
        </a-input>
        <a-button
          type="primary"
          html-type="submit"
          :loading="emailSending"
          :disabled="!emailCanSend"
        >
          <template #icon><send-outlined /></template>
          {{ emailSendButtonText }}
        </a-button>
      </div>
      <p v-if="emailNotice" class="email-notice" :class="emailNoticeType" role="status">
        <check-circle-outlined v-if="emailNoticeType === 'success'" />
        <span>{{ emailNotice }}</span>
      </p>
      <p v-else class="email-hint">{{ t('auth.email.hint') }}</p>
    </section>

    <template v-if="hasPrimaryProviders">
      <a-divider>{{ isChinese ? t('auth.accountOptions') : t('auth.orContinue') }}</a-divider>

      <div class="primary-providers">
        <button
          v-for="provider in primaryProviders"
          :key="provider.key"
          type="button"
          class="provider-button"
          :class="provider.className"
          @click="startOauth(provider.key)"
        >
          <component v-if="provider.icon" :is="provider.icon" class="provider-icon" />
          <img v-else :src="provider.image" class="provider-logo" alt="" />
          <span :class="{ 'visually-hidden': provider.imageOnly }">{{ provider.label }}</span>
        </button>
      </div>
    </template>

    <button
      v-if="hasSecondaryProviders"
      type="button"
      class="more-toggle"
      :aria-expanded="moreOpen"
      @click="toggleMore"
    >
      <span>{{ isChinese ? t('auth.otherOptions') : t('auth.moreOptions') }}</span>
      <down-outlined :class="{ rotated: moreOpen }" />
    </button>

    <transition name="method-expand">
      <div v-if="moreOpen && hasSecondaryProviders" class="secondary-section">
        <div class="secondary-providers">
          <button
            v-for="provider in secondaryProviders"
            :key="provider.key"
            type="button"
            class="secondary-button"
            :class="provider.className"
            @click="provider.key === 'wechat' ? showWechat() : startOauth(provider.key)"
          >
            <component v-if="provider.icon" :is="provider.icon" />
            <img v-else :src="provider.image" class="secondary-logo" alt="" />
            <span :class="{ 'visually-hidden': provider.imageOnly }">{{ provider.label }}</span>
          </button>
        </div>

        <wechat-login-panel
          v-if="!isChinese && wechatOpen"
          :has-overlay="hasOverlay"
          :loading="loading"
          :polling="polling"
          :qr-code="qrCode"
          :scene="scene"
          :simple-mode="simpleMode"
          :status="status"
          :status-text="statusText"
          @reload="fetchQrCode"
        />
      </div>
    </transition>

    <p class="privacy-note"><lock-outlined /> {{ t('auth.privacy') }}</p>
  </a-form>
</template>

<style scoped lang="less">
.login-form {
  color: #172338;
  font-family: Avenir, "Helvetica Neue", Arial, Helvetica, sans-serif;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
  white-space: nowrap;
}

.wechat-priority {
  display: grid;
  gap: 10px;
  margin-bottom: 17px;
}

.wechat-primary-button {
  position: relative;
  display: grid;
  width: 100%;
  min-height: 58px;
  grid-template-columns: 38px minmax(0, 1fr) auto 16px;
  gap: 11px;
  align-items: center;
  padding: 9px 13px;
  overflow: hidden;
  border: 1px solid #9dd7b5;
  border-radius: 8px;
  background: #f4fbf7;
  color: #16432d;
  cursor: pointer;
  text-align: left;
  transition: border-color .18s ease, background-color .18s ease, box-shadow .18s ease;

  &:hover,
  &:focus-visible {
    border-color: #42ad70;
    background: #eef9f2;
    box-shadow: 0 8px 22px rgba(18, 118, 65, .10);
    outline: none;
  }
}

.wechat-mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 50%;
  background: #20ad58;
  color: #fff;
  font-size: 21px;
}

.wechat-button-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;

  strong { font-size: 14px; line-height: 1.35; }
  small { overflow: hidden; color: #60786a; font-size: 11px; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
}

.recommended-label {
  padding: 3px 7px;
  border: 1px solid #a9ddbd;
  border-radius: 5px;
  background: #fff;
  color: #148146;
  font-size: 10px;
  line-height: 1.3;
}

.wechat-chevron {
  color: #527160;
  font-size: 11px;
  transition: transform .18s ease;

  &.rotated { transform: rotate(180deg); }
}

.section-label {
  display: block;
  margin-bottom: 9px;
  color: #24344d;
  font-weight: 650;
  font-size: 14px;
}

.email-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;

  :deep(.ant-input-affix-wrapper),
  :deep(.ant-btn) {
    height: 44px;
    border-radius: 6px;
    box-shadow: none;
  }

  :deep(.ant-input-prefix) {
    margin-right: 8px;
    color: #708097;
  }

  :deep(.ant-btn) {
    padding-inline: 17px;
    font-weight: 600;
  }
}

.locale-zh .email-section {
  padding-top: 1px;
}

.email-hint,
.email-notice {
  min-height: 20px;
  margin: 8px 0 0;
  color: #6b778c;
  font-size: 12px;
  line-height: 1.55;
}

.email-notice {
  display: flex;
  align-items: flex-start;
  gap: 6px;

  &.success { color: #27723e; }
  &.error { color: #b42318; }
}

:deep(.ant-divider) {
  margin: 20px 0 16px;
  color: #8591a3;
  font-size: 12px;
}

.primary-providers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
}

.provider-button,
.secondary-button,
.more-toggle {
  border: 1px solid #dce2ea;
  background: #fff;
  color: #24324a;
  cursor: pointer;
  transition: border-color .18s ease, background-color .18s ease, box-shadow .18s ease, color .18s ease;
}

.provider-button {
  display: flex;
  min-width: 0;
  height: 47px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 10px;
  border-radius: 6px;
  font-weight: 600;
  font-size: 13px;

  &:hover {
    border-color: #9eb8d7;
    background: #f8fbff;
    box-shadow: 0 5px 14px rgba(29, 62, 99, .08);
  }

  .provider-icon { flex: 0 0 auto; font-size: 18px; }
  .provider-logo { width: auto; max-width: 68px; height: 19px; object-fit: contain; }
  &.google .provider-icon { color: #4285f4; }
  &.github .provider-icon { color: #171a1f; }
  &.microsoft .provider-icon { color: #0078d4; }
}

.more-toggle {
  display: flex;
  width: 100%;
  height: 38px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 13px;
  border-color: transparent;
  border-radius: 6px;
  color: #607087;
  font-size: 13px;

  &:hover { background: #f5f7fa; color: #245f9d; }
  .anticon { transition: transform .18s ease; }
  .rotated { transform: rotate(180deg); }
}

.secondary-section {
  padding-top: 4px;
}

.secondary-providers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.secondary-button {
  display: flex;
  min-width: 0;
  height: 40px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 8px;
  border-radius: 6px;
  font-size: 12px;

  &:hover { border-color: #abc3d9; background: #f8fafc; }
  &.wechat { color: #167e48; }
  img { object-fit: contain; }
  .secondary-logo { width: auto; max-width: 62px; height: 17px; }
}

.secondary-section :deep(.wechat-panel) { margin-top: 10px; }

.method-expand-enter-active,
.method-expand-leave-active {
  transform-origin: top;
  transition: opacity .2s ease, transform .2s ease;
}

.method-expand-enter-from,
.method-expand-leave-to {
  opacity: 0;
  transform: translateY(-5px);
}

.privacy-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 18px 0 0;
  color: #8490a1;
  font-size: 11px;
  line-height: 1.5;
  text-align: center;
}

@media (min-width: 521px) and (max-height: 820px) {
  .wechat-priority { gap: 8px; margin-bottom: 12px; }
  .wechat-primary-button { min-height: 54px; padding-block: 7px; }
  .section-label { margin-bottom: 7px; }

  .email-row :deep(.ant-input-affix-wrapper),
  .email-row :deep(.ant-btn) { height: 42px; }

  .email-hint,
  .email-notice { min-height: 18px; margin-top: 6px; }

  :deep(.ant-divider) { margin: 14px 0 12px; }
  .provider-button { height: 44px; }
  .more-toggle { height: 34px; margin-top: 9px; }
  .privacy-note { margin-top: 13px; }
}

@media (max-width: 520px) {
  .email-row { grid-template-columns: 1fr; }
  .email-row :deep(.ant-btn) { width: 100%; }
  .primary-providers { gap: 6px; }
  .provider-button { gap: 5px; padding-inline: 5px; font-size: 12px; }
  .recommended-label { display: none; }
  .wechat-primary-button { grid-template-columns: 38px minmax(0, 1fr) 16px; }
}

@media (prefers-reduced-motion: reduce) {
  .provider-button,
  .secondary-button,
  .more-toggle,
  .wechat-primary-button,
  .wechat-chevron,
  .method-expand-enter-active,
  .method-expand-leave-active { transition: none; }
}
</style>
