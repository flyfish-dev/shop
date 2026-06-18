<script setup>
import { defineAsyncComponent, ref, onMounted } from 'vue';
import { getShopItems, getShopItemGroups } from '../../apis/api';
import { createItem, updateItem, deleteItem } from '../../apis/manage';
import { message, theme } from 'ant-design-vue';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ShoppingCartOutlined
} from '@ant-design/icons-vue';
import keyBy from 'lodash/keyBy';
import { resolveShopItemCover, setShopImageFallback } from '@/modules/shop/utils/shopCovers.js';
import { deliveryActionText, deliveryModeColor, deliveryModeText } from '@/modules/shop/utils/shopDelivery.js';

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

const formatPrice = price => {
  const amount = Number(price ?? 0);
  if (!Number.isFinite(amount)) {
    return price || '0.00';
  }
  return amount.toFixed(2);
};

const hasDeliveryActions = record => Array.isArray(record.deliveryActions) && record.deliveryActions.length > 0;

const itemStatusText = record => (record.enabled ? '上架中' : '已下架');

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

const handlePageChange = (page, pageSize) => {
  pagination.value.current = page;
  pagination.value.pageSize = pageSize;
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
        <a-button class="add-item-button" type="primary" @click="handleAdd">
          <template #icon><plus-outlined /></template>
          新增商品
        </a-button>
      </template>

      <div class="filter-area">
        <div class="filter-tags">
          <span class="filter-label">商品分组</span>
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

      <a-spin :spinning="loading">
        <a-empty v-if="!dataSource.length" class="item-empty" />

        <div v-else class="item-card-list">
          <article
            v-for="record in dataSource"
            :key="record.id"
            class="manage-item-card"
            :class="{
              'is-disabled': !record.enabled,
              'is-pinned': record.pinned,
              'is-recommended': record.recommended
            }"
          >
            <div class="item-card-top">
              <img
                class="cover-thumb"
                :src="resolveShopItemCover(record)"
                :alt="record.name"
                @error="event => setShopImageFallback(event, record.type)"
              />

              <div class="item-card-main">
                <div class="item-title-row">
                  <h3 :title="record.name">{{ record.name }}</h3>
                  <a-tag :color="record.enabled ? 'green' : 'default'" class="status-tag">
                    {{ itemStatusText(record) }}
                  </a-tag>
                </div>

                <div class="item-meta-line">
                  <span>{{ record.groupName || '未分组' }}</span>
                  <span>{{ record.typeName || record.type }}</span>
                </div>

                <div class="item-delivery-line">
                  <a-tag :color="deliveryModeColor(record.deliveryMode)">
                    {{ record.deliveryModeName || deliveryModeText(record.deliveryMode) }}
                  </a-tag>
                  <a-tag
                    v-for="action in record.deliveryActions || []"
                    :key="action"
                    color="cyan"
                  >
                    {{ deliveryActionText(action) }}
                  </a-tag>
                  <a-tag v-if="!hasDeliveryActions(record)" color="default">无交付动作</a-tag>
                </div>
              </div>
            </div>

            <div class="item-signal-row">
              <a-tag v-if="record.pinned" color="red">置顶</a-tag>
              <a-tag v-if="record.recommended" color="gold">推荐</a-tag>
              <a-tag v-if="record.contractRequired" color="purple">需签署</a-tag>
              <a-tag v-if="record.defaultCouponEnabled" color="green">
                优惠：{{ record.defaultCouponCode || '已启用' }}
              </a-tag>
            </div>

            <div class="item-metrics">
              <div class="metric-block price-block">
                <span class="metric-label">价格</span>
                <strong>¥{{ formatPrice(record.price) }}</strong>
              </div>
              <div class="metric-block">
                <span class="metric-label">购买量</span>
                <strong>
                  <shopping-cart-outlined />
                  {{ record.buyCount || 0 }}
                </strong>
              </div>
              <div class="metric-block sort-block">
                <span class="metric-label">排序</span>
                <a-input-number
                  v-model:value="record.sort"
                  class="sort-input"
                  :min="0"
                  size="small"
                  @blur="() => handleSortChange(record)"
                  @pressEnter="() => handleSortChange(record)"
                />
              </div>
            </div>

            <div class="item-actions">
              <div class="switch-grid">
                <label>
                  <span>上架</span>
                  <a-switch
                    :checked="record.enabled"
                    :checkedValue="true"
                    :unCheckedValue="false"
                    size="small"
                    @change="(checked) => handleFlagChange(record, 'enabled', checked)"
                  />
                </label>
                <label>
                  <span>置顶</span>
                  <a-switch
                    :checked="record.pinned"
                    :checkedValue="true"
                    :unCheckedValue="false"
                    size="small"
                    @change="(checked) => handleFlagChange(record, 'pinned', checked)"
                  />
                </label>
                <label>
                  <span>推荐</span>
                  <a-switch
                    :checked="record.recommended"
                    :checkedValue="true"
                    :unCheckedValue="false"
                    size="small"
                    @change="(checked) => handleFlagChange(record, 'recommended', checked)"
                  />
                </label>
              </div>

              <div class="action-buttons">
                <a-button type="link" size="small" @click="handleEdit(record)">
                  <template #icon><edit-outlined /></template>
                  编辑
                </a-button>
                <a-popconfirm
                  title="确定要删除该商品吗?"
                  @confirm="handleDelete(record.id)"
                >
                  <a-button type="link" danger size="small">
                    <template #icon><delete-outlined /></template>
                    删除
                  </a-button>
                </a-popconfirm>
              </div>
            </div>
          </article>
        </div>
      </a-spin>

      <div class="item-pagination">
        <a-pagination
          :current="pagination.current"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          :show-total="pagination.showTotal"
          :show-size-changer="pagination.showSizeChanger"
          :show-quick-jumper="pagination.showQuickJumper"
          :show-less-items="pagination.showLessItems"
          @change="handlePageChange"
          @showSizeChange="handlePageChange"
        />
      </div>
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
}

.filter-area {
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid #edf3ef;
  border-radius: 8px;
  background: #fbfdfc;

  .filter-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    color: v-bind('token.colorText');
    font-size: v-bind('token.fontSize');
  }

  .filter-label {
    margin-right: 2px;
    color: #31433a;
    font-weight: 600;
    white-space: nowrap;
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

.item-card-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(336px, 1fr));
  gap: 12px;
  min-width: 0;
}

.manage-item-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  overflow: hidden;
  border: 1px solid #edf2ef;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(16, 54, 34, .04);
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;

  &:hover {
    border-color: #cde7d5;
    box-shadow: 0 10px 28px rgba(16, 54, 34, .08);
    transform: translateY(-1px);
  }

  &.is-disabled {
    background: #fbfbfb;

    .cover-thumb,
    .item-card-main {
      opacity: .68;
    }
  }

  &.is-pinned {
    border-color: #ffd6d6;
  }

  &.is-recommended {
    box-shadow: 0 10px 28px rgba(250, 173, 20, .12);
  }
}

.item-card-top {
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr);
  gap: 10px;
  min-width: 0;
}

.cover-thumb {
  width: 78px;
  height: 78px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
  object-fit: cover;
}

.item-card-main {
  min-width: 0;
}

.item-title-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: start;
  min-width: 0;

  h3 {
    min-width: 0;
    margin: 0;
    overflow: hidden;
    color: #183323;
    font-size: 15px;
    font-weight: 650;
    line-height: 1.35;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .status-tag {
    margin-inline-end: 0;
  }
}

.item-meta-line {
  display: flex;
  min-width: 0;
  margin-top: 5px;
  gap: 8px;
  color: #697a72;
  font-size: 12px;

  span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    & + span::before {
      margin-right: 8px;
      color: #c2cec7;
      content: '/';
    }
  }
}

.item-delivery-line,
.item-signal-row {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 5px;

  :deep(.ant-tag) {
    margin-inline-end: 0;
    max-width: 100%;
  }
}

.item-delivery-line {
  margin-top: 8px;
}

.item-signal-row {
  min-height: 22px;
}

.item-metrics {
  display: grid;
  grid-template-columns: minmax(92px, 1.15fr) minmax(78px, .85fr) minmax(92px, .9fr);
  gap: 8px;
}

.metric-block {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f7faf8;

  .metric-label {
    color: #7b8a83;
    font-size: 12px;
    line-height: 1;
  }

  strong {
    display: flex;
    align-items: center;
    gap: 4px;
    min-width: 0;
    margin-top: 5px;
    color: #203a2b;
    font-size: 15px;
    line-height: 1.2;
  }
}

.price-block strong {
  color: #f04438;
  font-size: 18px;
}

.sort-input {
  width: 100%;

  :deep(.ant-input-number-input) {
    padding-inline: 6px;
  }
}

.item-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding-top: 2px;
  border-top: 1px solid #f0f4f2;
}

.switch-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  min-width: 0;

  label {
    display: flex;
    min-width: 0;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
    padding: 6px 8px;
    border-radius: 8px;
    background: #f7faf8;
    color: #52635b;
    font-size: 12px;
  }
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;

  :deep(.ant-btn) {
    padding-inline: 4px;
  }
}

.item-empty {
  padding: 40px 0;
  border: 1px dashed #dfe9e3;
  border-radius: 8px;
  background: #fbfdfc;
}

.item-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;

  :deep(.ant-pagination) {
    margin-bottom: 0;
  }
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
    margin-inline-end: 38px;

    .ant-btn {
      height: 32px;
      padding-inline: 12px;
    }
  }

  .add-item-button {
    min-width: 96px;
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

  .item-card-list {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }

  .manage-item-card {
    padding: 10px;
  }

  .item-card-top {
    grid-template-columns: 68px minmax(0, 1fr);
    gap: 9px;
  }

  .cover-thumb {
    width: 68px;
    height: 68px;
  }

  .item-title-row {
    gap: 6px;

    h3 {
      font-size: 14px;
    }
  }

  .item-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px;
  }

  .metric-block {
    padding: 7px 8px;

    strong {
      font-size: 13px;
    }
  }

  .price-block strong {
    font-size: 15px;
  }

  .item-actions {
    grid-template-columns: minmax(0, 1fr);
  }

  .switch-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));

    label {
      padding: 6px;
    }
  }

  .action-buttons {
    justify-content: flex-end;
  }

  .item-pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
