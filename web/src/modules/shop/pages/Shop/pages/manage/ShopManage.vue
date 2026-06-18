<script setup>
import { ref, onMounted } from 'vue';
import { getCurrentShop } from '../../apis/api';
import { updateShop, uploadImage } from '../../apis/manage';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';

const loading = ref(false);
const editing = ref(false);
const editingShop = ref({
  name: '',
  description: '',
  avatar: ''
});
const avatarFileList = ref([]);
const form = ref(null);

const loadData = async () => {
  loading.value = true;
  try {
    const data = await getCurrentShop();
    editingShop.value = data;
    avatarFileList.value = data.avatar ? [{
        uid: '-1',
        name: 'avatar',
        status: 'done',
        url: data.avatar
      }] : [];
  } catch (e) {
    message.error('加载失败');
  } finally {
    loading.value = false;
  }
};

const handleEdit = () => {
  editing.value = true;
};

const handleSave = () => {
  form.value?.validate().then(async () => {
    try {
      let avatar = editingShop.value.avatar;
      const file = avatarFileList.value?.[0];
      if (file?.originFileObj) {
        const data = new FormData();
        data.append('file', file.originFileObj);
        avatar = await uploadImage(data);
      } else if (file?.url) {
        avatar = file.url;
      }
      await updateShop(editingShop.value.id, {
        name: editingShop.value.name,
        description: editingShop.value.description,
        avatar
      });
      message.success('更新成功');
      editing.value = false;
      await loadData();
    } catch (e) {
      message.error(e.message || '操作失败');
    }
  });
};

const handleCancel = () => {
  editing.value = false;
  loadData();
};

onMounted(loadData);
</script>

<template>
  <div class="shop-manage">
    <a-card :loading="loading">
      <template #title>
        <span>店铺管理</span>
      </template>
      <template #extra>
        <a-space>
          <template v-if="editing">
            <a-button @click="handleCancel">取消</a-button>
            <a-button type="primary" @click="handleSave">保存</a-button>
          </template>
          <a-button v-else type="primary" @click="handleEdit">
            编辑店铺
          </a-button>
        </a-space>
      </template>

      <div v-if="!editing" class="shop-info">
        <a-descriptions bordered>
          <a-descriptions-item label="店铺头像" :span="3">
            <a-avatar
              :size="64"
              :src="editingShop.avatar"
              class="shop-avatar"
            >
              {{ editingShop.name?.charAt(0)?.toUpperCase() }}
            </a-avatar>
          </a-descriptions-item>
          <a-descriptions-item label="店铺名称" :span="3">
            {{ editingShop.name }}
          </a-descriptions-item>
          <a-descriptions-item label="店铺描述" :span="3">
            {{ editingShop.description || '暂无描述' }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间" :span="3">
            {{ editingShop.createTime || '--' }}
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <a-form
        v-else
        ref="form"
        :model="editingShop"
        :rules="{
          name: [{ required: true, message: '请输入店铺名称' }]
        }"
        layout="vertical"
        class="shop-form"
      >
        <a-form-item label="店铺头像" name="avatar" class="avatar-form-item">
          <a-upload
            v-model:file-list="avatarFileList"
            list-type="picture-card"
            :max-count="1"
            :before-upload="() => false"
            accept="image/*"
            class="avatar-uploader"
          >
            <div v-if="!avatarFileList?.length">
              <plus-outlined />
              <div style="margin-top: 8px">上传头像</div>
            </div>
          </a-upload>
        </a-form-item>

        <a-form-item label="店铺名称" name="name">
          <a-input v-model:value="editingShop.name" />
        </a-form-item>

        <a-form-item label="店铺描述" name="description">
          <a-textarea
            v-model:value="editingShop.description"
            :auto-size="{ minRows: 4, maxRows: 6 }"
          />
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped lang="less">
.shop-manage {
  padding: 20px;
  min-width: 0;

  .shop-info {
    width: 560px;
    max-width: 100%;
    margin: 0 auto;
    padding: 24px 0;

    .shop-avatar {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      background-color: #14d78c;
      margin: 0 auto;
    }
  }

  .shop-form {
    padding: 24px 0;
    max-width: 560px;
    margin: 0 auto;

    .avatar-form-item {
      text-align: center;
    }
  }

  :deep(.avatar-uploader) {
    .ant-upload {
      width: 128px;
      height: 128px;
      margin: 0 auto;
    }

    .ant-upload-select {
      margin: 0;
    }

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  :deep(.ant-descriptions) {
    background: #fff;

    .ant-descriptions-item-label {
      width: 120px;
    }
  }

  :deep(.ant-form-item) {
    margin-bottom: 24px;
  }

  :deep(.ant-card-head) {
    border-bottom: 1px solid #f0f0f0;
    margin-bottom: 0;
  }

  :deep(.ant-card-body) {
    padding: 24px;
  }
}

@media only screen and (max-width: 640px) {
  .shop-manage {
    padding: 0;

    .shop-info {
      width: 100%;
      padding: 12px 0;
    }

    .shop-form {
      padding: 12px 0;
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
    }

    :deep(.ant-card-body) {
      padding: 12px;
    }

    :deep(.ant-descriptions-view) {
      overflow-x: auto;
    }

    :deep(.ant-form-item) {
      margin-bottom: 18px;
    }
  }
}
</style>
