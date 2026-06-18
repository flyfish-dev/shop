<template>
  <a-spin :spinning="loading">
    <div class="select-model-design">
      <h2 class="model-header">数据表：
        <input v-model="model.name"/>
        <a-divider type="vertical"/>
        表备注：
        <input class="comment" v-model="model.comment"/>
        <a class="save" @click="save">
          <save-filled/>
          保存
        </a>
      </h2>
      <h2 class="tabs">
        <a-space>
          <a class="opt-btn tab-btn" @click="setView(tab)" :class="{active: view === tab.view}" :key="tab.view"
             v-for="tab in tabs">
            {{ tab.name }}
          </a>
        </a-space>
      </h2>
      <field-manage v-show="view === 'fields'" :model="model"/>
      <index-manage v-show="view === 'indexes'" :model="model"/>
      <a-textarea v-show="view === 'preview'" :rows="20" :value="sql" />
    </div>
  </a-spin>
</template>

<script>
import { computed, inject, onMounted, ref, watch } from 'vue';
import FieldManage from '@/modules/lowcode/pages/ModelDesign/pages/components/FieldManage';
import IndexManage from '@/modules/lowcode/pages/ModelDesign/pages/components/IndexManage';
import { SaveFilled } from '@ant-design/icons-vue';
import { message as $message } from 'ant-design-vue';
import { useRouter } from '@/router/use';

const tabs = [
  {
    name: '字段管理',
    view: 'fields',
  },
  {
    name: '索引管理',
    view: 'indexes',
  },
  {
    name: 'SQL预览',
    view: 'preview'
  }
];


const modelProvider = () => ({
  name: '',
  comment: '',
  fields: [],
  indexes: [],
  related: [],
});

export default {
  name: 'SelectModelDesign',
  components: {
    FieldManage,
    IndexManage,
    SaveFilled
  },
  setup() {
    const router = useRouter();
    const model = ref(modelProvider());
    const loading = ref(false);
    const view = ref('fields');
    const sql = ref('');

    const testModel = inject('testModel')
    const loadModel = inject('loadModel')
    const parentModel = inject('model')
    const setModel = inject('setModel')

    const table = inject('table')
    const status = inject('status')
    const info = inject('info')

    // 加载数据表详情
    const loadTableDetail = async () => {
      const tableName = table.value;
      view.value = 'fields';
      if (!tableName) {
        // 不存在表名，证明是新建，此时重置状态
        model.value = modelProvider();
        return;
      }
      loading.value = true;
      try {
        // 已经确认过的模型，予以缓存
        if (parentModel.value?._confirm) {
          model.value = parentModel.value;
          return;
        }
        // 加载模型
        await loadModel(model.value);
      } finally {
        loading.value = false;
      }
    }
    // 监听报表的变化，及时变化
    watch(() => table.value, () => loadTableDetail())
    // 初次进入，刷新数据
    onMounted(async () => loadTableDetail())
    const validations = {
      fields: [
        { f: value => value.fields.some(field => !field.name), msg: '请确保列名都已经填写！' },
        { f: value => value.fields.some(field => field.length > 65535 && !field.type.endsWith('text')), msg: '请填写小于65535的长度！' },
      ],
      indexes: [
        { f: value => value.indexes.some(index => !index.name), msg: '请确保索引名都已经填写！' },
        { f: value => value.indexes.some(index => !index.fields?.length), msg: '索引请务必指定字段！' },
      ],
      // 基础校验
      validateBase() {
        const value = model.value;
        if (!value.fields.length) return '请至少添加一个字段！';
        if (!value.name) return '请指定表名！';
        if (!value.comment) return '请指定表备注！';
      },
      validate(data, list) {
        const res = list.some(({ f, msg }) => {
          const error = f(data);
          if (error) {
            $message.error(msg);
          }
          return error;
        });
        return !res;
      }
    }
    // 校验逻辑
    const validate = all => {
      const value = model.value;
      if (all) {
        const baseErr = validations.validateBase();
        if (baseErr) {
          $message.error(baseErr);
          return false;
        }
        return value.fields.length && validations.validate(value, validations.fields) &&
            validations.validate(value, validations.indexes);
      }
      switch (view.value) {
        case 'fields':
          return validations.validate(value, validations.fields)
        case 'indexes':
          return validations.validate(value, validations.indexes)
        default:
          return true;
      }
    };
    return {
      view,
      tabs,
      model,
      loading,
      sql,
      setView: async tab => {
        // 校验
        if (!validate()) return;
        view.value = tab.view;
        if (tab.view === 'preview') {
          try {
            const { sql: value } = await testModel(model.value);
            sql.value = value || '尚未做出任何改变';
          } catch (e) {
            sql.value = e.message;
          }
        }
      },
      save: async () => {
        if (!validate(true)) {
          return;
        }
        // 测试模型
        const hide = $message.loading('正在检测中...')
        try {
          const { sql: value, desc } = await testModel(model.value);
          if (value) {
            // 更新数据缓存，到达下一步
            model.value._confirm = true;
            // 重置保存视图状态
            status.value = 'info';
            // 设置模型
            setModel(model.value)
            // 设置信息
            info.value = desc;
          } else {
            $message.info('尚未做出任何修改，不需要保存。')
          }
        } catch (e) {
          $message.error('检测失败，请检查！' + e)
        } finally {
          hide()
        }
      }
    };
  },
}
</script>

<style scoped lang="less">
.select-model-design {
  margin: 40px auto;

  .model-header {
    input {
      outline: none;
      border: none;
      box-shadow: none;
      border-bottom: 1px solid darkslategray;
      transition: 0.5s border-bottom ease;
      font-size: 20px;
      width: 250px;

      &:focus, &:hover {
        border-bottom: 1px solid #1890ff;
      }

      &.comment {
        width: 450px;
      }
    }

    .save {
      margin-left: 25px;
    }
  }

  .opt-btn {
    margin-left: 20px;

    &.red {
      color: red;
    }
  }

  // tab条
  .tabs {
    text-align: center;
    margin-top: 20px;
    margin-bottom: 20px;
    // tab条项目按钮
    .tab-btn {
      margin: 0 15px;
      color: darkslategray;
      transition: 0.4s all ease;
      font-size: 19px;

      &.active {
        color: #1890ff;
        border-bottom: 2px solid #1890ff;
      }

      &:hover {
        opacity: 0.5;
      }
    }
  }

  .table-list {
    margin-top: 30px;

    .list-item {
      overflow: hidden;
      transition: 0.5s background-color ease;

      :deep(.ant-card-body) {
        padding: 15px;

        .ant-card-meta-title {
          font-size: 13px;
        }

        .ant-card-meta-description {
          font-size: 12px;
        }
      }

      &:hover {
        background-color: #e6f4ff;
        cursor: pointer;
      }

      &.selected {
        background-color: #a1d5fa;
      }
    }
  }

}
</style>
