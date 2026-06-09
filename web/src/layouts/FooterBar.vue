<script setup>
import {
  BookOutlined,
  CodeOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  GithubOutlined,
  SafetyCertificateOutlined,
  ShopOutlined,
  ToolOutlined,
  WechatOutlined
} from '@ant-design/icons-vue';
import { computed, onMounted, ref, watch } from 'vue';
import wechatMpQr from '@/assets/contact/wechat-mp.jpg';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';

const year = new Date().getFullYear();
const {
  hasLowcode,
  hasShop,
  portalTitle,
  lowcodeEntryPath,
  shopEntryName,
  shopEntryPath,
  loadPortalCapabilities
} = usePortalCapabilities();
const customerWechatQr = ref('');
const brandTitle = computed(() => {
  return portalTitle.value;
});
const brandDesc = computed(() => {
  if (hasShop.value && !hasLowcode.value) {
    return '面向开发者服务的商品与交付小铺。';
  }
  return '轻量级、高性能、便捷的开源低代码集成平台。';
});

const productLinks = computed(() => [
  ...(hasLowcode.value ? [
    { label: '数据建模', href: lowcodeEntryPath.value, icon: DatabaseOutlined },
    { label: '代码生成', href: '/code-generate', icon: CodeOutlined }
  ] : []),
  ...(hasShop.value ? [
    { label: shopEntryName.value, href: shopEntryPath.value, icon: ShopOutlined }
  ] : [])
]);

const resourceLinks = [
  { label: '博客', href: 'https://blog.flyfish.dev', icon: BookOutlined },
  { label: '文件预览', href: 'https://viewer.flyfish.dev', icon: FileTextOutlined },
  { label: 'Office 预览套件', href: 'https://product.flyfish.group', icon: FileTextOutlined },
  { label: '代码仓库', href: 'https://git.flyfish.dev', icon: GithubOutlined }
];

const serviceItems = computed(() => [
  ...(hasLowcode.value ? [{ label: '轻量级低代码开发工作台', icon: ToolOutlined }] : []),
  ...(hasShop.value ? [{ label: 'Gitea 授权与仓库权限开通', icon: SafetyCertificateOutlined }] : [])
]);

const contactItems = computed(() => [
  ...(hasShop.value && customerWechatQr.value ? [{ label: '客服微信', image: customerWechatQr.value }] : []),
  { label: '公众号', image: wechatMpQr }
]);

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
  <div class='footer-box'>
    <div class='footer-inner'>
      <section class='footer-brand'>
        <div class='brand-line'>
          <img src='@/assets/logo.svg' alt='Flyfish Logo'>
          <div>
            <strong>{{ brandTitle }}</strong>
            <p>{{ brandDesc }}</p>
          </div>
        </div>
      </section>

      <section class='footer-column'>
        <h3>产品能力</h3>
        <router-link v-for='item in productLinks' :key='item.href' :href='item.href'>
          <component :is='item.icon' />
          <span>{{ item.label }}</span>
        </router-link>
      </section>

      <section class='footer-column'>
        <h3>资源入口</h3>
        <a v-for='item in resourceLinks' :key='item.href' :href='item.href' target='_blank' rel='noreferrer'>
          <component :is='item.icon' />
          <span>{{ item.label }}</span>
        </a>
      </section>

      <section class='footer-column'>
        <h3>服务信息</h3>
        <div v-for='item in serviceItems' :key='item.label' class='footer-text'>
          <component :is='item.icon' />
          <span>{{ item.label }}</span>
        </div>
        <a href='https://beian.miit.gov.cn/' target='_blank' rel='noreferrer'>
          晋ICP备2024030443号
        </a>
      </section>

      <section class='footer-column footer-contact'>
        <h3>联系方式</h3>
        <div class='contact-grid'>
          <div v-for='item in contactItems' :key='item.label' class='contact-card'>
            <img :src='item.image' :alt='item.label'>
            <span>
              <wechat-outlined />
              {{ item.label }}
            </span>
          </div>
        </div>
      </section>
    </div>

    <div class='footer-bottom'>
      <span>飞鱼开源 Copyright © 2015 - {{ year }}</span>
      <span>Flyfish Dev</span>
    </div>
  </div>
</template>

<style scoped lang='less'>
.footer-box {
  width: 100%;
  background: #fff;
  border-top: 1px solid #e8eef5;
  color: #4b5b73;

  &.fixed {
    position: fixed;
    bottom: 0;
    left: 0;
    border-top: 1px solid #f0f0f0;
  }
}

.footer-inner {
  display: grid;
  grid-template-columns: minmax(240px, 1.25fr) repeat(3, minmax(140px, 1fr)) minmax(210px, .9fr);
  gap: 28px;
  width: min(1100px, calc(100vw - 48px));
  margin: 0 auto;
  padding: 34px 0 24px;
}

.brand-line {
  display: flex;
  gap: 14px;
  align-items: center;

  img {
    width: 54px;
    height: 54px;
    flex: none;
    object-fit: contain;
    border-radius: 16px;
    background: #f7fbff;
  }

  strong {
    display: block;
    margin-bottom: 6px;
    color: #25364d;
    font-size: 18px;
    line-height: 1.3;
  }

  p {
    margin: 0;
    color: #6b7c93;
    line-height: 1.8;
  }
}

.footer-column {
  min-width: 0;

  h3 {
    margin: 0 0 12px;
    color: #25364d;
    font-size: 15px;
    line-height: 1.4;
  }

  a,
  .footer-text {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 28px;
    color: #5d6f86;
    font-size: 13px;
    line-height: 1.6;
    text-decoration: none;

    :deep(.anticon) {
      color: #1677ff;
      font-size: 15px;
    }
  }

  a:hover {
    color: #1677ff;
  }
}

.footer-bottom {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  width: min(1100px, calc(100vw - 48px));
  margin: 0 auto;
  padding: 14px 0 18px;
  border-top: 1px solid #edf2f7;
  color: #7b8a9b;
  font-size: 12px;
  line-height: 1.6;
}

.footer-contact {
  .contact-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .contact-card {
    display: flex;
    min-width: 0;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 8px;
    border: 1px solid #edf2f7;
    border-radius: 8px;
    background: #fbfdff;

    img {
      width: 78px;
      height: 78px;
      object-fit: cover;
      border-radius: 6px;
      background: #fff;
    }

    span {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      color: #4b5b73;
      font-size: 12px;
      line-height: 1.2;
      white-space: nowrap;
    }

    :deep(.anticon) {
      color: #21a366;
    }
  }
}

@media only screen and (max-width: 820px) {
  .footer-inner {
    grid-template-columns: 1fr 1fr;
    gap: 24px 20px;
  }

  .footer-brand {
    grid-column: 1 / -1;
  }
}

@media only screen and (max-width: 560px) {
  .footer-inner,
  .footer-bottom {
    width: calc(100vw - 32px);
  }

  .footer-inner {
    grid-template-columns: 1fr;
    padding-top: 28px;
  }

  .footer-contact .contact-grid {
    grid-template-columns: repeat(2, minmax(0, 120px));
  }

  .footer-bottom {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
