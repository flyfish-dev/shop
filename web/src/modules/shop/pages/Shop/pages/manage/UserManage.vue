<script setup>
import { computed, onMounted, ref } from 'vue';
import { ReloadOutlined, SearchOutlined, UserOutlined } from '@ant-design/icons-vue';
import { getManageUsers } from '../../apis/manage.js';

const loading = ref(false);
const keyword = ref('');
const users = ref([]);

const columns = [
  {
    title: '用户',
    dataIndex: 'username',
    key: 'user',
    width: 240
  },
  {
    title: '联系方式',
    key: 'contact',
    width: 260
  },
  {
    title: '绑定账号',
    key: 'authorizations'
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 170
  }
];

const authLabels = {
  gitea: 'Gitea',
  github: 'GitHub',
  gitee: '码云',
  wechat: '微信'
};

const loadUsers = async () => {
  loading.value = true;
  try {
    users.value = await getManageUsers({ keyword: keyword.value?.trim() || undefined });
  } finally {
    loading.value = false;
  }
};

const resolveAuthList = user => Object.entries(user.authorizations || {})
  .map(([type, auth]) => ({
    type,
    label: authLabels[type] || type,
    name: auth.displayName || auth.nickname || auth.loginName || auth.openid,
    login: auth.loginName,
    avatar: auth.avatarUrl,
    profileUrl: auth.profileUrl
  }));

const totalText = computed(() => `共 ${users.value.length} 位用户`);

onMounted(loadUsers);
</script>

<template>
  <div class="user-manage">
    <a-card title="用户管理">
      <template #extra>
        <a-space wrap>
          <span class="total">{{ totalText }}</span>
          <a-input-search
            v-model:value="keyword"
            allow-clear
            enter-button
            class="search-input"
            @search="loadUsers"
          >
            <template #prefix>
              <search-outlined />
            </template>
          </a-input-search>
          <a-button @click="loadUsers">
            <template #icon><reload-outlined /></template>
            刷新
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="users"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 980 }"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'user'">
            <div class="user-cell">
              <a-avatar v-if="record.avatar" :src="record.avatar" :size="42" />
              <a-avatar v-else :size="42">
                <user-outlined />
              </a-avatar>
              <div class="user-main">
                <strong>{{ record.username || `用户 ${record.id}` }}</strong>
                <span>ID {{ record.id }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'contact'">
            <div class="contact-cell">
              <span v-if="record.phone">{{ record.phone }}</span>
              <span v-if="record.email">{{ record.email }}</span>
              <span v-if="!record.phone && !record.email">未维护</span>
            </div>
          </template>
          <template v-else-if="column.key === 'authorizations'">
            <div class="auth-list">
              <template v-if="!resolveAuthList(record).length">
                <a-empty :image="null" description="未绑定" />
              </template>
              <template v-else>
                <a
                  v-for="auth in resolveAuthList(record)"
                  :key="`${record.id}-${auth.type}-${auth.name}`"
                  class="auth-pill"
                  :href="auth.profileUrl || undefined"
                  :target="auth.profileUrl ? '_blank' : undefined"
                  rel="noreferrer"
                >
                  <a-avatar v-if="auth.avatar" :src="auth.avatar" :size="22" />
                  <span>{{ auth.label }}</span>
                  <strong>{{ auth.name }}</strong>
                </a>
              </template>
            </div>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped lang="less">
.user-manage {
  min-width: 0;
  padding: 20px;
}

.total {
  color: #6b7a90;
  font-size: 13px;
}

.search-input {
  width: 220px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.user-main {
  display: flex;
  min-width: 0;
  flex-direction: column;

  strong {
    overflow: hidden;
    color: #24364d;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: #8a96a8;
    font-size: 12px;
  }
}

.contact-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #5e6f85;
  font-size: 13px;
}

.auth-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  :deep(.ant-empty) {
    margin: 0;
    color: #8c96a5;
  }
}

.auth-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 260px;
  padding: 4px 8px 4px 4px;
  border: 1px solid #e2ebf5;
  border-radius: 8px;
  color: #42546b;
  background: #fbfdff;
  text-decoration: none;

  strong {
    min-width: 0;
    overflow: hidden;
    color: #21334a;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media only screen and (max-width: 640px) {
  .user-manage {
    padding: 0;
  }

  .search-input {
    width: 180px;
  }
}
</style>
