<script setup>
import {
  BookOutlined,
  CodeOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  GithubOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
  ShopOutlined,
  WechatOutlined,
  WhatsAppOutlined
} from '@ant-design/icons-vue';
import { computed, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import wechatMpQr from '@/assets/contact/wechat-mp.jpg';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';
import { SUPPORT_CONTACTS } from '@/utils/supportContacts.js';
import { lowcodeSiteUrl } from '@/runtime/application.js';

const year = new Date().getFullYear();
const { locale, t } = useI18n();
const {
  hasShop,
  loadPortalCapabilities
} = usePortalCapabilities();
const customerWechatQr = ref('');
const brandTitle = computed(() => t('storefront.title'));
const brandDesc = computed(() => t('brand.shopDesc'));

const productLinks = computed(() => [
  ...(hasShop.value ? [
    { label: t('nav.market'), href: '/', icon: ShopOutlined },
    { label: t('shop.home.allProducts'), href: '/shop/item-list', icon: CodeOutlined }
  ] : [])
]);

const resourceLinks = computed(() => [
  { label: t('footer.lowcodeWorkbench'), href: lowcodeSiteUrl, icon: DatabaseOutlined },
  { label: t('nav.blog'), href: 'https://blog.flyfish.dev', icon: BookOutlined },
  { label: t('nav.filePreview'), href: 'https://viewer.flyfish.dev', icon: FileTextOutlined },
  { label: t('nav.officeSuite'), href: 'https://product.flyfish.group', icon: FileTextOutlined },
  { label: t('nav.gitRepository'), href: 'https://git.flyfish.dev', icon: GithubOutlined }
]);

const serviceItems = computed(() => [
  ...(hasShop.value ? [{ label: t('storefront.source'), icon: CodeOutlined }, { label: t('storefront.license'), icon: SafetyCertificateOutlined }] : [])
]);

const legalLinks = computed(() => [
  { label: t('footer.privacyPolicy'), href: '/privacy' },
  { label: t('footer.termsOfService'), href: '/terms' }
]);

const useInternationalContacts = computed(() => !String(locale.value).toLowerCase().startsWith('zh'));
const contactItems = computed(() => {
  if (useInternationalContacts.value) {
    return [
      {
        key: 'whatsapp',
        label: t('footer.whatsapp'),
        detail: t('footer.openWhatsApp'),
        href: SUPPORT_CONTACTS.whatsapp.href,
        icon: WhatsAppOutlined,
        image: SUPPORT_CONTACTS.whatsapp.qrImage,
        imageAlt: t('footer.whatsappQrAlt')
      },
      {
        key: 'telegram',
        label: t('footer.telegram'),
        detail: SUPPORT_CONTACTS.telegram.handle,
        href: SUPPORT_CONTACTS.telegram.href,
        icon: SendOutlined,
        image: SUPPORT_CONTACTS.telegram.qrImage,
        imageAlt: t('footer.telegramQrAlt')
      }
    ];
  }
  return [
    ...(hasShop.value && customerWechatQr.value
      ? [{ key: 'wechat-support', label: t('footer.customerWechat'), image: customerWechatQr.value }]
      : []),
    { key: 'wechat-official', label: t('footer.wechatOfficial'), image: wechatMpQr }
  ];
});

const loadShopContactImage = async () => {
  if (!hasShop.value || customerWechatQr.value) {
    return;
  }
  const { getShopContactImages } = await import('@/modules/shop/assets/contact.js');
  customerWechatQr.value = getShopContactImages().customerWechatQr;
};

onMounted(() => {
  loadPortalCapabilities().then(loadShopContactImage).catch(() => {});
});

watch(hasShop, () => {
  loadShopContactImage().catch(() => {});
});
</script>

<template>
  <footer class='footer-box'>
    <div class='footer-inner'>
      <section class='footer-brand'>
        <div class='brand-line'>
          <img src='@/assets/logo.png' alt='Flyfish Logo'>
          <div>
            <strong>{{ brandTitle }}</strong>
            <p>{{ brandDesc }}</p>
          </div>
        </div>
      </section>

      <nav class='footer-nav' :aria-label='t("footer.serviceInfo")'>
        <section v-if='productLinks.length' class='footer-column'>
          <h3>{{ t('footer.productCapabilities') }}</h3>
          <router-link v-for='item in productLinks' :key='item.href' :href='item.href'>
            <component :is='item.icon' />
            <span>{{ item.label }}</span>
          </router-link>
        </section>

        <section v-if='serviceItems.length' class='footer-column'>
          <h3>{{ t('footer.resourceLinks') }}</h3>
          <a v-for='item in resourceLinks' :key='item.href' :href='item.href' target='_blank' rel='noopener noreferrer'>
            <component :is='item.icon' />
            <span>{{ item.label }}</span>
          </a>
        </section>

        <section class='footer-column'>
          <h3>{{ t('footer.serviceInfo') }}</h3>
          <div v-for='item in serviceItems' :key='item.label' class='footer-text'>
            <component :is='item.icon' />
            <span>{{ item.label }}</span>
          </div>
        </section>
      </nav>

      <section class='footer-contact'>
        <h3>{{ t('footer.contact') }}</h3>
        <div class='contact-grid'>
          <a
            v-for='item in contactItems'
            v-if='useInternationalContacts'
            :key='item.key'
            class='contact-item contact-link'
            :class='item.key'
            :href='item.href'
            target='_blank'
            rel='noopener noreferrer'
            :aria-label='`${item.label}: ${item.detail}`'
          >
            <img :src='item.image' :alt='item.imageAlt'>
            <span class='contact-provider'>
              <component :is='item.icon' />
              <strong>{{ item.label }}</strong>
            </span>
            <small>{{ item.detail }}</small>
          </a>
          <div v-for='item in contactItems' v-else :key='item.key' class='contact-item'>
            <img :src='item.image' :alt='item.label'>
            <span class='contact-provider wechat'>
              <wechat-outlined />
              <strong>{{ item.label }}</strong>
            </span>
          </div>
        </div>
      </section>
    </div>

    <div class='footer-bottom'>
      <span>{{ t('footer.copyright', { year }) }}</span>
      <nav class='footer-legal' :aria-label='t("footer.serviceInfo")'>
        <router-link v-for='item in legalLinks' :key='item.href' :to='item.href'>
          {{ item.label }}
        </router-link>
        <a
          v-if='!useInternationalContacts'
          href='https://beian.miit.gov.cn/'
          target='_blank'
          rel='noopener noreferrer'
        >
          {{ t('footer.icp') }}
        </a>
      </nav>
      <span class='footer-signature'>Flyfish Dev</span>
    </div>
  </footer>
</template>

<style scoped lang='less'>
.footer-box {
  width: 100%;
  border-top: 2px solid #d9ebe3;
  background: #f7f9fb;
  color: #536276;

  &.fixed {
    position: fixed;
    bottom: 0;
    left: 0;
  }
}

.footer-inner,
.footer-bottom {
  width: calc(100% - 48px);
  max-width: 1200px;
  margin: 0 auto;
}

.footer-inner {
  display: grid;
  grid-template-columns: minmax(240px, .95fr) minmax(430px, 1.65fr) minmax(250px, .9fr);
  gap: 48px;
  align-items: start;
  padding: 40px 0 30px;
}

.brand-line {
  display: flex;
  align-items: flex-start;
  gap: 14px;

  img {
    width: 58px;
    height: 58px;
    flex: none;
    border: 1px solid #e3ebe7;
    border-radius: 8px;
    background: #fff;
    object-fit: contain;
  }

  strong {
    display: block;
    margin: 2px 0 7px;
    color: #203329;
    font-size: 18px;
    line-height: 1.35;
  }

  p {
    max-width: 280px;
    margin: 0;
    color: #718095;
    font-size: 13px;
    line-height: 1.7;
  }
}

.footer-nav {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

.footer-column,
.footer-contact {
  min-width: 0;
  text-align: start;

  h3 {
    margin: 0 0 14px;
    color: #27394e;
    font-size: 13px;
    font-weight: 700;
    line-height: 1.4;
  }
}

.footer-column {
  a,
  .footer-text {
    display: flex;
    min-height: 30px;
    align-items: center;
    gap: 8px;
    color: #66768a;
    font-size: 12px;
    line-height: 1.5;
    text-decoration: none;

    :deep(.anticon) {
      flex: none;
      color: #43886b;
      font-size: 14px;
    }
  }

  a {
    transition: color .18s ease, transform .18s ease;

    &:hover {
      color: #176b4a;
      transform: translateX(2px);
    }
  }
}

.contact-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.contact-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px;
  border: 1px solid #e1e8ec;
  border-radius: 8px;
  background: #fff;
  color: #435267;
  text-decoration: none;

  > img {
    width: min(96px, 100%);
    aspect-ratio: 1;
    border-radius: 5px;
    background: #fff;
    object-fit: contain;
  }

  .contact-provider {
    display: inline-flex;
    max-width: 100%;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    line-height: 1.25;
    white-space: nowrap;

    :deep(.anticon) {
      color: #18874b;
    }
  }

  small {
    overflow: hidden;
    max-width: 100%;
    color: #7d8b9d;
    font-size: 10px;
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &.telegram .contact-provider :deep(.anticon) {
    color: #229ed9;
  }
}

.contact-link {
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;

  &:hover {
    border-color: #b8d5c8;
    box-shadow: 0 8px 20px rgb(36 84 62 / 8%);
    color: #2f4639;
    transform: translateY(-2px);
  }
}

.footer-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 24px;
  align-items: center;
  padding: 15px 0 18px;
  border-top: 1px solid #e1e7eb;
  color: #7c8999;
  font-size: 11px;
  line-height: 1.5;
}

.footer-legal {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px 18px;

  a {
    color: #6e7c8d;
    text-decoration: none;

    &:hover {
      color: #176b4a;
    }
  }
}

.footer-signature {
  color: #526478;
  font-weight: 600;
}

@media only screen and (max-width: 1040px) {
  .footer-inner {
    grid-template-columns: minmax(210px, .8fr) minmax(390px, 1.45fr);
    gap: 34px;
  }

  .footer-contact {
    grid-column: 1 / -1;
  }

  .contact-grid {
    max-width: 260px;
  }
}

@media only screen and (max-width: 760px) {
  .footer-inner {
    grid-template-columns: 1fr;
    gap: 30px;
    padding-top: 32px;
  }

  .footer-nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .footer-contact {
    grid-column: auto;
  }

  .footer-bottom {
    grid-template-columns: 1fr auto;
  }

  .footer-legal {
    grid-column: 1 / -1;
    grid-row: 1;
    justify-content: flex-start;
  }
}

@media only screen and (max-width: 560px) {
  .footer-inner,
  .footer-bottom {
    width: calc(100vw - 32px);
  }

  .brand-line p {
    max-width: none;
  }

  .footer-nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 24px 18px;
  }

  .footer-column:last-child {
    grid-column: 1 / -1;
  }

  .contact-grid {
    width: min(100%, 260px);
  }

  .footer-bottom {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    flex-direction: column;
  }

  .footer-legal {
    justify-content: flex-start;
  }
}
</style>
