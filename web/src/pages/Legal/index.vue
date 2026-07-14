<script setup>
import {
  ArrowLeftOutlined,
  CustomerServiceOutlined,
  SafetyCertificateOutlined
} from '@ant-design/icons-vue';
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import LanguageSwitch from '@/components/LanguageSwitch/index.vue';
import { useRoute } from '@/router/use.js';
import logo from '@/assets/logo.png';
import { getLegalDocument } from './content.js';

const route = useRoute();
const { locale } = useI18n();
const documentType = computed(() => route.path === '/terms' ? 'terms' : 'privacy');
const document = computed(() => getLegalDocument(locale.value, documentType.value));
</script>

<template>
  <div class="legal-page">
    <header class="legal-header">
      <div class="header-inner">
        <router-link class="brand" href="/">
          <img :src="logo" alt="Flyfish Dev">
          <span>
            <strong>{{ document.brand }}</strong>
            <small>{{ document.brandSubtitle }}</small>
          </span>
        </router-link>
        <div class="header-actions">
          <router-link class="home-link" href="/">
            <arrow-left-outlined />
            <span>{{ document.backHome }}</span>
          </router-link>
          <language-switch />
        </div>
      </div>
    </header>

    <main>
      <section class="legal-intro">
        <div class="legal-container">
          <div class="eyebrow">
            <safety-certificate-outlined />
            {{ document.eyebrow }}
          </div>
          <h1>{{ document.title }}</h1>
          <p>{{ document.intro }}</p>
          <div class="updated-at">
            {{ document.lastUpdated }} · {{ document.updatedAt }}
          </div>
          <nav class="document-nav" :aria-label="document.title">
            <router-link href="/privacy" :class="{ active: documentType === 'privacy' }">
              {{ document.privacyLink }}
            </router-link>
            <router-link href="/terms" :class="{ active: documentType === 'terms' }">
              {{ document.termsLink }}
            </router-link>
          </nav>
        </div>
      </section>

      <div class="legal-container legal-body">
        <article>
          <section v-for="(section, index) in document.sections" :key="section.title">
            <h2><span>{{ index + 1 }}</span>{{ section.title }}</h2>
            <p v-for="paragraph in section.paragraphs || []" :key="paragraph">{{ paragraph }}</p>
            <ul v-if="section.bullets">
              <li v-for="item in section.bullets" :key="item">{{ item }}</li>
            </ul>
          </section>
        </article>

        <aside class="contact-band">
          <customer-service-outlined />
          <div>
            <h2>{{ document.contactTitle }}</h2>
            <p>{{ document.contactBody }}</p>
          </div>
          <router-link href="/login">{{ document.contactAction }}</router-link>
        </aside>
      </div>
    </main>

    <footer>
      <div class="legal-container footer-inner">
        <span>© 2015 - 2026 {{ document.copyright }}</span>
        <div>
          <router-link href="/privacy">{{ document.privacyLink }}</router-link>
          <router-link href="/terms">{{ document.termsLink }}</router-link>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped lang="less">
.legal-page {
  min-height: 100vh;
  background: #fff;
  color: #26384d;
  text-align: left;
}

.legal-container,
.header-inner {
  width: min(920px, calc(100% - 40px));
  margin: 0 auto;
}

.legal-header {
  border-bottom: 1px solid #e6edf4;
  background: rgba(255, 255, 255, .96);
}

.header-inner {
  display: flex;
  min-height: 72px;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.brand {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
  color: #173a33;
  text-decoration: none;

  img {
    width: 42px;
    height: 42px;
    object-fit: contain;
  }

  span,
  strong,
  small {
    display: block;
  }

  strong {
    font-size: 17px;
    line-height: 1.25;
  }

  small {
    margin-top: 2px;
    color: #718096;
    font-size: 11px;
    line-height: 1.2;
  }
}

.header-actions,
.home-link {
  display: inline-flex;
  align-items: center;
}

.header-actions {
  gap: 14px;
}

.home-link {
  gap: 6px;
  color: #4f6479;
  font-size: 13px;
  text-decoration: none;

  &:hover {
    color: #1677ff;
  }
}

.legal-intro {
  border-bottom: 1px solid #e6edf4;
  background: linear-gradient(135deg, #f4fbf8 0%, #f7faff 58%, #fff8f4 100%);

  .legal-container {
    position: relative;
    padding: 58px 0 0;
  }

  h1 {
    margin: 14px 0 16px;
    color: #173a33;
    font-size: clamp(34px, 5vw, 50px);
    font-weight: 700;
    letter-spacing: 0;
    line-height: 1.15;
  }

  p {
    max-width: 760px;
    margin: 0;
    color: #52677d;
    font-size: 16px;
    line-height: 1.85;
  }
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #15815f;
  font-size: 13px;
  font-weight: 700;
}

.updated-at {
  margin-top: 18px;
  color: #7a899a;
  font-size: 12px;
}

.document-nav {
  display: flex;
  gap: 26px;
  margin-top: 34px;

  a {
    position: relative;
    padding: 0 1px 13px;
    color: #5b6f83;
    font-size: 14px;
    font-weight: 650;
    text-decoration: none;

    &::after {
      position: absolute;
      right: 0;
      bottom: -1px;
      left: 0;
      height: 2px;
      background: transparent;
      content: '';
    }

    &.active {
      color: #1677ff;

      &::after {
        background: #1677ff;
      }
    }
  }
}

.legal-body {
  padding: 54px 0 64px;

  article {
    max-width: 820px;
  }

  section {
    margin-bottom: 42px;
  }

  h2 {
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 0 0 15px;
    color: #20374c;
    font-size: 20px;
    letter-spacing: 0;
    line-height: 1.45;

    span {
      display: inline-flex;
      width: 28px;
      height: 28px;
      flex: none;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      background: #eaf7f1;
      color: #16805e;
      font-size: 12px;
    }
  }

  p,
  li {
    color: #516579;
    font-size: 15px;
    line-height: 1.9;
  }

  p {
    margin: 0 0 12px;
  }

  ul {
    margin: 0;
    padding-left: 24px;
  }

  li {
    margin-bottom: 8px;
    padding-left: 4px;

    &::marker {
      color: #16a078;
    }
  }
}

.contact-band {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  margin-top: 8px;
  padding: 22px 24px;
  border: 1px solid #cfe7dc;
  border-radius: 8px;
  background: #f5fbf8;

  > :deep(.anticon) {
    color: #16805e;
    font-size: 24px;
  }

  h2 {
    margin: 0 0 4px;
    font-size: 16px;
  }

  p {
    margin: 0;
    font-size: 13px;
    line-height: 1.65;
  }

  a {
    padding: 8px 14px;
    border: 1px solid #a9d8c5;
    border-radius: 6px;
    color: #126b50;
    font-size: 13px;
    font-weight: 650;
    text-decoration: none;

    &:hover {
      border-color: #16805e;
      background: #fff;
    }
  }
}

footer {
  border-top: 1px solid #e6edf4;
  background: #f8fafc;
  color: #748397;
  font-size: 12px;
}

.footer-inner {
  display: flex;
  min-height: 68px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;

  div {
    display: flex;
    gap: 18px;
  }

  a {
    color: #5c7186;
    text-decoration: none;
  }
}

@media only screen and (max-width: 640px) {
  .legal-container,
  .header-inner {
    width: calc(100% - 28px);
  }

  .header-inner {
    min-height: 64px;
  }

  .brand {
    gap: 8px;

    img {
      width: 36px;
      height: 36px;
    }

    small {
      display: none;
    }
  }

  .header-actions {
    gap: 9px;
  }

  .home-link span {
    display: none;
  }

  .home-link {
    width: 28px;
    height: 28px;
    justify-content: center;
    border: 1px solid #dfe7ef;
    border-radius: 50%;
    background: #fff;
  }

  .legal-intro {
    .legal-container {
      padding-top: 38px;
    }

    h1 {
      font-size: 34px;
    }

    p {
      font-size: 14px;
    }
  }

  .document-nav {
    margin-top: 26px;
  }

  .legal-body {
    padding: 38px 0 48px;

    section {
      margin-bottom: 34px;
    }

    h2 {
      font-size: 18px;
    }

    p,
    li {
      font-size: 14px;
      line-height: 1.85;
    }
  }

  .contact-band {
    grid-template-columns: auto minmax(0, 1fr);
    align-items: start;
    padding: 18px;

    a {
      grid-column: 1 / -1;
      justify-self: stretch;
      text-align: center;
    }
  }

  .footer-inner {
    min-height: 82px;
    flex-direction: column;
    align-items: flex-start;
    justify-content: center;
    gap: 7px;
  }
}
</style>
