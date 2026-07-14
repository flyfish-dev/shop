<script setup>
import { computed, onMounted, watch } from 'vue';
import { ArrowLeftOutlined } from '@ant-design/icons-vue';
import { storeToRefs } from 'pinia';
import { useI18n } from 'vue-i18n';
import LoginForm from './components/LoginForm';
import LanguageSwitch from '@/components/LanguageSwitch/index.vue';
import useClientStore from '@/modules/auth/store/client.js';
import { useRoute, useRouter } from '@/router/use.js';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';
import latestBrandLogo from '@/assets/flyfish-dev-logo-transparent.png';

const { t, locale } = useI18n();
const store = useClientStore();
const route = useRoute();
const router = useRouter();
const { initialized, isAuthenticated } = storeToRefs(store);
const { portalTitle, loadPortalCapabilities } = usePortalCapabilities();
const showLogin = computed(() => initialized.value && !isAuthenticated.value);
const oauthFailed = computed(() => route.query?.oauth === 'failed');
const localizedPortalTitle = computed(() => locale.value === 'en-US'
  ? t('brand.lowcodeTitle')
  : (portalTitle.value || t('brand.lowcodeTitle')));
const brandPurpose = computed(() => t('home.appPurpose'));

const redirectIfAuthenticated = () => {
  if (isAuthenticated.value) {
    store.redirectAfterAuthentication(router, '/');
  }
};

onMounted(async () => {
  loadPortalCapabilities().catch(() => {});
  await store.loadUser();
  redirectIfAuthenticated();
});

watch(isAuthenticated, redirectIfAuthenticated);
</script>

<template>
  <main class="login-page">
    <div class="ambient-background" aria-hidden="true">
      <span class="accent-line accent-green" />
      <span class="accent-line accent-blue" />
      <span class="accent-line accent-coral" />
      <span class="motion-track track-one" />
      <span class="motion-track track-two" />
    </div>

    <div class="page-tools">
      <language-switch />
    </div>

    <a class="back-link" href="/">
      <arrow-left-outlined />
      <span>{{ t('auth.backHome') }}</span>
    </a>

    <div class="login-layout">
      <section class="brand-stage" aria-labelledby="login-brand-title">
        <div class="brand-visual">
          <div class="logo-stage">
            <span class="logo-outline" aria-hidden="true" />
            <img :src="latestBrandLogo" :alt="localizedPortalTitle" />
          </div>
          <div class="brand-copy">
            <span class="brand-kicker">FLYFISH DEV</span>
            <h1 id="login-brand-title">{{ localizedPortalTitle }}</h1>
            <p>{{ brandPurpose }}</p>
          </div>
        </div>

      </section>

      <section class="login-surface">
        <header class="login-header">
          <span class="surface-mark" aria-hidden="true" />
          <div>
            <h2>{{ t('auth.title') }}</h2>
            <p>{{ t('auth.subtitle') }}</p>
          </div>
        </header>
        <a-alert
          v-if="oauthFailed"
          class="oauth-alert"
          type="error"
          show-icon
          :message="t('auth.oauthFailed')"
        />
        <login-form v-if="showLogin" />
        <div v-else class="loading-state"><a-spin /></div>
      </section>
    </div>
  </main>
</template>

<style scoped lang="less">
.login-page {
  position: relative;
  width: 100%;
  min-height: 100vh;
  overflow: hidden auto;
  padding: 72px 48px 48px;
  background: #eef5f2;
  color: #172338;
}

.ambient-background {
  position: fixed;
  z-index: 0;
  inset: 0;
  overflow: hidden;
  background-image: url("@/assets/background.svg");
  background-repeat: no-repeat;
  background-position: center 76px;
  background-size: max(1180px, 100%) auto;
  opacity: .72;
  pointer-events: none;
  animation: background-drift 18s ease-in-out infinite alternate;
}

.accent-line {
  position: absolute;
  top: 0;
  height: 4px;
}

.accent-green { left: 0; width: 38%; background: #188758; }
.accent-blue { left: 38%; width: 34%; background: #2c74d8; }
.accent-coral { right: 0; width: 28%; background: #e97864; }

.motion-track {
  position: absolute;
  width: 88px;
  height: 2px;
  background: #59a984;
  opacity: .32;
  animation: signal-pass 7s ease-in-out infinite;
}

.track-one { top: 18%; left: 8%; }
.track-two { right: 10%; bottom: 14%; width: 54px; background: #4d82c1; animation-delay: -3.5s; }

.page-tools {
  position: fixed;
  z-index: 4;
  top: 20px;
  right: 28px;
}

.login-layout {
  position: relative;
  z-index: 1;
  display: grid;
  width: min(100%, 1180px);
  min-height: calc(100vh - 120px);
  grid-template-columns: minmax(0, 1fr) minmax(460px, 520px);
  gap: 76px;
  align-items: center;
  margin: 0 auto;
}

.brand-stage {
  display: flex;
  min-width: 0;
  align-self: stretch;
  flex-direction: column;
  justify-content: center;
  padding: 30px 0;
  animation: stage-enter .7s cubic-bezier(.2, .75, .25, 1) both;
}

.brand-visual {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.logo-stage {
  position: relative;
  width: 246px;
  height: 246px;
  animation: logo-float 5.5s ease-in-out infinite;

  img {
    position: relative;
    z-index: 1;
    display: block;
    width: 100%;
    height: 100%;
    object-fit: contain;
    filter: drop-shadow(0 22px 22px rgba(26, 80, 59, .14));
  }
}

.logo-outline {
  position: absolute;
  inset: 33px 18px 24px 26px;
  border: 1px solid rgba(36, 119, 81, .20);
  border-radius: 50%;
  animation: outline-pulse 4.5s ease-in-out infinite;
}

.brand-copy {
  width: 100%;
  max-width: 560px;
  margin-top: -13px;
  text-align: center;
}

.brand-kicker {
  display: block;
  margin-bottom: 10px;
  color: #267451;
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 2px;
}

.brand-copy h1 {
  margin: 0;
  color: #153426;
  font-weight: 750;
  font-size: 38px;
  line-height: 1.22;
  letter-spacing: 0;
}

.brand-copy p {
  max-width: 520px;
  margin: 16px 0 0;
  color: #5e7268;
  font-size: 15px;
  line-height: 1.8;
}

.login-surface {
  width: 100%;
  padding: 30px 32px 25px;
  border: 1px solid rgba(188, 207, 198, .86);
  border-radius: 8px;
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 24px 64px rgba(24, 58, 42, .13);
  backdrop-filter: blur(16px);
  animation: surface-enter .65s .08s cubic-bezier(.2, .75, .25, 1) both;
}

.login-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 24px;
  text-align: left;

  h2 {
    margin: 0 0 8px;
    color: #172338;
    font-weight: 700;
    font-size: 25px;
    line-height: 1.25;
    letter-spacing: 0;
  }

  p {
    margin: 0;
    color: #68768a;
    font-size: 14px;
    line-height: 1.55;
  }
}

.surface-mark {
  width: 4px;
  height: 34px;
  flex: 0 0 auto;
  margin-top: 2px;
  border-radius: 2px;
  background: #1b8b59;
}

.oauth-alert {
  margin: -8px 0 18px;
  border-radius: 6px;
  font-size: 12px;
}

.loading-state {
  display: flex;
  min-height: 280px;
  align-items: center;
  justify-content: center;
}

.back-link {
  position: fixed;
  z-index: 4;
  top: 28px;
  left: 32px;
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 6px;
  color: #607269;
  font-size: 13px;
  text-decoration: none;

  &:hover { color: #146f49; }
}

@keyframes background-drift {
  from { background-position: center 76px; }
  to { background-position: calc(50% + 18px) 58px; }
}

@keyframes logo-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-9px); }
}

@keyframes outline-pulse {
  0%, 100% { opacity: .35; transform: scale(.96); }
  50% { opacity: .8; transform: scale(1.05); }
}

@keyframes signal-pass {
  0%, 100% { opacity: .12; transform: translateX(-14px) scaleX(.65); }
  50% { opacity: .42; transform: translateX(26px) scaleX(1); }
}

@keyframes stage-enter {
  from { opacity: 0; transform: translateX(-20px); }
  to { opacity: 1; transform: translateX(0); }
}

@keyframes surface-enter {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 980px) {
  .login-layout { grid-template-columns: minmax(250px, .8fr) minmax(430px, 500px); gap: 34px; }
  .logo-stage { width: 190px; height: 190px; }
  .brand-copy h1 { font-size: 31px; }
  .brand-copy p { font-size: 14px; }
}

@media (min-width: 761px) and (max-height: 820px) {
  .login-page { padding-top: 52px; padding-bottom: 24px; }
  .login-layout { min-height: calc(100vh - 76px); }
  .brand-stage { padding: 12px 0; }
  .logo-stage { width: 205px; height: 205px; }
  .brand-copy { margin-top: -10px; }
  .brand-copy p { margin-top: 10px; line-height: 1.65; }
  .login-surface { padding: 23px 28px 19px; }
  .login-header { margin-bottom: 17px; }
  .login-header h2 { margin-bottom: 6px; }
}

@media (max-width: 760px) {
  .login-page {
    padding: 68px 16px 30px;
  }

  .page-tools { top: 16px; right: 16px; }
  .login-layout {
    min-height: auto;
    grid-template-columns: 1fr;
    gap: 22px;
  }

  .brand-stage { padding: 2px 4px 0; }
  .brand-visual { flex-direction: row; align-items: center; gap: 15px; }
  .logo-stage { width: 92px; height: 92px; flex: 0 0 auto; margin: 0; }
  .logo-outline { inset: 13px 7px 10px 10px; }
  .brand-copy { margin: 0; text-align: left; }
  .brand-kicker { margin-bottom: 4px; font-size: 10px; letter-spacing: 1.5px; }
  .brand-copy h1 { font-size: 24px; }
  .brand-copy p { margin-top: 7px; font-size: 12px; line-height: 1.55; }
  .back-link { top: 23px; left: 16px; }
  .login-surface { padding: 25px 22px 22px; }
  .login-header h2 { font-size: 22px; }
}

@media (max-width: 440px) {
  .login-page { padding-inline: 12px; }
  .brand-stage { padding-top: 12px; }
  .brand-visual { gap: 10px; }
  .logo-stage { width: 76px; height: 76px; }
  .brand-copy h1 { font-size: 20px; }
  .brand-copy p { display: none; }
  .login-surface { padding: 22px 16px 19px; }
  .login-header { gap: 10px; margin-bottom: 20px; }
  .login-header h2 { font-size: 21px; }
}

@media (prefers-reduced-motion: reduce) {
  .ambient-background,
  .motion-track,
  .logo-stage,
  .logo-outline,
  .brand-stage,
  .login-surface { animation: none; }
}
</style>
