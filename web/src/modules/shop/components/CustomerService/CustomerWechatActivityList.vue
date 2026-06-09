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

const typeOptions = [
  { label: '全部动态', value: 'ALL' },
  { label: '关注', value: 'SUBSCRIBE' },
  { label: '取关', value: 'UNSUBSCRIBE' },
  { label: '扫码', value: 'SCAN' },
  { label: '文字', value: 'TEXT' },
  { label: '图片', value: 'IMAGE' },
  { label: '位置', value: 'LOCATION' },
  { label: '事件', value: 'EVENT' }
];

const typeMeta = {
  SUBSCRIBE: { text: '关注', color: 'green', icon: UserAddOutlined, tone: 'green' },
  UNSUBSCRIBE: { text: '取关', color: 'red', icon: UserDeleteOutlined, tone: 'red' },
  SCAN: { text: '扫码', color: 'blue', icon: QrcodeOutlined, tone: 'blue' },
  TEXT: { text: '文字', color: 'geekblue', icon: MessageOutlined, tone: 'blue' },
  IMAGE: { text: '图片', color: 'purple', icon: PictureOutlined, tone: 'purple' },
  LOCATION: { text: '位置', color: 'cyan', icon: EnvironmentOutlined, tone: 'cyan' },
  EVENT: { text: '事件', color: 'orange', icon: ThunderboltOutlined, tone: 'orange' },
  MESSAGE: { text: '消息', color: 'default', icon: MessageOutlined, tone: 'gray' }
};

const metaOf = item => typeMeta[item?.activityType] || typeMeta.MESSAGE;

const openidSuffix = value => {
  const text = String(value || '');
  return text.length > 10 ? text.slice(-8) : text;
};

const displayName = item => customerDisplayName({
  displayName: item?.displayName,
  avatar: item?.avatar,
  wechatOpenid: item?.wechatOpenid,
  userId: item?.userId
});

const hasActivities = computed(() => props.activities.length > 0);
</script>

<template>
  <section class='wechat-activity-panel'>
    <header class='activity-header'>
      <div class='activity-title'>
        <wechat-outlined />
        <span>公众号动态</span>
      </div>
      <a-space :size='8' wrap>
        <a-select
          :value='activityType'
          size='small'
          class='activity-type-select'
          :options='typeOptions'
          @update:value='value => emit("update:activityType", value)'
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
        :data-source='activities'
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
                  <strong>{{ item.title }}</strong>
                  <small>{{ item.createTime }}</small>
                </span>
              </template>
              <template #description>
                <span class='activity-user-line'>
                  <strong>{{ displayName(item) }}</strong>
                  <a-tag v-if='item.userId' color='blue'>已绑定</a-tag>
                  <span>openid · {{ openidSuffix(item.wechatOpenid) }}</span>
                </span>
              </template>
            </a-list-item-meta>

            <div class='activity-content'>
              {{ item.content || '无内容' }}
            </div>

            <div class='activity-details'>
              <a-tag v-if='item.messageType'>{{ item.messageType }}</a-tag>
              <a-tag v-if='item.eventType' color='orange'>{{ item.eventType }}</a-tag>
              <a-tag v-if='item.eventKey' color='cyan'>{{ item.eventKey }}</a-tag>
              <a-tag v-if='item.wechatMsgId' color='default'>MsgId {{ openidSuffix(item.wechatMsgId) }}</a-tag>
            </div>
          </a-list-item>
        </template>
      </a-list>

      <div v-else class='activity-empty'>
        <a-empty :description='hasFilter ? "暂无匹配动态" : "暂无公众号动态"'>
          <a-button v-if='hasFilter' size='small' @click='emit("reset")'>清空筛选</a-button>
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
  background: #f7f9fb;
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
  min-height: 0;
  padding: 14px 16px 18px;
  overflow-y: auto;
}

.activity-item {
  position: relative;
  margin-bottom: 10px;
  padding: 14px 16px !important;
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
  gap: 8px;

  strong {
    color: #1f3325;
    font-size: 14px;
  }

  small {
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
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: #203040;
  font-size: 13px;
  line-height: 1.65;
  word-break: break-word;
}

.activity-details {
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
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
