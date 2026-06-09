<script setup>
import { defineAsyncComponent, ref, onMounted } from 'vue';
import { getShopItems, getShopItemGroups } from '../../apis/api';
import { createItem, updateItem, deleteItem } from '../../apis/manage';
import { message, theme } from 'ant-design-vue';
import keyBy from 'lodash/keyBy';
import { resolveShopItemCover, setShopImageFallback } from '@/modules/shop/utils/shopCovers.js';
import { deliveryModeColor, deliveryModeText } from '@/modules/shop/utils/shopDelivery.js';

const ShopItemModal = defineAsyncComponent(() => import('../ShopItemModal.vue'));

const { useToken } = theme;
const { token } = useToken();

const loading = ref(false);
const dataSource = ref([]);
const visible = ref(false);
const editingItem = ref(null);
const groups = ref([]); // 商品分组列表
const selectedGroupId = ref(undefined); // 选中的分组ID

// 分页配置
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: (total) => `共 ${total} 条`,
  showSizeChanger: true,
  showQuickJumper: true,
  showLessItems: true,
});

const tableScroll = { x: 1340 };

const columns = [
  {
    title: '商品图片',
    dataIndex: 'cover',
    key: 'cover',
    width: 92,
  },
  {
    title: '商品名称',
    dataIndex: 'name',
    width: 210,
    ellipsis: true,
  },
  {
    title: '分组',
    dataIndex: 'groupName',
    width: 110,
  },
  {
    title: '商品类型',
    dataIndex: 'typeName',
    key: 'type',
    width: 120,
  },
  {
    title: '交付方式',
    dataIndex: 'deliveryMode',
    key: 'deliveryMode',
    width: 112,
  },
  {
    title: '价格',
    dataIndex: 'price',
    width: 92,
  },
  {
    title: '购买量',
    dataIndex: 'buyCount',
    width: 88,
  },
  {
    title: '排序',
    dataIndex: 'sort',
    key: 'sort',
    width: 96,
  },
  {
    title: '置顶',
    dataIndex: 'pinned',
    key: 'pinned',
    width: 86,
  },
  {
    title: '推荐',
    dataIndex: 'recommended',
    key: 'recommended',
    width: 86,
  },
  {
    title: '状态',
    dataIndex: 'enabled',
    key: 'enabled',
    width: 98,
  },
  {
    title: '操作',
    key: 'action',
    width: 110,
  },
];

// 加载分组数据
const loadGroups = async () => {
  try {
    groups.value = await getShopItemGroups();
  } catch (e) {
    message.error('加载分组失败');
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const records = await getShopItems({
      page: pagination.value.current - 1,
      size: pagination.value.pageSize,
      groupId: selectedGroupId.value,
      includeDisabled: true
    });
    // 获取分组
    if (records.length) {
      const groupMap = keyBy(groups.value, 'id');
      records.forEach(item => {
        item.groupName = groupMap[item.groupId]?.name || '未分组';
      });
    }
    dataSource.value = records;
    pagination.value.total = records.page?.total ?? records.length;
  } finally {
    loading.value = false;
  }
};

// 处理分组变化
const handleGroupChange = (value) => {
  selectedGroupId.value = value;
  pagination.value.current = 1; // 重置页码
  loadData();
};

// 处理分页变化
const handleTableChange = (pag) => {
  pagination.value.current = pag.current;
  pagination.value.pageSize = pag.pageSize;
  loadData();
};

const handleAdd = () => {
  editingItem.value = null;
  visible.value = true;
};

const handleEdit = (record) => {
  editingItem.value = { ...record };
  visible.value = true;
};

const handleDelete = async (id) => {
  try {
    await deleteItem(id);
    message.success('删除成功');
    await loadData();
  } catch (e) {
    message.error('删除失败');
  }
};

const handleSave = async (values) => {
  try {
    if (editingItem.value) {
      await updateItem(editingItem.value.id, values);
      message.success('更新成功');
    } else {
      await createItem(values);
      message.success('创建成功');
    }
    visible.value = false;
    await loadData();
  } catch (e) {
    message.error(e.message || '操作失败');
  }
};

const handleSortChange = async (record) => {
  try {
    await updateItem(record.id, { sort: record.sort ?? 0 });
    message.success('排序已更新');
    await loadData();
  } catch (e) {
    await loadData();
    message.error('操作失败');
  }
};

const handleFlagChange = async (record, field, checked) => {
  try {
    await updateItem(record.id, { [field]: checked });
    const actionName = {
      enabled: checked ? '上架' : '下架',
      pinned: checked ? '置顶' : '取消置顶',
      recommended: checked ? '推荐' : '取消推荐',
    }[field] || '更新';
    message.success(`${actionName}成功`);
    await loadData();
  } catch (e) {
    await loadData();
    message.error('操作失败');
  }
};

onMounted(async () => {
  await loadGroups();
  await loadData();
});
</script>

<template>
  <div class="item-manage">
    <a-card title="商品管理">
      <template #extra>
        <a-button type="primary" @click="handleAdd">
          新增商品
        </a-button>
      </template>

      <!-- 添加筛选区域 -->
      <div class="filter-area">
        <div class="filter-tags">
          商品分组：
          <a-tag
            class="group-tag"
            :class="{ active: !selectedGroupId }"
            @click="handleGroupChange(undefined)"
          >
            全部
          </a-tag>
          <a-tag
            v-for="group in groups"
            :key="group.id"
            class="group-tag"
            :class="{ active: selectedGroupId === group.id }"
            @click="handleGroupChange(group.id)"
          >
            {{ group.name }}
          </a-tag>
        </div>
      </div>

      <a-table
        :loading="loading"
        :columns="columns"
        :data-source="dataSource"
        :pagination="pagination"
        :scroll="tableScroll"
        @change="handleTableChange"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'cover'">
            <img
              class="cover-thumb"
              :src="resolveShopItemCover(record)"
              :alt="record.name"
              @error="event => setShopImageFallback(event, record.type)"
            />
          </template>

          <template v-else-if="column.key === 'type'">
            <a-tag color="blue">{{ record.typeName || record.type }}</a-tag>
          </template>

          <template v-else-if="column.key === 'deliveryMode'">
            <a-tag :color="deliveryModeColor(record.deliveryMode)">
              {{ record.deliveryModeName || deliveryModeText(record.deliveryMode) }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'sort'">
            <a-input-number
              v-model:value="record.sort"
              class="sort-input"
              :min="0"
              size="small"
              @blur="() => handleSortChange(record)"
              @pressEnter="() => handleSortChange(record)"
            />
          </template>

          <template v-else-if="column.key === 'pinned'">
            <a-switch
              :checked="record.pinned"
              :checkedValue="true"
              :unCheckedValue="false"
              checked-children="是"
              un-checked-children="否"
              @change="(checked) => handleFlagChange(record, 'pinned', checked)"
            />
          </template>

          <template v-else-if="column.key === 'recommended'">
            <a-switch
              :checked="record.recommended"
              :checkedValue="true"
              :unCheckedValue="false"
              checked-children="是"
              un-checked-children="否"
              @change="(checked) => handleFlagChange(record, 'recommended', checked)"
            />
          </template>

          <template v-else-if="column.key === 'enabled'">
            <a-switch
              :checked="record.enabled"
              :checkedValue="true"
              :unCheckedValue="false"
              checked-children="上架"
              un-checked-children="下架"
              @change="(checked) => handleFlagChange(record, 'enabled', checked)"
            />
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space>
              <a @click="handleEdit(record)">编辑</a>
              <a-popconfirm
                title="确定要删除该商品吗?"
                @confirm="handleDelete(record.id)"
              >
                <a>删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <shop-item-modal
      v-if="visible"
      v-model:visible="visible"
      :item="editingItem"
      @save="handleSave"
    />
  </div>
</template>

<style scoped lang="less">
.item-manage {
  padding: 16px;
  min-width: 0;

  :deep(.ant-card) {
    width: 100%;
  }

  :deep(.ant-card-body) {
    min-width: 0;
    padding: 20px;
  }

  :deep(.ant-table-wrapper) {
    width: 100%;
  }
}

.filter-area {
  margin-bottom: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 4px;

  .filter-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;

    // 标签前的文字样式
    color: v-bind('token.colorText');
    font-size: v-bind('token.fontSize');
  }

  :deep(.group-tag) {
    cursor: pointer;
    user-select: none;
    padding: 4px 12px;
    margin: 0;
    transition: all 0.3s;

    &:hover {
      color: v-bind('token.colorPrimary');
      border-color: v-bind('token.colorPrimary');
    }

    &.active {
      color: #fff;
      background: v-bind('token.colorPrimary');
      border-color: v-bind('token.colorPrimary');

      &:hover {
        background: v-bind('token.colorPrimaryHover');
        border-color: v-bind('token.colorPrimaryHover');
      }
    }
  }
}

.cover-thumb {
  width: 54px;
  height: 54px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
  object-fit: cover;
}

.sort-input {
  width: 72px;
}

@media only screen and (max-width: 640px) {
  .item-manage {
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

  .filter-area {
    margin-bottom: 12px;
    padding: 12px;
    border-radius: 8px;

    .filter-tags {
      gap: 6px;
      font-size: 13px;
    }
  }

  :deep(.group-tag) {
    padding: 3px 10px;
  }

  :deep(.ant-table-wrapper) {
    max-width: 100%;
  }

  :deep(.ant-table) {
    font-size: 13px;
  }

  :deep(.ant-table-thead > tr > th),
  :deep(.ant-table-tbody > tr > td) {
    white-space: nowrap;
  }

  :deep(.ant-pagination) {
    justify-content: flex-start;
    margin-bottom: 0;
  }
}
</style>
