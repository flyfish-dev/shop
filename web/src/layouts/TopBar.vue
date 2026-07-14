<script setup>
import UserInfo from '@/components/UserInfo';
import LanguageSwitch from '@/components/LanguageSwitch/index.vue';
import TopBarGuestActions from './components/TopBarGuestActions.vue';
import TopBarNav from './components/TopBarNav.vue';
import { useTopBarState } from './hooks/useTopBarState.js';

const {
  brandDescription,
  brandTitle,
  collapsed,
  compactLogin,
  compactUser,
  gutter,
  isHome,
  login,
  navInteractive,
  navStyle,
  showStart,
  start,
  topbarStyle,
  user
} = useTopBarState();
</script>

<template>
  <div class='topbar-space' :class='{ compact: !isHome }' :style='topbarStyle'>
    <div class='banner' :class='{ collapsed }'>
      <img alt='Flyfish Logo' class='banner-logo' src='@/assets/logo.png'>
      <router-link href='/' class='brand-link'><h1 class='title'>{{ brandTitle }}</h1></router-link>
      <p class='brand-purpose'>{{ brandDescription }}</p>
      <top-bar-nav :interactive='navInteractive' :style='navStyle' />
      <div class='banner-tool'>
        <user-info v-if='user?.id' :compact='compactUser' />
        <top-bar-guest-actions
          v-else
          :compact-login='compactLogin'
          :gutter='gutter'
          :show-start='showStart'
          @login='login'
          @start='start'
        />
      </div>
    </div>
    <language-switch class='topbar-language' />
  </div>
</template>

<style scoped lang='less'>
.topbar-space {
  --expanded-height: 500px;
  --collapsed-height: 72px;
  height: var(--expanded-height);
  position: relative;
  z-index: 20;

  &.compact {
    height: var(--topbar-height);
  }
}

.banner {
  width: 100%;
  height: var(--topbar-height);
  box-sizing: border-box;
  background: white;
  overflow: hidden;
  position: fixed;
  top: 0;
  left: 0;
  z-index: 20;
  box-shadow:
    0 0 10px rgba(34, 153, 221, var(--topbar-shadow-alpha)),
    0 0 5px rgba(34, 153, 221, var(--topbar-shadow-alpha));
  transition: box-shadow .18s linear;

  .brand-link {
    position: absolute;
    top: var(--title-top);
    left: var(--title-left);
    display: block;
    max-width: var(--title-max-width);
    color: inherit;
    text-decoration: none;
    transform: translateX(var(--title-translate));
    will-change: top, left, transform, max-width;
  }

  .title {
    margin: 0;
    overflow: hidden;
    font-size: var(--title-font-size);
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
    letter-spacing: 0;
  }

  .brand-purpose {
    position: absolute;
    top: var(--purpose-top);
    left: var(--purpose-left);
    width: var(--purpose-width);
    margin: 0;
    color: #5d6b66;
    font-size: 15px;
    line-height: 1.6;
    text-align: center;
    letter-spacing: 0;
    opacity: var(--purpose-opacity);
    transform: translateX(var(--purpose-translate));
    pointer-events: none;
    will-change: top, left, width, opacity, transform;
  }

  .banner-logo {
    position: absolute;
    display: block;
    top: var(--logo-top);
    left: var(--logo-left);
    width: var(--logo-size);
    height: var(--logo-size);
    object-fit: contain;
    border-radius: var(--logo-radius);
    will-change: width, height, top, left, border-radius;
  }

  .banner-tool {
    position: absolute;
    top: var(--tool-top);
    left: var(--tool-left);
    display: flex;
    align-items: center;
    height: 34px;
    font-size: 14px;
    transform: translateX(var(--tool-translate));
    will-change: top, left, transform;
  }
}

.topbar-language {
  position: fixed;
  top: 18px;
  right: 16px;
  z-index: 32;
  opacity: .86;
}

@media only screen and (max-width: 640px) {
  .topbar-space {
    --expanded-height: 500px;
    --collapsed-height: 112px;
  }

  .banner {
    .banner-tool {
      font-size: 13px;
    }
  }

  .topbar-language {
    top: 12px;
    right: 8px;
    opacity: .82;
  }

  .banner .brand-purpose {
    font-size: 14px;
    line-height: 1.55;
  }
}
</style>
