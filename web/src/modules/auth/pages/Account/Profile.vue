<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  CheckCircleFilled,
  DisconnectOutlined,
  GithubOutlined,
  LinkOutlined,
  LoadingOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  UserOutlined,
  WechatOutlined
} from '@ant-design/icons-vue';
import { storeToRefs } from 'pinia';
import useClientStore from '@/modules/auth/store/client.js';
import { PortalOauth, PortalUsers } from '@/modules/auth/api.js';
import { getQrCode, getResult } from '@/modules/auth/pages/Login/api.js';
import { useRoute, useRouter } from '@/router/use.js';
import giteeLogo from '@/assets/gitee.svg';
import giteaLogo from '@/assets/gitea-text.svg';
import { getAuthorizationDisplay } from '@/modules/auth/authority.js';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';

const store = useClientStore();
const route = useRoute();
const router = useRouter();
const { profileReturnPath, user } = storeToRefs(store);
const {
  hasShop,
  shopEntryName,
  shopEntryPath,
  loadPortalCapabilities
} = usePortalCapabilities();

const saving = ref(false);
const formRef = ref(null);
const avatarFileList = ref([]);
const bindingProvider = ref('');
const unbindingType = ref('');
const oauthProviders = ref({
  wechat: true,
  gitea: true,
  gitee: true,
  github: true
});
const formState = reactive({
  username: '',
  avatar: '',
  phone: '',
  email: '',
  bio: ''
});

const PHONE_PATTERN = /^\+?[0-9][0-9\-\s]{6,19}$/;
const EMAIL_PATTERN = /^[a-zA-Z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/;
const AVATAR_MAX_SIZE = 2 * 1024 * 1024;

const trimValue = value => (value || '').trim();

const validateUsername = async (_rule, value) => {
  if (!trimValue(value)) {
    throw new Error('请输入昵称');
  }
  if (trimValue(value).length > 64) {
    throw new Error('昵称不能超过 64 个字符');
  }
};

const validatePhone = async (_rule, value) => {
  const phone = trimValue(value);
  if (phone && !PHONE_PATTERN.test(phone)) {
    throw new Error('请输入正确的手机号，支持区号、空格和短横线');
  }
};

const validateEmail = async (_rule, value) => {
  const email = trimValue(value);
  if (email && !EMAIL_PATTERN.test(email)) {
    throw new Error('请输入正确的邮箱地址');
  }
};

const profileRules = {
  username: [{ required: true, validator: validateUsername, trigger: ['blur', 'change'] }],
  phone: [{ validator: validatePhone, trigger: ['blur', 'change'] }],
  email: [{ validator: validateEmail, trigger: ['blur', 'change'] }],
  bio: [{ max: 1024, message: '个人简介不能超过 1024 个字符', trigger: ['blur', 'change'] }]
};

const wechatVisible = ref(false);
const wechatLoading = ref(false);
const wechatPolling = ref(false);
const wechatSimple = ref(false);
const wechatQr = ref('');
const wechatScene = ref('');
const wechatStatus = ref('IDLE');
const wechatMessage = ref('');
let wechatPollTimer = null;

const syncForm = () => {
  formState.username = user.value?.username || '';
  formState.avatar = user.value?.avatar || '';
  formState.phone = user.value?.phone || '';
  formState.email = user.value?.email || '';
  formState.bio = user.value?.bio || '';
  avatarFileList.value = formState.avatar ? [{
    uid: '-1',
    name: '当前头像',
    status: 'done',
    url: formState.avatar
  }] : [];
};

watch(user, syncForm, { immediate: true });

onMounted(async () => {
  await Promise.all([store.loadUser(), loadOauthProviders(), loadPortalCapabilities()]);
  syncForm();
});

onBeforeUnmount(() => {
  clearWechatPoll();
});

const authorizations = computed(() => user.value?.authorizations || {});

const platforms = computed(() => [
  {
    type: 'wechat',
    title: '微信',
    color: '#18a058',
    icon: WechatOutlined,
    enabled: true,
    auth: authorizations.value.wechat
  },
  {
    type: 'gitea',
    title: '飞鱼开源',
    color: '#1677ff',
    logo: giteaLogo,
    enabled: oauthProviders.value.gitea,
    auth: authorizations.value.gitea
  },
  {
    type: 'gitee',
    title: '码云 Gitee',
    color: '#c71d23',
    logo: giteeLogo,
    enabled: oauthProviders.value.gitee,
    auth: authorizations.value.gitee
  },
  {
    type: 'github',
    title: 'GitHub',
    color: '#24292f',
    icon: GithubOutlined,
    enabled: oauthProviders.value.github,
    auth: authorizations.value.github
  }
]);

const hasUser = computed(() => user.value?.id > 0);
const showLoginWelcome = computed(() => route.query?.welcome === '1' || Boolean(profileReturnPath.value));
const matchesCapabilityPath = (target, capabilityPath) => {
  const path = (capabilityPath || '').trim();
  if (!target || !path || path === '/') {
    return false;
  }
  return target === path || target.startsWith(`${path}/`);
};
const profileReturnLabel = computed(() => {
  const target = profileReturnPath.value || '';
  if (hasShop.value && matchesCapabilityPath(target, shopEntryPath.value)) {
    return `返回${shopEntryName.value}`;
  }
  return '返回刚才页面';
});

const displayAuthName = auth => getAuthorizationDisplay(auth).name;
const displayAuthMeta = auth => {
  const display = getAuthorizationDisplay(auth);
  return [
    display.login ? `@${display.login}` : '',
    display.email,
    display.unionId ? `UnionID ${display.maskedUnionId}` : '',
    (!display.login && !display.email && !display.unionId && display.openid) ? `识别码 ${display.maskedOpenid}` : '',
    display.authTime
  ]
    .filter(Boolean)
    .join(' · ');
};

const saveProfile = async () => {
  if (!hasUser.value) {
    message.warning('请先登录');
    return;
  }
  saving.value = true;
  try {
    await formRef.value?.validate();
    let avatar = formState.avatar;
    const avatarFile = avatarFileList.value?.[0];
    if (avatarFile?.originFileObj) {
      const data = new FormData();
      data.append('file', avatarFile.originFileObj);
      avatar = await PortalUsers.uploadAvatar(data);
    } else if (avatarFile?.url) {
      avatar = avatarFile.url;
    } else {
      avatar = '';
    }
    const nextUser = await PortalUsers.updateCurrent({
      username: trimValue(formState.username),
      avatar,
      phone: trimValue(formState.phone),
      email: trimValue(formState.email),
      bio: trimValue(formState.bio)
    });
    store.user = nextUser;
    store.markProfilePromptSeen(nextUser);
    message.success('个人信息已更新');
  } catch (e) {
    if (e?.errorFields) {
      message.warning('请先修正表单中的提示');
    } else {
      message.error(e.message || '个人信息保存失败');
    }
  } finally {
    saving.value = false;
  }
};

const backToLoginOrigin = () => {
  store.returnToProfileOrigin(router);
};

const beforeAvatarUpload = file => {
  if (!file.type?.startsWith('image/')) {
    message.error('头像必须是图片文件');
    return false;
  }
  if (file.size > AVATAR_MAX_SIZE) {
    message.error('头像图片不能超过 2MB');
    return false;
  }
  return false;
};

const handleAvatarChange = ({ fileList }) => {
  avatarFileList.value = fileList
    .filter(file => {
      const rawFile = file.originFileObj || file;
      return !rawFile.type || (rawFile.type.startsWith('image/') && rawFile.size <= AVATAR_MAX_SIZE);
    })
    .slice(-1);
};

const loadOauthProviders = async () => {
  try {
    oauthProviders.value = {
      ...oauthProviders.value,
      ...(await PortalOauth.providers())
    };
  } catch (e) {
    oauthProviders.value = {
      ...oauthProviders.value,
      gitea: false,
      gitee: false,
      github: false
    };
  }
};

const bindPlatform = async platform => {
  if (!hasUser.value) {
    message.warning('请先登录');
    return;
  }
  if (platform.type === 'wechat') {
    await startWechatBinding();
    return;
  }
  if (!platform.enabled) {
    message.warning('暂不可用');
    return;
  }
  bindingProvider.value = platform.type;
  try {
    store.clearOAuthLoginRedirect();
    const url = await PortalUsers.prepareBinding(platform.type, '/account/profile');
    location.href = url || `/oauth/${platform.type}`;
  } catch (e) {
    message.error(e.message || '发起账号绑定失败');
  } finally {
    bindingProvider.value = '';
  }
};

const unbindPlatform = async platform => {
  unbindingType.value = platform.type;
  try {
    await PortalUsers.unbind(platform.type);
    await store.refreshUser();
    message.success(`${platform.title} 已解绑`);
  } catch (e) {
    message.error(e.message || '解绑失败');
  } finally {
    unbindingType.value = '';
  }
};

const clearWechatPoll = () => {
  if (wechatPollTimer) {
    clearTimeout(wechatPollTimer);
    wechatPollTimer = null;
  }
};

const scheduleWechatPoll = (delay = 1200) => {
  clearWechatPoll();
  wechatPollTimer = setTimeout(() => pollWechatBinding(), delay);
};

const startWechatBinding = async () => {
  clearWechatPoll();
  wechatVisible.value = true;
  wechatLoading.value = true;
  wechatStatus.value = 'LOADING';
  wechatMessage.value = '';
  try {
    const { simple, url, sceneId } = await getQrCode({ mode: 'bind' }, { credential: true });
    wechatSimple.value = simple;
    wechatQr.value = url;
    wechatScene.value = sceneId;
    wechatStatus.value = 'WAITING';
    scheduleWechatPoll(600);
  } catch (e) {
    wechatStatus.value = 'ERROR';
    wechatMessage.value = e.message || '';
  } finally {
    wechatLoading.value = false;
  }
};

const pollWechatBinding = async () => {
  if (!wechatScene.value || wechatStatus.value === 'CONFIRMED') {
    return;
  }
  wechatPolling.value = true;
  try {
    const resp = await getResult(wechatScene.value, { credential: true });
    const status = resp?.status || 'WAITING';
    wechatStatus.value = status;
    wechatMessage.value = resp?.statusText || '';
    if (status === 'CONFIRMED') {
      if (resp.token) {
        store.setToken(resp.token);
      }
      await store.refreshUser();
      message.success('微信账号已绑定');
      closeWechatBinding();
      return;
    }
    if (status !== 'EXPIRED') {
      scheduleWechatPoll(status === 'SCANNED' ? 1000 : 1500);
    }
  } catch (e) {
    wechatStatus.value = 'ERROR';
    wechatMessage.value = e.message || '';
  } finally {
    wechatPolling.value = false;
  }
};

const closeWechatBinding = () => {
  clearWechatPoll();
  wechatVisible.value = false;
  wechatPolling.value = false;
};

const wechatStatusText = computed(() => {
  if (wechatMessage.value) {
    return wechatMessage.value;
  }
  const texts = {
    IDLE: '准备绑定二维码',
    LOADING: '正在加载二维码',
    WAITING: wechatSimple.value ? '等待公众号回复' : '等待扫码',
    SCANNED: '等待验证码回复',
    CONFIRMED: '绑定成功',
    EXPIRED: '二维码已过期，请重新获取',
    ERROR: '请稍后重试'
  };
  return texts[wechatStatus.value] || '等待扫码';
});

const wechatOverlayVisible = computed(() => ['LOADING', 'ERROR', 'EXPIRED'].includes(wechatStatus.value));
</script>

<template>
  <div class='profile-page'>
    <header class='profile-header'>
      <div>
        <p class='eyebrow'>Account Center</p>
        <h2>个人信息维护</h2>
      </div>
      <div class='profile-header-actions'>
        <a-button
          v-if='profileReturnPath'
          type='primary'
          ghost
          @click='backToLoginOrigin'
        >
          {{ profileReturnLabel }}
        </a-button>
        <a-avatar v-if='user?.avatar' :size='64' :src='user.avatar' />
        <a-avatar v-else :size='64'>
          <user-outlined />
        </a-avatar>
      </div>
    </header>

    <a-alert
      v-if='showLoginWelcome'
      class='profile-welcome'
      type='success'
      show-icon
      message='登录成功'
      description='完善头像、联系方式和简介后，购买、工单和客服沟通会更顺畅。'
    >
      <template #action>
        <a-button size='small' type='primary' @click='backToLoginOrigin'>
          {{ profileReturnLabel }}
        </a-button>
      </template>
    </a-alert>

    <div class='profile-grid'>
      <a-card class='profile-panel' title='基础资料' :bordered='false'>
        <a-form ref='formRef' layout='vertical' :model='formState' :rules='profileRules'>
          <a-form-item label='昵称' name='username'>
            <a-input
              v-model:value='formState.username'
              :maxlength='64'
              @pressEnter='saveProfile'
            />
          </a-form-item>
          <a-form-item label='头像' class='avatar-form-item'>
            <div class='avatar-editor'>
              <a-upload
                v-model:file-list='avatarFileList'
                list-type='picture-card'
                :max-count='1'
                :before-upload='beforeAvatarUpload'
                accept='image/*'
                @change='handleAvatarChange'
              >
                <div v-if='!avatarFileList.length' class='avatar-upload-empty'>
                  <plus-outlined />
                  <span>上传头像</span>
                </div>
              </a-upload>
            </div>
          </a-form-item>
          <a-form-item label='手机号' name='phone'>
            <a-input
              v-model:value='formState.phone'
              :maxlength='32'
              allow-clear
              @pressEnter='saveProfile'
            />
          </a-form-item>
          <a-form-item label='邮箱' name='email'>
            <a-input
              v-model:value='formState.email'
              :maxlength='128'
              allow-clear
              @pressEnter='saveProfile'
            />
          </a-form-item>
          <a-form-item label='个人简介' name='bio'>
            <a-textarea
              v-model:value='formState.bio'
              :maxlength='1024'
              :rows='4'
              show-count
            />
          </a-form-item>
          <a-button type='primary' :loading='saving' @click='saveProfile'>
            <template #icon><save-outlined /></template>
            保存资料
          </a-button>
        </a-form>
      </a-card>

      <a-card class='profile-panel' title='社交账号绑定' :bordered='false'>
        <div class='platform-list'>
          <div v-for='platform in platforms' :key='platform.type' class='platform-row'>
            <div class='platform-icon' :style='{ color: platform.color }'>
              <img v-if='platform.logo' :src='platform.logo' :alt='platform.title'>
              <component v-else :is='platform.icon' />
            </div>
            <div class='platform-main'>
              <div class='platform-title'>
                <strong>{{ platform.title }}</strong>
                <a-tag v-if='platform.auth' color='success'>
                  <check-circle-filled />
                  已绑定
                </a-tag>
                <a-tag v-else-if='!platform.enabled' color='warning'>未配置</a-tag>
                <a-tag v-else>未绑定</a-tag>
              </div>
              <p v-if='platform.auth'>{{ displayAuthName(platform.auth) }}</p>
              <p v-if='platform.auth' class='platform-meta'>{{ displayAuthMeta(platform.auth) }}</p>
              <a v-if='platform.auth?.profileUrl' :href='platform.auth.profileUrl' target='_blank' rel='noreferrer'>
                查看平台主页
              </a>
            </div>
            <div class='platform-actions'>
              <a-button
                v-if='!platform.auth'
                type='primary'
                ghost
                :disabled='!platform.enabled'
                :loading='bindingProvider === platform.type'
                @click='bindPlatform(platform)'
              >
                <template #icon><link-outlined /></template>
                绑定
              </a-button>
              <a-space v-else wrap>
                <a-button
                  :disabled='!platform.enabled'
                  :loading='bindingProvider === platform.type'
                  @click='bindPlatform(platform)'
                >
                  <template #icon><reload-outlined /></template>
                  更新资料
                </a-button>
                <a-popconfirm
                  title='确定解绑该平台账号吗？'
                  ok-text='解绑'
                  cancel-text='取消'
                  @confirm='unbindPlatform(platform)'
                >
                  <a-button danger :loading='unbindingType === platform.type'>
                    <template #icon><disconnect-outlined /></template>
                    解绑
                  </a-button>
                </a-popconfirm>
              </a-space>
            </div>
          </div>
        </div>
      </a-card>
    </div>

    <a-modal
      v-model:open='wechatVisible'
      title='绑定微信账号'
      :footer='null'
      centered
      @cancel='closeWechatBinding'
    >
      <div class='wechat-bind'>
        <p v-if='wechatSimple'>公众号回复 <strong>{{ wechatScene }}</strong></p>
        <p v-else>扫码后回复 <strong>{{ wechatScene }}</strong></p>
        <div class='qr-box'>
          <img :src='wechatQr' alt='微信绑定二维码' :class='{ muted: wechatOverlayVisible }'>
          <div v-if='wechatOverlayVisible' class='qr-overlay'>
            <loading-outlined v-if='wechatLoading || wechatStatus === "LOADING"' />
            <span>{{ wechatStatusText }}</span>
            <a v-if='wechatStatus !== "LOADING"' @click='startWechatBinding'>
              <reload-outlined />
              重新获取
            </a>
          </div>
        </div>
        <div class='wechat-status' :class='wechatStatus.toLowerCase()'>
          <loading-outlined v-if='wechatPolling' />
          {{ wechatStatusText }}
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped lang='less'>
.profile-page {
  width: min(1120px, calc(100vw - 48px));
  margin: 0 auto 48px;
}

.profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 0 26px;

  .eyebrow {
    margin: 0 0 8px;
    color: #1677ff;
    font-size: 12px;
    font-weight: 700;
    line-height: 1.2;
  }

  h2 {
    margin: 0;
    color: #1d2b3a;
    font-size: 28px;
    line-height: 1.25;
    letter-spacing: 0;
  }

}

.profile-header-actions {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  flex: none;
}

.profile-welcome {
  margin: 0 0 18px;
  border: 1px solid rgba(51, 162, 4, .16);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(246, 255, 237, .92), rgba(255, 255, 255, .96));
  text-align: left;

  :deep(.ant-alert-action) {
    align-self: center;
  }
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(280px, .78fr) minmax(0, 1.22fr);
  gap: 20px;
  align-items: start;
}

.profile-panel {
  border-radius: 8px;
  box-shadow: 0 14px 36px rgba(24, 70, 120, .08);

  :deep(.ant-card-head) {
    border-bottom-color: #edf2f7;
  }
}

.platform-list {
  display: grid;
  gap: 14px;
}

.platform-row {
  display: grid;
  grid-template-columns: 50px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  min-height: 82px;
  padding: 14px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fff;
}

.platform-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  border-radius: 8px;
  background: #f7fafc;
  font-size: 24px;

  img {
    max-width: 34px;
    max-height: 26px;
  }
}

.platform-main {
  min-width: 0;

  p {
    margin: 6px 0 0;
    overflow: hidden;
    color: #697a8c;
    font-size: 13px;
    line-height: 1.5;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .platform-meta {
    color: #96a1ad;
    font-size: 12px;
  }

  a {
    display: inline-block;
    margin-top: 4px;
    color: #1677ff;
    font-size: 13px;
  }
}

.platform-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;

  strong {
    overflow: hidden;
    color: #25364d;
    line-height: 1.35;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.platform-actions {
  display: flex;
  justify-content: flex-end;

  :deep(.ant-space) {
    justify-content: flex-end;
  }
}

.avatar-form-item {
  :deep(.ant-upload-wrapper .ant-upload-list-picture-card) {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  :deep(.ant-upload-wrapper .ant-upload-select),
  :deep(.ant-upload-wrapper .ant-upload-list-item-container) {
    width: 96px;
    height: 96px;
    margin: 0;
  }

  :deep(.ant-upload-wrapper .ant-upload-list-item) {
    overflow: hidden;
    border-radius: 8px;
  }
}

.avatar-editor {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar-upload-empty {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #5f6f82;
  font-size: 13px;

  .anticon {
    color: #1677ff;
    font-size: 22px;
  }
}

.wechat-bind {
  text-align: center;

  p {
    margin: 4px 0 16px;
    color: #536273;
    line-height: 1.6;

    strong {
      color: #18a058;
      font-size: 18px;
    }
  }
}

.qr-box {
  position: relative;
  width: 210px;
  height: 210px;
  margin: 0 auto;
  padding: 10px;
  border-radius: 8px;
  background: #f0f5f0;

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: contain;
    transition: opacity .18s ease, filter .18s ease;

    &.muted {
      opacity: .3;
      filter: grayscale(.4);
    }
  }
}

.qr-overlay {
  position: absolute;
  inset: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px;
  border-radius: 6px;
  background: rgba(0, 0, 0, .68);
  color: #fff;

  a {
    color: #91caff;
  }
}

.wechat-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 34px;
  margin-top: 14px;
  padding: 7px 12px;
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

@media only screen and (max-width: 760px) {
  .profile-page {
    width: calc(100vw - 32px);
    margin-bottom: 36px;
  }

  .profile-header {
    align-items: flex-start;
    flex-wrap: wrap;
    padding-top: 10px;

    h2 {
      font-size: 24px;
    }
  }

  .profile-header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .profile-welcome {
    :deep(.ant-alert-content) {
      min-width: 0;
    }

    :deep(.ant-alert-action) {
      margin-inline-start: 0;
      padding-top: 10px;
    }
  }

  .profile-grid {
    grid-template-columns: 1fr;
  }

  .avatar-editor {
    align-items: flex-start;
  }

  .platform-row {
    grid-template-columns: 44px minmax(0, 1fr);
  }

  .platform-icon {
    width: 44px;
    height: 44px;
  }

  .platform-actions {
    grid-column: 1 / -1;
    justify-content: stretch;

    :deep(.ant-space) {
      display: grid;
      grid-template-columns: 1fr 1fr;
      width: 100%;
    }

    :deep(.ant-btn) {
      width: 100%;
    }
  }
}
</style>
