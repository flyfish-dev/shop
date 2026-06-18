<script setup>
import { ref, onMounted } from 'vue';
import { getShopItemGroups } from '../../apis/api';
import { createItemGroup, updateItemGroup, deleteItemGroup } from '../../apis/manage';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';

const loading = ref(false);
const dataSource = ref([]);
const editingGroup = ref();

const columns = [
  {
    title: '分组名称',
    dataIndex: 'name',
    width: 160,
  },
  {
    title: '排序',
    dataIndex: 'sort',
    width: 90,
  },
  {
    title: '启用',
    dataIndex: 'enabled',
    width: 100,
  },
  {
    title: '描述',
    dataIndex: 'description',
    ellipsis: true,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 180,
  },
  {
    title: '操作',
    dataIndex: 'action',
    width: 140,
    fixed: 'right',
  },
];

const loadData = async () => {
  loading.value = true;
  try {
    const data = await getShopItemGroups();
    dataSource.value = data;
  } catch (e) {
    message.error('加载失败');
  } finally {
    loading.value = false;
  }
};

const handleAdd = () => {
  if (editingGroup.value) {
    message.warning('请先保存当前编辑的分组');
    return;
  }

  const newGroup = {
    id: 'temp_' + Date.now(),
    name: '',
    sort: 0,
    enabled: true,
    description: '',
    isNew: true
  };
  dataSource.value = [newGroup, ...dataSource.value];
  editingGroup.value = newGroup;
};

const handleEdit = (record) => {
  if (editingGroup.value) {
    message.warning('请先保存当前编辑的分组');
    return;
  }
  editingGroup.value = { ...record };
};

const handleCancel = () => {
  if (editingGroup.value?.isNew) {
    dataSource.value = dataSource.value.filter(item => !item.isNew);
  }
  editingGroup.value = null;
};

const handleDelete = async (id) => {
  try {
    await deleteItemGroup(id);
    message.success('删除成功');
    await loadData();
  } catch (e) {
    message.error('删除失败');
  }
};

const handleSave = async (record) => {
  try {
    if (!record.name?.trim()) {
      message.error('分组名称不能为空');
      return;
    }

    if (!record.isNew) {
      await updateItemGroup(record.id, {
        name: record.name.trim(),
        sort: record.sort || 0,
        enabled: record.enabled !== false,
        description: record.description?.trim()
      });
      message.success('更新成功');
    } else {
      await createItemGroup({
        name: record.name.trim(),
        sort: record.sort || 0,
        enabled: record.enabled !== false,
        description: record.description?.trim()
      });
      message.success('创建成功');
    }
    editingGroup.value = null;
    await loadData();
  } catch (e) {
    message.error(e.message || '操作失败');
  }
};

onMounted(loadData);
</script>

<template>
  <div class="group-manage">
    <a-card>
      <template #title>
        <span>商品分组管理</span>
      </template>
      <template #extra>
        <a-button type="primary" @click="handleAdd">
          新增分组
        </a-button>
      </template>

      <a-table
        :loading="loading"
        :columns="columns"
        :data-source="dataSource"
        :pagination="false"
        :scroll="{ x: 820 }"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'name'">
            <template v-if="editingGroup?.id === record.id">
              <div class="editing-cell">
                <a-input
                  v-model:value="editingGroup.name"
                  :status="editingGroup.name?.trim() ? '' : 'error'"
                  @pressEnter="handleSave(editingGroup)"
                />
              </div>
            </template>
            <template v-else>
              {{ record.name }}
            </template>
          </template>

          <template v-else-if="column.dataIndex === 'sort'">
            <template v-if="editingGroup?.id === record.id">
              <div class="editing-cell">
                <a-input-number
                  v-model:value="editingGroup.sort"
                  :min="0"
                  style="width: 100%"
                  @pressEnter="handleSave(editingGroup)"
                />
              </div>
            </template>
            <template v-else>
              {{ record.sort }}
            </template>
          </template>

          <template v-else-if="column.dataIndex === 'description'">
            <template v-if="editingGroup?.id === record.id">
              <div class="editing-cell">
                <a-input
                  v-model:value="editingGroup.description"
                  @pressEnter="handleSave(editingGroup)"
                />
              </div>
            </template>
            <template v-else>
              {{ record.description }}
            </template>
          </template>

          <template v-else-if="column.dataIndex === 'enabled'">
            <template v-if="editingGroup?.id === record.id">
              <a-switch
                v-model:checked="editingGroup.enabled"
                :checkedValue="true"
                :unCheckedValue="false"
                checked-children="启用"
                un-checked-children="停用"
              />
            </template>
            <template v-else>
              <a-tag :color="record.enabled === false ? 'default' : 'green'">
                {{ record.enabled === false ? '停用' : '启用' }}
              </a-tag>
            </template>
          </template>

          <template v-else-if="column.dataIndex === 'action'">
            <a-space>
              <template v-if="record.isNew || editingGroup?.id === record.id">
                <a-button
                  type="link"
                  :disabled="!editingGroup.name?.trim()"
                  @click="handleSave(editingGroup)"
                >
                  保存
                </a-button>
                <a-button type="link" @click="handleCancel">取消</a-button>
              </template>
              <template v-else>
                <a-button type="link" @click="handleEdit(record)">编辑</a-button>
                <a-popconfirm
                  v-if="!record.isNew"
                  title="确定要删除该分组吗?"
                  @confirm="handleDelete(record.id)"
                >
                  <a-button type="link" danger>删除</a-button>
                </a-popconfirm>
              </template>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped lang="less">
.group-manage {
  padding: 20px;
  min-width: 0;

  :deep(.ant-card-head) {
    border-bottom: none;
  }

  :deep(.ant-table-cell) {
    vertical-align: middle;
  }

  :deep(.ant-input-number) {
    width: 100%;
  }

  :deep(.ant-btn-link) {
    padding: 0;
    height: auto;
  }

  .editing-cell {
    padding: 4px 0;
  }

  // 固定操作列样式
  :deep(.ant-table-cell-fix-right) {
    background: #fff;
  }
}

@media only screen and (max-width: 640px) {
  .group-manage {
    padding: 0;
  }

  :deep(.ant-card) {
    border-radius: 8px;
  }

  :deep(.ant-card-head) {
    min-height: 50px;
    padding: 0 12px;
  }

  :deep(.ant-card-head-title) {
    min-width: 0;
    overflow: hidden;
    font-size: 16px;
    text-align: left;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  :deep(.ant-card-extra) {
    padding: 8px 0;

    .ant-btn {
      height: 32px;
      padding-inline: 12px;
    }
  }

  :deep(.ant-card-body) {
    padding: 12px;
  }

  :deep(.ant-table) {
    font-size: 13px;
  }

  :deep(.ant-table-thead > tr > th),
  :deep(.ant-table-tbody > tr > td) {
    white-space: nowrap;
  }
}
</style>
