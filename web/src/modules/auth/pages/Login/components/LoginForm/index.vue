<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import gitee from '@/assets/gitee.svg';
import gitea from '@/assets/gitea-text.svg';
import github from '@/assets/github-text.svg';
import {
  CheckCircleOutlined,
  LoadingOutlined,
  MailOutlined,
  ReloadOutlined,
  SendOutlined
} from '@ant-design/icons-vue'
import { getQrCode, getResult } from '@/modules/auth/pages/Login/api';
import { useRouter } from '@/router/use';
import useClientStore from '@/modules/auth/store/client.js';
import { PortalOauth } from '@/modules/auth/api.js';
import { useEmailMagicLink } from './useEmailMagicLink.js';

const router = useRouter();
const store = useClientStore();
const qrCode = ref('');
const scene = ref('');
const message = ref('');
const simpleMode = ref(false);
const loading = ref(false);
const polling = ref(false);
const status = ref('IDLE');
const oauthProviders = ref({
  email: true,
  gitea: true,
  gitee: true,
  github: true
});
let pollTimer = null;

const statusText = computed(() => {
  if (message.value) {
    return message.value;
  }
  const texts = {
    IDLE: '准备登录二维码',
    LOADING: '正在加载二维码',
    WAITING: simpleMode.value ? '等待公众号回复' : '等待扫码',
    SCANNED: '等待验证码回复',
    CONFIRMED: '登录成功，正在进入平台',
    EXPIRED: '二维码已过期，请重新获取',
    ERROR: '请稍后重试',
  };
  return texts[status.value] || '等待扫码';
});

const hasOverlay = computed(() => ['LOADING', 'CONFIRMED', 'EXPIRED', 'ERROR'].includes(status.value));
const emailEnabled = computed(() => oauthProviders.value?.email !== false);

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
    simpleMode.value = simple
    qrCode.value = url;
    scene.value = sceneId;
    message.value = '';
    status.value = 'WAITING';
    schedulePoll(500);
  } catch (e) {
    status.value = 'ERROR';
    message.value = '';
  } finally {
    loading.value = false
  }
}

const loadOauthProviders = async () => {
  try {
    oauthProviders.value = {
      ...oauthProviders.value,
      ...(await PortalOauth.providers())
    };
  } catch (e) {
    oauthProviders.value = {
      email: false,
      gitea: false,
      gitee: false,
      github: false
    };
  }
};

const startOauth = provider => {
  if (!oauthProviders.value?.[provider]) {
    return;
  }
  store.rememberOAuthLoginRedirect(store.redirection || '/');
  window.location.assign(`/oauth/${provider}`);
};

const normalizeRedirect = value => {
  const redirect = (value || '').trim();
  if (redirect && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect;
  }
  return '/';
};

const emailRedirect = computed(() => normalizeRedirect(store.redirection));
const {
  emailLoginOpen,
  emailAddress,
  emailSending,
  emailNotice,
  emailNoticeType,
  emailCanSend,
  emailSendButtonText,
  toggleEmailLogin,
  sendEmailLogin
} = useEmailMagicLink({
  emailEnabled,
  redirect: emailRedirect
});

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
    const resp = await getResult(scene.value)
    const nextStatus = resp?.status || 'WAITING';
    status.value = nextStatus;
    message.value = resp?.statusText || '';
    if (nextStatus === 'CONFIRMED') {
      await completeLogin(resp);
      return;
    }
    if (nextStatus === 'EXPIRED') {
      return;
    }
    schedulePoll(nextStatus === 'SCANNED' ? 1000 : 1500);
  } catch (e) {
    status.value = 'ERROR';
    message.value = '';
  } finally {
    polling.value = false;
  }
}

onMounted(() => {
  fetchQrCode();
  loadOauthProviders();
})

onBeforeUnmount(clearPollTimer);
</script>

<template>
<a-form class='login-form'>
  <div class='login-main'>
    <h2>微信扫码登录</h2>
    <p v-if='simpleMode'>公众号回复<span>{{scene}}</span></p>
    <p v-else>扫码后回复<span>{{scene}}</span></p>
    <div class='qr-code'>
      <img :src='qrCode' alt='登录二维码' :class='{muted: hasOverlay}' />
      <div v-if='hasOverlay' class='overlay'>
        <div v-if='loading || status === "LOADING"'>
          <loading-outlined /> {{statusText}}
        </div>
        <div v-else>
          <div>{{statusText}}</div>
          <a v-if='status !== "CONFIRMED"' @click='fetchQrCode'><reload-outlined /> 重新获取</a>
        </div>
      </div>
    </div>
    <div class='login-status' :class='status.toLowerCase()'>
      <loading-outlined v-if='polling && status !== "CONFIRMED"' />
      {{statusText}}
    </div>
  </div>
  <div class='other'>
    <a-divider style='border-color: darkgrey'>其它登录方式</a-divider>
    <div class='oauth-links'>
      <a
        class='other-link gitee-login'
        :class='{ disabled: !oauthProviders.gitee }'
        :href='oauthProviders.gitee ? "/oauth/gitee" : undefined'
        title='码云登录'
        @click.prevent='startOauth("gitee")'
      >
        <img :src='gitee' alt='码云'>
      </a>
      <a
        class='other-link gitea-login'
        :class='{ disabled: !oauthProviders.gitea }'
        :href='oauthProviders.gitea ? "/oauth/gitea" : undefined'
        title='Gitea 登录'
        @click.prevent='startOauth("gitea")'
      >
        <img :src='gitea' alt='Gitea'>
      </a>
      <a
        class='other-link github-login'
        :class='{ disabled: !oauthProviders.github }'
        :href='oauthProviders.github ? "/oauth/github" : undefined'
        title='GitHub 登录'
        @click.prevent='startOauth("github")'
      >
        <img :src='github' alt='GitHub'>
      </a>
      <button
        type='button'
        class='other-link text-login email-login'
        :class='{ active: emailLoginOpen, disabled: !emailEnabled }'
        :disabled='!emailEnabled || emailSending'
        title='邮箱快速登录'
        @click='toggleEmailLogin'
      >
        <span class='login-icon'>
          <mail-outlined />
        </span>
        <span>邮箱登录</span>
      </button>
    </div>
    <div v-if='emailLoginOpen' class='email-login-panel'>
      <a-input
        v-model:value='emailAddress'
        :maxlength='128'
        allow-clear
        placeholder='邮箱地址'
        :disabled='emailSending'
        @pressEnter='sendEmailLogin'
      >
        <template #prefix>
          <mail-outlined />
        </template>
      </a-input>
      <a-button type='primary' :loading='emailSending' :disabled='!emailCanSend' @click='sendEmailLogin'>
        <template #icon>
          <send-outlined />
        </template>
        {{ emailSendButtonText }}
      </a-button>
      <p v-if='emailNotice' class='email-login-note' :class='emailNoticeType'>
        <check-circle-outlined v-if='emailNoticeType === "success"' />
        <span>{{ emailNotice }}</span>
      </p>
    </div>
  </div>
</a-form>
</template>

<style scoped lang='less'>
.login-form {
  .login-main {
    p>span {
      color: #5cb05c;
      font-weight: bold;
      font-size: 19px;
      margin: 0 2px;
    }
    .qr-code {
      width: 200px;
      height: 200px;
      margin: 0 auto;
      padding: 10px;
      background-color: #e1e1e1;
      position: relative;
      img {
        display: block;
        line-height: 170px;
        width: 100%;
        height: 100%;
        transition: opacity .2s ease, filter .2s ease;

        &.muted {
          opacity: .35;
          filter: grayscale(.35);
        }
      }
      div {
        width: 100%;
      }
      a {
        display: block;
        margin-top: 12px;
      }
      .overlay {
        position: absolute;
        width: 100%;
        height: 100%;
        left: 0;
        top: 0;
        background: #000000AB;
        color: white;
        padding: 20px;
        display: flex;
        align-items: center;
      }
    }
    .login-status {
      display: flex;
      width: 260px;
      min-height: 36px;
      margin: 14px auto 0;
      padding: 8px 12px;
      align-items: center;
      justify-content: center;
      gap: 8px;
      border: 1px solid #d9eddf;
      border-radius: 8px;
      background: #f6fbf7;
      color: #40614b;
      font-size: 13px;
      line-height: 1.45;

      &.scanned {
        border-color: #91caff;
        background: #f0f7ff;
        color: #0958d9;
      }

      &.confirmed {
        border-color: #b7eb8f;
        background: #f6ffed;
        color: #237804;
      }

      &.expired,
      &.error {
        border-color: #ffccc7;
        background: #fff2f0;
        color: #a8071a;
      }
    }
  }
  .other {
    margin-top: 24px;
    line-height: 22px;
    text-align: center;

    .oauth-links {
      display: flex;
      align-items: center;
      justify-content: center;
      flex-wrap: wrap;
      gap: 14px;
    }

    .other-link {
      display: inline-flex;
      width: auto;
      min-width: 112px;
      height: 52px;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 12px 14px;
      border: 0;
      border-radius: 8px;
      background: transparent;
      color: #253248;
      font-family: Avenir, Helvetica Neue, Arial, Helvetica, sans-serif;
      font-size: 15px;
      line-height: 1;
      cursor: pointer;
      transition: background-color .2s ease, color .2s ease, box-shadow .2s ease;

      &:hover {
        background-color: #edf5ff;
      }

      &.active {
        background-color: #e6f4ff;
        color: #0958d9;
        box-shadow: inset 0 0 0 1px #91caff;
      }

      &.disabled {
        cursor: not-allowed;
        opacity: .38;

        &:hover {
          background-color: transparent;
        }
      }

      img {
        display: block;
        max-height: 26px;
        width: auto;
        max-width: 112px;
        object-fit: contain;
      }

      &.text-login {
        min-width: 124px;
        border: 1px solid #e5ebf3;
        background: #fff;
        color: #1f2f46;
        box-shadow: 0 8px 20px rgba(31, 47, 70, .06);

        &:hover {
          border-color: #b9d6ff;
          background: #f7fbff;
          color: #0958d9;
          box-shadow: 0 10px 24px rgba(22, 119, 255, .12);
        }

        &.active {
          border-color: #91caff;
          background: #eef7ff;
          color: #0958d9;
          box-shadow: 0 10px 24px rgba(22, 119, 255, .14);
        }

        &.disabled {
          background: #f8fafc;
          box-shadow: none;
        }

        .login-icon {
          display: inline-flex;
          width: 26px;
          height: 26px;
          align-items: center;
          justify-content: center;
          border-radius: 50%;
          background: linear-gradient(135deg, #edf6ff 0%, #e8fff4 100%);
          color: #1677ff;

          .anticon {
            font-size: 15px;
          }
        }
      }
    }

    .email-login-panel {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 12px;
      align-items: center;
      width: min(100%, 380px);
      margin: 16px auto 0;
      padding: 14px 16px;
      border: 1px solid #dfeaf8;
      border-radius: 8px;
      background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
      box-shadow: 0 12px 32px rgba(33, 54, 88, .08);

      :deep(.ant-input-affix-wrapper) {
        height: 40px;
        border-color: #d9e5f3;
        border-radius: 8px;
        font-family: Avenir, Helvetica Neue, Arial, Helvetica, sans-serif;

        .ant-input-prefix {
          color: #6d7f94;
        }
      }

      :deep(.ant-btn) {
        height: 40px;
        border-radius: 8px;
        font-family: Avenir, Helvetica Neue, Arial, Helvetica, sans-serif;
        box-shadow: none;
      }

      .email-login-note {
        grid-column: 1 / -1;
        display: flex;
        min-height: 20px;
        align-items: center;
        gap: 6px;
        margin: 0;
        color: #42526d;
        font-size: 13px;
        line-height: 1.45;
        text-align: left;

        &.success {
          color: #237804;
        }

        &.error {
          color: #a8071a;
        }
      }
    }

    @media (max-width: 430px) {
      .email-login-panel {
        grid-template-columns: 1fr;

        :deep(.ant-btn) {
          width: 100%;
        }
      }
    }
  }
  a {
    color: #1890ff;
    text-decoration: none;
    background-color: transparent;
    outline: none;
    cursor: pointer;
    transition: color 0.3s;
    -webkit-text-decoration-skip: objects;
  }
}

</style>
