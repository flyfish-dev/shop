<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  ApiOutlined,
  CloudSyncOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue';
import {
  gitProviders,
  permissionOptions,
  useGitRepositoryManage
} from '../../hooks/useGitRepositoryManage.js';

const {
  loading,
  saving,
  tokenLoading,
  remoteLoading,
  syncing,
  repositories,
  tokens,
  provider,
  keyword,
  syncTokenId,
  repositoryForm,
  tokenForm,
  tokenOptions,
  syncTokenOptions,
  remoteOptions,
  loadRepositories,
  loadTokens,
  loadRemoteRepositories,
  resetRepositoryForm,
  resetTokenForm,
  applyRemoteRepository,
  saveRepository,
  syncRepositories,
  saveToken,
  removeRepository,
  removeToken
} = useGitRepositoryManage();

const repositoryModalVisible = ref(false);
const tokenManagerVisible = ref(false);
const tokenModalVisible = ref(false);
const editingRepositoryId = ref(null);
const editingTokenId = ref(null);
const repositoryFormRef = ref();
const tokenFormRef = ref();
const tokenTab = ref('github');
const resettingRepositoryForm = ref(false);

const repositoryColumns = [
  { title: '仓库', dataIndex: 'name', key: 'name', width: 280 },
  { title: '平台', dataIndex: 'providerName', key: 'provider', width: 100 },
  { title: 'Token', dataIndex: 'accessTokenName', key: 'token', width: 180 },
  { title: '权限', dataIndex: 'permissionName', key: 'permission', width: 100 },
  { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 110 },
  { title: '排序', dataIndex: 'sort', key: 'sort', width: 90 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' }
];

const tokenColumns = [
  { title: '名称', dataIndex: 'name', key: 'name', width: 220 },
  { title: '账号', dataIndex: 'username', key: 'username', width: 160 },
  { title: 'Token', dataIndex: 'tokenMasked', key: 'token', width: 180 },
  { title: '过期时间', dataIndex: 'expireTime', key: 'expireTime', width: 180 },
  { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 100 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' }
];

const repositoryRules = {
  provider: [{ required: true, message: '请选择平台', trigger: 'change' }],
  accessTokenId: [{ required: true, message: '请选择 API Token', trigger: 'change' }],
  owner: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  repo: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  name: [{ required: true, message: '请输入管理名称', trigger: 'blur' }]
};

const tokenRules = {
  provider: [{ required: true, message: '请选择平台', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  tokenValue: [{ required: true, message: '请输入 API Token', trigger: 'blur' }]
};

const repositoryModalTitle = computed(() => editingRepositoryId.value ? '编辑代码仓库' : '新增代码仓库');
const tokenModalTitle = computed(() => editingTokenId.value ? '编辑 API Token' : '新增 API Token');
const currentPermissions = computed(() => permissionOptions(repositoryForm.provider));
const activeTokenRules = computed(() => editingTokenId.value
  ? { ...tokenRules, tokenValue: [] }
  : tokenRules);

const openCreateRepository = async () => {
  editingRepositoryId.value = null;
  resettingRepositoryForm.value = true;
  resetRepositoryForm();
  await nextTick();
  resettingRepositoryForm.value = false;
  await loadTokens(repositoryForm.provider);
  repositoryModalVisible.value = true;
};

const openEditRepository = async record => {
  editingRepositoryId.value = record.id;
  resettingRepositoryForm.value = true;
  resetRepositoryForm(record);
  await nextTick();
  resettingRepositoryForm.value = false;
  await loadTokens(record.provider);
  repositoryModalVisible.value = true;
};

const submitRepository = async () => {
  await repositoryFormRef.value?.validate?.();
  try {
    await saveRepository(editingRepositoryId.value);
    message.success(editingRepositoryId.value ? '仓库已更新' : '仓库已创建');
    repositoryModalVisible.value = false;
  } catch (e) {
    message.error(e.message || '保存失败');
  }
};

const openTokenManager = async () => {
  tokenTab.value = provider.value || 'github';
  await loadTokens(tokenTab.value);
  tokenManagerVisible.value = true;
};

const openCreateToken = () => {
  editingTokenId.value = null;
  resetTokenForm(null, tokenTab.value);
  tokenModalVisible.value = true;
};

const openEditToken = record => {
  editingTokenId.value = record.id;
  resetTokenForm(record, record.provider);
  tokenModalVisible.value = true;
};

const submitToken = async () => {
  await tokenFormRef.value?.validate?.();
  try {
    await saveToken(editingTokenId.value);
    message.success(editingTokenId.value ? 'Token 已更新' : 'Token 已创建');
    tokenModalVisible.value = false;
    if (repositoryModalVisible.value && repositoryForm.provider === tokenForm.provider) {
      await loadTokens(repositoryForm.provider);
    }
  } catch (e) {
    message.error(e.message || '保存失败');
  }
};

const deleteRepository = async record => {
  await removeRepository(record);
  message.success('仓库已删除');
};

const deleteToken = async record => {
  await removeToken(record);
  message.success('Token 已删除');
};

const handleSyncRepositories = async () => {
  try {
    const result = await syncRepositories();
    if (result) {
      message.success(`已同步 ${result.totalCount || 0} 个仓库`);
    }
  } catch (e) {
    message.error(e.message || '同步失败');
  }
};

watch(provider, async next => {
  syncTokenId.value = undefined;
  await Promise.all([loadRepositories(), loadTokens(next)]);
});

watch(tokenTab, next => {
  loadTokens(next);
});

watch(tokenManagerVisible, visible => {
  if (!visible) {
    loadTokens(provider.value);
  }
});

watch(() => repositoryForm.provider, async next => {
  if (resettingRepositoryForm.value) {
    return;
  }
  repositoryForm.permission = permissionOptions(next)[0].value;
  repositoryForm.accessTokenId = undefined;
  repositoryForm.remoteKey = undefined;
  await loadTokens(next);
});

watch(() => repositoryForm.accessTokenId, () => {
  repositoryForm.remoteKey = undefined;
  loadRemoteRepositories();
});

onMounted(async () => {
  await Promise.all([loadRepositories(), loadTokens(provider.value)]);
});
</script>

<template>
  <div class="git-repository-manage">
    <a-card title="代码仓库管理">
      <template #extra>
        <a-space>
          <a-select v-model:value="provider" :options="gitProviders" style="width: 120px" />
          <a-input-search
            v-model:value="keyword"
            allow-clear
            style="width: 240px"
            @search="loadRepositories"
          />
          <a-button @click="loadRepositories">
            <template #icon><reload-outlined /></template>
            刷新
          </a-button>
          <a-select
            v-model:value="syncTokenId"
            :options="syncTokenOptions"
            :loading="tokenLoading"
            style="width: 220px"
          />
          <a-button :loading="syncing" :disabled="!syncTokenId" @click="handleSyncRepositories">
            <template #icon><cloud-sync-outlined /></template>
            同步仓库
          </a-button>
          <a-button @click="openTokenManager">
            <template #icon><api-outlined /></template>
            API Token
          </a-button>
          <a-button type="primary" @click="openCreateRepository">
            <template #icon><plus-outlined /></template>
            新增仓库
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="repositoryColumns"
        :data-source="repositories"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 1100 }"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div class="repo-name">
              <strong>{{ record.name }}</strong>
              <a :href="record.url" target="_blank" rel="noreferrer">{{ record.fullName }}</a>
              <span v-if="record.description">{{ record.description }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'provider'">
            <a-tag color="blue">{{ record.providerName }}</a-tag>
          </template>
          <template v-else-if="column.key === 'token'">
            {{ record.accessTokenName || '--' }}
          </template>
          <template v-else-if="column.key === 'permission'">
            <a-tag color="green">{{ record.permissionName }}</a-tag>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">
              {{ record.enabled ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openEditRepository(record)">
                <template #icon><edit-outlined /></template>
                编辑
              </a-button>
              <a-popconfirm title="确认删除该仓库？" @confirm="deleteRepository(record)">
                <a-button type="link" danger size="small">
                  <template #icon><delete-outlined /></template>
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="repositoryModalVisible"
      :title="repositoryModalTitle"
      :confirm-loading="saving"
      width="680px"
      @ok="submitRepository"
    >
      <a-form ref="repositoryFormRef" layout="vertical" :model="repositoryForm" :rules="repositoryRules">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="平台" name="provider">
              <a-select v-model:value="repositoryForm.provider" :options="gitProviders" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="API Token" name="accessTokenId">
              <a-select
                v-model:value="repositoryForm.accessTokenId"
                :options="tokenOptions"
                :loading="tokenLoading"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="远程仓库" name="remoteKey">
          <a-select
            v-model:value="repositoryForm.remoteKey"
            show-search
            allow-clear
            :filter-option="false"
            :loading="remoteLoading"
            :options="remoteOptions"
            @search="loadRemoteRepositories"
            @change="applyRemoteRepository"
            @dropdown-visible-change="open => open && loadRemoteRepositories()"
          />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="Owner" name="owner">
              <a-input v-model:value="repositoryForm.owner" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="仓库名称" name="repo">
              <a-input v-model:value="repositoryForm.repo" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="管理名称" name="name">
          <a-input v-model:value="repositoryForm.name" />
        </a-form-item>
        <a-form-item label="中文描述" name="description">
          <a-textarea v-model:value="repositoryForm.description" :rows="3" />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="交付权限" name="permission">
              <a-select v-model:value="repositoryForm.permission" :options="currentPermissions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="排序" name="sort">
              <a-input-number v-model:value="repositoryForm.sort" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="过期时间" name="expireTime">
              <a-date-picker
                v-model:value="repositoryForm.expireTime"
                show-time
                style="width: 100%"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="启用状态" name="enabled">
              <a-switch v-model:checked="repositoryForm.enabled" checked-children="启用" un-checked-children="停用" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal v-model:open="tokenManagerVisible" title="API Token 管理" width="840px" :footer="null">
      <a-tabs v-model:active-key="tokenTab">
        <a-tab-pane v-for="item in gitProviders" :key="item.value" :tab="item.label">
          <div class="token-toolbar">
            <a-button type="primary" @click="openCreateToken">
              <template #icon><plus-outlined /></template>
              新增 Token
            </a-button>
          </div>
          <a-table
            :columns="tokenColumns"
            :data-source="tokens"
            :loading="tokenLoading"
            :pagination="{ pageSize: 6 }"
            :scroll="{ x: 900 }"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'enabled'">
                <a-tag :color="record.enabled && !record.expired ? 'green' : 'default'">
                  {{ record.enabled && !record.expired ? '可用' : '不可用' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'expireTime'">
                {{ record.expireTime || '长期' }}
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="openEditToken(record)">
                    <template #icon><edit-outlined /></template>
                    编辑
                  </a-button>
                  <a-popconfirm title="确认删除该 Token？" @confirm="deleteToken(record)">
                    <a-button type="link" danger size="small">
                      <template #icon><delete-outlined /></template>
                      删除
                    </a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-modal>

    <a-modal
      v-model:open="tokenModalVisible"
      :title="tokenModalTitle"
      :confirm-loading="saving"
      width="560px"
      @ok="submitToken"
    >
      <a-form ref="tokenFormRef" layout="vertical" :model="tokenForm" :rules="activeTokenRules">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="平台" name="provider">
              <a-select v-model:value="tokenForm.provider" :options="gitProviders" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" name="name">
              <a-input v-model:value="tokenForm.name" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="API Token" name="tokenValue">
          <a-input-password v-model:value="tokenForm.tokenValue" autocomplete="off" />
        </a-form-item>
        <a-form-item label="账号名称" name="username">
          <a-input v-model:value="tokenForm.username" />
        </a-form-item>
        <a-form-item label="中文描述" name="description">
          <a-textarea v-model:value="tokenForm.description" :rows="3" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="过期时间" name="expireTime">
              <a-date-picker
                v-model:value="tokenForm.expireTime"
                show-time
                style="width: 100%"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="启用状态" name="enabled">
              <a-switch v-model:checked="tokenForm.enabled" checked-children="启用" un-checked-children="停用" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped lang="less">
.git-repository-manage {
  .repo-name {
    display: grid;
    gap: 4px;
    min-width: 0;

    strong,
    a,
    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    a {
      color: #1677ff;
      font-size: 13px;
    }

    span {
      color: #6b7280;
      font-size: 12px;
    }
  }

  .token-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 12px;
  }
}
</style>
