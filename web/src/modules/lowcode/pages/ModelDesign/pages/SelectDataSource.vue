<template>
  <div class="select-data-source">
    <h2>
      数据源
      <a class="opt-btn" @click="add"><plus-outlined/> 添加</a>
      <a class="opt-btn" style="position: relative">
        <import-outlined/> 导入
        <input type="file" @change="from" />
      </a>
      <a class="opt-btn" @click="to"><export-outlined/> 导出</a>
    </h2>
    <a-list item-layout="horizontal" :data-source="dataSources">
      <template #renderItem="{ item }">
        <a-list-item :key="item.key" class="list-item" :class="{selected: dataSource === item.key}"
                     @click="setDataSource(item)">
          <div class="float-btn-group">
            <a-button @click.stop="edit(item)" type="primary" shape="circle">
              <template #icon><edit-filled /></template>
            </a-button>
            <a-popconfirm ok-text="删除" ok-type="danger" cancel-text="取消" @confirm="remove(item)">
              <template #title>
                <h4>删除 {{item.name}}？</h4>
              </template>
              <a-button @click.stop type="primary" danger shape="circle">
                <template #icon><delete-filled /></template>
              </a-button>
            </a-popconfirm>
          </div>
          <a-list-item-meta :description="sourceAddress(item)">
            <template #title><a>{{ item.name }}</a></template>
            <template #avatar>
              <a-avatar style="background-color: #1890ff">
                <template #icon>
                  <database-filled />
                </template>
              </a-avatar>
            </template>
          </a-list-item-meta>
        </a-list-item>
      </template>
    </a-list>
    <a-drawer v-model:open="visible" width="800" root-class-name="datasource-drawer">
      <template #title>
        数据源维护
        <a style="float: right" @click="save"><save-outlined/> 保存</a>
      </template>
      <a-form ref="form" v-if="properties" :rules="rules" :label-col="{ span: 4 }" :wrapper-col="{ span : 18 }"
              :model="properties">
        <a-form-item label="数据源名称" name="name">
          <a-input v-model:value="properties.name" allow-clear />
        </a-form-item>
        <a-form-item label="地址" name="host">
          <a-input v-model:value="properties.host" placeholder="如 127.0.0.1 或 mysql.example.com" allow-clear />
        </a-form-item>
        <a-form-item label="端口" name="port">
          <a-input-number v-model:value="properties.port" :min="1" :max="65535" style="width: 100%" />
        </a-form-item>
        <a-form-item label="数据库名" name="databaseName">
          <a-input v-model:value="properties.databaseName" allow-clear />
        </a-form-item>
        <a-form-item label="账号" name="username">
          <a-input v-model:value="properties.username" allow-clear />
        </a-form-item>
        <a-form-item label="密码" name="password">
          <a-input type="password" v-model:value="properties.password" allow-clear />
        </a-form-item>
        <a-form-item label="参数" name="params">
          <template #extra>
            <p style="margin-top: 10px">可选，示例：<a @click="properties.params = exampleParams">填入</a></p>
            <p style="line-break: anywhere">{{exampleParams}}</p>
          </template>
          <a-textarea :rows="2" v-model:value="properties.params" placeholder="serverZoneId=Asia/Shanghai" allow-clear />
        </a-form-item>
      </a-form>
    </a-drawer>
  </div>
</template>

<script>
import useStore from '@/modules/lowcode/store/datasource.js'
import { DatabaseFilled, PlusOutlined, ExportOutlined, ImportOutlined, SaveOutlined, EditFilled, DeleteFilled } from '@ant-design/icons-vue';
import { inject, ref, computed } from 'vue';
import { message as $message } from 'ant-design-vue';
import { dataSourceAddress, defaultDataSource, normalizeDataSourceDraft } from '@/utils/dataSource';

const exampleParams = 'serverZoneId=Asia/Shanghai';

export default {
  name: 'SelectDataSource',
  components: {
    DatabaseFilled,
    PlusOutlined,
    ExportOutlined,
    ImportOutlined,
    SaveOutlined,
    EditFilled,
    DeleteFilled,
  },
  setup() {
    const store = useStore();
    const setDataSource = inject('setDataSource')
    const dataSource = inject('dataSource')
    const dataSources = inject('dataSources')
    const properties = ref(null)
    const form = ref();
    const visible = computed({
      get: () => !!properties.value,
      set: e => {
        if (!e) {
          properties.value = null;
        }
      }
    })
    // 读取文件
    const readAsText = async file => {
      return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.readAsText(file);
        reader.onload = () => resolve(reader.result);
        reader.onerror = error => reject(error);
      });
    }
    const add = () => {
      properties.value = defaultDataSource();
    }
    const from = async e => {
      const [ file ] = e.target.files;
      if (file) {
        const close = $message.loading('正在导入数据源...', 0);
        try {
          const text = await readAsText(file);
          const value = JSON.parse(text);
          const sources = Array.isArray(value) ? value : Object.values(value);
          for (const source of sources) {
            await store.setSource(source);
          }
          await store.loadDataSources();
          $message.success(`已导入 ${sources.length} 个数据源`);
        } catch (e) {
          $message.error('读取文件异常，不是json文件！' + e.message);
        } finally {
          close();
          e.target.value = '';
        }
      }
    }
    const to = () => {
      const sources = store.source;
      const blob = new Blob([JSON.stringify(sources)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a')
      a.href = url
      a.download = 'sources.json';
      a.click()
      window.URL.revokeObjectURL(url)
    }
    const save = async () => {
      const close = $message.loading('正在保存和校验...', 0);
      try {
        const value = normalizeDataSourceDraft(properties.value);
        await form.value.validate()
        await store.setSource(value)
        properties.value = null;
      } catch (e) {
        console.log(e)
        if (e instanceof Error) {
          $message.error('数据源校验失败！原因：' + e.message);
        }
      } finally {
        close();
      }
    }
    const rules = {
      name: [{ required: true, message: '请输入数据源名称！' }],
      host: [{ required: true, message: '请输入地址！' }],
      port: [{ required: true, type: 'number', message: '请输入端口！' }],
      databaseName: [{ required: true, message: '请输入数据库名！' }],
      username: [{ required: true, message: '请输入账号！' }],
      password: [{ required: true, message: '请输入密码！' }],
    }
    return {
      dataSources,
      setDataSource,
      dataSource,
      properties,
      visible,
      rules,
      form,
      exampleParams,
      sourceAddress: dataSourceAddress,
      add,
      remove: item => store.removeSource({ key: item.key }),
      edit: item => properties.value = normalizeDataSourceDraft(item),
      from,
      to,
      save,
    }
  },
}
</script>

<style scoped lang="less">
.select-data-source {
  margin: 40px auto;
  input[type="file"] {
    cursor: pointer;
    opacity: 0;
    position: absolute;
    width: 75px;
    max-height: 30px;
    left: 0;
    top: 0;
  }
  :deep(.ant-list-item) {
    overflow: hidden;
    padding-left: 15px;
    padding-right: 15px;
    position: relative;
    .float-btn-group {
      position: absolute;
      right: 20px;
      top: 13px;
      display: none;
      button:last-child {
        margin-left: 10px;
      }
    }
    &:hover {
      background-color: #e6f4ff;
      cursor: pointer;
      .float-btn-group {
        display: block;
      }
    }
  }
  .opt-btn {
    margin-left: 20px;
  }
  .list-item {
    transition: 0.5s background-color ease;
    &.selected {
      background-color: #a1d5fa;
    }
  }
}
</style>
