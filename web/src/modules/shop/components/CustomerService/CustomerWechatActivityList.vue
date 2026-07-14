<script setup>
import { computed } from 'vue';
import {
  EnvironmentOutlined,
  MessageOutlined,
  PictureOutlined,
  QrcodeOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
  UserAddOutlined,
  UserDeleteOutlined,
  WechatOutlined
} from '@ant-design/icons-vue';
import { customerDisplayName } from './customerDisplay.js';
import { useI18n } from 'vue-i18n';

const props = defineProps({
  activities: {
    type: Array,
    default: () => []
  },
  loading: Boolean,
  keyword: String,
  activityType: String,
  hasFilter: Boolean
});

const emit = defineEmits([
  'refresh',
  'reset',
  'update:keyword',
  'update:activityType'
]);
const { locale, t, te } = useI18n();

const updateActivityType = value => {
  emit('update:activityType', value);
  emit('refresh');
};

const activityTypes = ['ALL', 'SUBSCRIBE', 'UNSUBSCRIBE', 'SCAN', 'TEXT', 'IMAGE', 'LOCATION', 'EVENT'];
const typeOptions = computed(() => activityTypes.map(value => ({
  value,
  label: t(`customerService.activity.types.${value}`)
})));

const typeMeta = {
  SUBSCRIBE: { color: 'green', icon: UserAddOutlined, tone: 'green' },
  UNSUBSCRIBE: { color: 'red', icon: UserDeleteOutlined, tone: 'red' },
  SCAN: { color: 'blue', icon: QrcodeOutlined, tone: 'blue' },
  TEXT: { color: 'geekblue', icon: MessageOutlined, tone: 'blue' },
  IMAGE: { color: 'purple', icon: PictureOutlined, tone: 'purple' },
  LOCATION: { color: 'cyan', icon: EnvironmentOutlined, tone: 'cyan' },
  EVENT: { color: 'orange', icon: ThunderboltOutlined, tone: 'orange' },
  MESSAGE: { color: 'default', icon: MessageOutlined, tone: 'gray' }
};

const metaOf = item => {
  const type = typeMeta[item?.activityType] ? item.activityType : 'MESSAGE';
  return {
    ...typeMeta[type],
    text: t(`customerService.activity.types.${type}`),
    title: t(`customerService.activity.titles.${type}`)
  };
};

const openidSuffix = value => {
  const text = String(value || '');
  return text.length > 10 ? text.slice(-8) : text;
};

const displayName = item => customerDisplayName({
  displayName: item?.displayName,
  avatar: item?.avatar,
  wechatOpenid: item?.wechatOpenid,
  userId: item?.userId
}, locale.value);

const readableMessageType = value => {
  const text = String(value || '').trim();
  const normalized = text.toLowerCase();
  const key = `customerService.activity.messageTypes.${normalized}`;
  return te(key) ? t(key) : text;
};

const readableEventType = value => {
  const text = String(value || '').trim();
  const normalized = text.toLowerCase();
  const key = `customerService.activity.events.${normalized}`;
  return te(key) ? t(key) : text;
};

const sceneText = value => {
  const text = String(value || '').trim();
  if (!text) {
    return '';
  }
  const normalized = text.startsWith('qrscene_') ? text.slice('qrscene_'.length) : text;
  if (normalized.startsWith('login_')) {
    return t('customerService.activity.loginScene', { id: normalized.slice('login_'.length) });
  }
  if (normalized.startsWith('buy_') || normalized.startsWith('purchase_')) {
    return t('customerService.activity.purchaseScene', { id: normalized.replace(/^(buy|purchase)_/, '') });
  }
  if (normalized.startsWith('bind_')) {
    return t('customerService.activity.bindScene', { id: normalized.slice('bind_'.length) });
  }
  return normalized;
};

const userTraceText = item => {
  if (item?.userId) {
    return t('customerService.activity.linkedUser');
  }
  const suffix = openidSuffix(item?.wechatOpenid);
  return suffix
    ? t('customerService.activity.visitorWithId', { id: suffix })
    : t('customerService.activity.visitor');
};

const activityTitle = item => {
  const meta = metaOf(item);
  if (item?.activityType === 'SCAN' && item?.eventKey) {
    return `${meta.title} · ${sceneText(item.eventKey)}`;
  }
  return meta.title || item?.title || t('customerService.activity.defaultTitle');
};

const activitySummary = item => {
  const content = String(item?.content || '').trim();
  if (item?.activityType === 'SUBSCRIBE') {
    return sceneText(item.eventKey)
      ? t('customerService.activity.subscribedVia', { scene: sceneText(item.eventKey) })
      : t('customerService.activity.subscribed');
  }
  if (item?.activityType === 'UNSUBSCRIBE') {
    return t('customerService.activity.unsubscribed');
  }
  if (item?.activityType === 'SCAN') {
    return sceneText(item.eventKey)
      ? t('customerService.activity.scannedScene', { scene: sceneText(item.eventKey) })
      : t('customerService.activity.scanned');
  }
  if (item?.activityType === 'IMAGE') {
    return content && /^https?:\/\//i.test(content)
      ? t('customerService.activity.sentImage')
      : (content || t('customerService.activity.sentImage'));
  }
  if (item?.activityType === 'LOCATION') {
    return content || t('customerService.activity.sentLocation');
  }
  if (item?.activityType === 'EVENT') {
    return item?.eventType
      ? t('customerService.activity.triggeredNamedEvent', { event: readableEventType(item.eventType) })
      : t('customerService.activity.triggeredEvent');
  }
  return content || item?.title || t('customerService.activity.sentMessage');
};

const detailTags = item => [
  ['SCAN', 'SUBSCRIBE'].includes(item?.activityType) && item?.eventKey
    ? { text: sceneText(item.eventKey), color: 'cyan' }
    : null,
  item?.activityType === 'EVENT' && item?.eventType
    ? { text: readableEventType(item.eventType), color: 'orange' }
    : null,
  item?.activityType === 'MESSAGE' && item?.messageType
    ? { text: readableMessageType(item.messageType), color: 'blue' }
    : null
].filter(Boolean);

const isImageUrl = item => item?.activityType === 'IMAGE' && /^https?:\/\//i.test(String(item?.content || '').trim());

const shouldShowContent = item => {
  if (!item) {
    return false;
  }
  if (item.activityType === 'SUBSCRIBE') {
    return Boolean(item.eventKey);
  }
  return ['UNSUBSCRIBE', 'SCAN', 'TEXT', 'IMAGE', 'LOCATION', 'EVENT', 'MESSAGE'].includes(item.activityType);
};

const activityTimestamp = item => {
  const value = String(item?.createTime || '').trim();
  if (!value) {
    return 0;
  }
  const timestamp = Date.parse(value.replace(' ', 'T'));
  return Number.isNaN(timestamp) ? 0 : timestamp;
};

const sortedActivities = computed(() => [...props.activities].sort((left, right) => (
  activityTimestamp(right) - activityTimestamp(left)
  || Number(right?.id || 0) - Number(left?.id || 0)
)));

const hasActivities = computed(() => sortedActivities.value.length > 0);
</script>

<template>
  <section class='wechat-activity-panel'>
    <header class='activity-header'>
      <div class='activity-title'>
        <wechat-outlined />
        <span>{{ t('customerService.activity.title') }}</span>
      </div>
      <a-space :size='8' wrap>
        <a-select
          :value='activityType'
          size='small'
          class='activity-type-select'
          :options='typeOptions'
          @update:value='updateActivityType'
        />
        <a-input-search
          :value='keyword'
          size='small'
          allow-clear
          class='activity-search'
          @update:value='value => emit("update:keyword", value)'
          @search='emit("refresh")'
        />
        <a-button size='small' @click='emit("refresh")'>
          <template #icon><reload-outlined /></template>
        </a-button>
      </a-space>
    </header>

    <a-spin :spinning='loading'>
      <a-list
        v-if='hasActivities'
        class='activity-list'
        item-layout='vertical'
        :data-source='sortedActivities'
      >
        <template #renderItem='{ item }'>
          <a-list-item class='activity-item' :class='`tone-${metaOf(item).tone}`'>
            <template #extra>
              <a-tag :color='metaOf(item).color'>{{ metaOf(item).text }}</a-tag>
            </template>

            <a-list-item-meta>
              <template #avatar>
                <a-avatar v-if='item.avatar' :src='item.avatar' :size='42' />
                <a-avatar v-else :size='42' class='activity-avatar'>
                  <component :is='metaOf(item).icon' />
                </a-avatar>
              </template>
              <template #title>
                <span class='activity-meta-title'>
                  <strong>{{ activityTitle(item) }}</strong>
                  <small>{{ item.createTime }}</small>
                </span>
              </template>
              <template #description>
                <span class='activity-user-line'>
                  <strong>{{ displayName(item) }}</strong>
                  <a-tag v-if='item.userId' color='blue'>{{ t('customerService.activity.linked') }}</a-tag>
                  <span>{{ userTraceText(item) }}</span>
                </span>
              </template>
            </a-list-item-meta>

            <div v-if='shouldShowContent(item)' class='activity-content'>
              <a
                v-if='isImageUrl(item)'
                :href='item.content'
                target='_blank'
                rel='noreferrer'
              >
                {{ t('customerService.activity.viewImage') }}
              </a>
              <template v-else>
                {{ activitySummary(item) }}
              </template>
            </div>

            <div v-if='detailTags(item).length' class='activity-details'>
              <a-tag
                v-for='tag in detailTags(item)'
                :key='`${tag.color}-${tag.text}`'
                :color='tag.color'
              >
                {{ tag.text }}
              </a-tag>
            </div>
          </a-list-item>
        </template>
      </a-list>

      <div v-else class='activity-empty'>
        <a-empty :description="hasFilter ? t('customerService.activity.noMatch') : t('customerService.activity.empty')">
          <a-button v-if='hasFilter' size='small' @click='emit("reset")'>{{ t('customerService.activity.clearFilters') }}</a-button>
        </a-empty>
      </div>
    </a-spin>
  </section>
</template>

<style scoped lang='less'>
.wechat-activity-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  height: 100%;
  overflow: hidden;
  background: #f7f9fb;

  :deep(.ant-spin-nested-loading),
  :deep(.ant-spin-container) {
    min-height: 0;
    height: 100%;
  }
}

.activity-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #edf1f5;
  background: rgba(255, 255, 255, .98);
}

.activity-title,
.activity-meta-title,
.activity-user-line,
.activity-details {
  display: flex;
  align-items: center;
}

.activity-title {
  gap: 8px;
  color: #203626;
  font-size: 15px;
  font-weight: 800;
}

.activity-type-select {
  width: 108px;
}

.activity-search {
  width: 180px;
}

.activity-list {
  height: 100%;
  min-height: 0;
  padding: 14px 16px 18px;
  overflow-y: auto;
}

.activity-item {
  position: relative;
  margin-bottom: 10px;
  padding: 12px 14px !important;
  border: 1px solid #e8eef4;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(24, 43, 70, .06);

  &::before {
    position: absolute;
    top: 12px;
    bottom: 12px;
    left: 0;
    width: 3px;
    border-radius: 0 999px 999px 0;
    background: #98a4b3;
    content: '';
  }

  &.tone-green::before { background: #33a204; }
  &.tone-red::before { background: #ff4d4f; }
  &.tone-blue::before { background: #1677ff; }
  &.tone-purple::before { background: #7b61ff; }
  &.tone-cyan::before { background: #13c2c2; }
  &.tone-orange::before { background: #fa8c16; }
}

.activity-avatar {
  background: #eef6ff;
  color: #1677ff;
}

.activity-meta-title {
  min-width: 0;
  justify-content: space-between;
  gap: 8px;

  strong {
    min-width: 0;
    overflow: hidden;
    color: #1f3325;
    font-size: 14px;
    line-height: 1.35;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    flex: none;
    color: #8c98a7;
    font-size: 12px;
  }
}

.activity-user-line {
  flex-wrap: wrap;
  gap: 6px;
  color: #718091;
  font-size: 12px;

  strong {
    color: #405466;
  }
}

.activity-content {
  margin-top: 10px;
  padding: 9px 11px;
  border-radius: 8px;
  background: #f8fafc;
  color: #203040;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;

  a {
    color: #1677ff;
    font-weight: 700;
  }
}

.activity-details {
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;

  :deep(.ant-tag) {
    margin-inline-end: 0;
  }
}

.activity-empty {
  display: grid;
  min-height: 360px;
  place-items: center;
}

@media only screen and (max-width: 720px) {
  .activity-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .activity-search {
    width: min(220px, calc(100vw - 180px));
  }

  .activity-list {
    padding: 12px;
  }
}
</style>
