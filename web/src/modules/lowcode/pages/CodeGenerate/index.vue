<template>
  <a-card class="screen-card">
    <template #title>
      <navigator-bar>
        代码生成
        <a style="margin-left: 20px" @click="generate">
          <robot-filled /> {{ generating ? '生成中...' : '点击生成' }}
        </a>
      </navigator-bar>
    </template>
    <a-divider>基本配置</a-divider>
    <a-form ref="form" :label-col="{ span: 4 }" :wrapper-col="{ span : 18 }"
            :model="model">
      <a-form-item label="数据源名称" name="source" :rules="rules.source">
        <a-select :value="model.source" @update:value="setDataSource" :options="dataSources" allow-clear/>
      </a-form-item>
      <a-form-item label="包含的表" :name="['config', 'includes']" :rules="rules.includes">
        <a-select mode="multiple" v-model:value="model.config.includes" :options="tables" allow-clear :disabled="!model.source" />
      </a-form-item>
      <a-form-item label="过滤表前缀" :name="['config', 'tablePrefix']">
        <a-select mode="tags" v-model:value="model.config.tablePrefix" :options="prefixes" allow-clear />
      </a-form-item>
      <a-form-item label="过滤表后缀" :name="['config', 'tableSuffix']">
        <a-select mode="tags" v-model:value="model.config.tableSuffix" :options="suffixes" allow-clear />
      </a-form-item>
      <a-divider>包配置</a-divider>
      <a-form-item label="父包名" name="config.packageConfig.parent">
        <a-input v-model:value="model.config.packageConfig.parent" allow-clear/>
      </a-form-item>
      <a-form-item label="父包模块名" name="config.packageConfig.moduleName">
        <a-input v-model:value="model.config.packageConfig.moduleName" allow-clear/>
      </a-form-item>
      <a-form-item label="Entity 包名" name="config.packageConfig.entity">
        <a-input v-model:value="model.config.packageConfig.entity" allow-clear/>
      </a-form-item>
      <a-form-item label="Service 包名" name="config.packageConfig.service">
        <a-input v-model:value="model.config.packageConfig.service" allow-clear/>
      </a-form-item>
      <a-form-item label="Service Impl 包名" name="config.packageConfig.serviceImpl">
        <a-input v-model:value="model.config.packageConfig.serviceImpl" allow-clear/>
      </a-form-item>
      <a-form-item label="Mapper 包名" name="config.packageConfig.mapper">
        <a-input v-model:value="model.config.packageConfig.mapper" allow-clear/>
      </a-form-item>
      <a-form-item label="Mapper XML 包名" name="config.packageConfig.xml">
        <a-input v-model:value="model.config.packageConfig.xml" allow-clear/>
      </a-form-item>
      <a-form-item label="Controller 包名" name="config.packageConfig.controller">
        <a-input v-model:value="model.config.packageConfig.controller" allow-clear/>
      </a-form-item>
      <a-form-item label="自定义文件包名" name="config.packageConfig.other">
        <a-input v-model:value="model.config.packageConfig.other" allow-clear/>
      </a-form-item>
      <a-divider>生成选项</a-divider>
      <a-form-item label="作者名" name="config.author">
        <a-input v-model:value="model.config.author" allow-clear/>
      </a-form-item>
      <a-form-item label="启用Kotlin" name="config.enableKotlin">
        <a-checkbox v-model:checked="model.config.enableKotlin" />
      </a-form-item>
      <a-form-item label="启用Swagger" name="config.enableSwagger">
        <a-checkbox v-model:checked="model.config.enableSwagger" />
      </a-form-item>
      <a-form-item label="时间策略" name="config.dateType">
        <a-radio-group v-model:value="model.config.dateType" :options="dateTypes" />
      </a-form-item>
      <a-form-item label="注释日期格式" name="config.commentDate">
        <a-input v-model:value="model.config.commentDate" />
      </a-form-item>
    </a-form>
  </a-card>
</template>

<script>
import { computed, onMounted, reactive, ref } from 'vue';
import { RobotFilled } from '@ant-design/icons-vue';
import useStore from '@/modules/lowcode/store/datasource.js';
import { getTables } from '@/modules/lowcode/pages/ModelDesign/apis';
import { message as $message } from 'ant-design-vue';
import { generate } from '@/modules/lowcode/pages/CodeGenerate/apis';
import { dataSourceOptionLabel } from '@/utils/dataSource';

export default {
  name: 'CodeGenerate',
  components: { RobotFilled },
  setup() {
    const store = useStore();
    const rules = {
      source: [{ required: true, message: '必须选择数据源！' }],
      includes: [{ required: true, message: '必须选择表！' }],
    };
    const form = ref(null);
    const generating = ref(false);
    const dateTypes = [
      { value: 'ONLY_DATE', label: '只使用java.util.date代替' },
      { value: 'SQL_PACK', label: '使用java.sql包下的' },
      { value: 'TIME_PACK', label: '使用java.time包下的' }
    ]
    const prefixes = [
      { value: 'sys_', label: 'sys_' },
      { value: 't_', label: 't_' },
      { value: 't_dl_', label: 't_dl_'}
    ]
    const suffixes = [
      { value: '_history', label: '_history' },
      { value: '_ref', label: '_ref' },
      { value: '_copy', label: '_copy'}
    ]
    const model = reactive({
      source: null,
      config: {
        includes: [],
        author: 'wangyu',
        enableKotlin: false,
        enableSwagger: false,
        tablePrefix: ['t_dl_', 'sys_', 't_'],
        tableSuffix: [],
        dateType: 'ONLY_DATE',
        commentDate: 'yyyy-MM-dd',
        packageConfig: {
          parent: 'com.chinaunicom.system',
          moduleName: '',
          entity: 'domain.po',
          service: 'service',
          serviceImpl: 'service.impl',
          mapper: 'mapper',
          xml: 'mapper.mapping',
          controller: 'controller',
          other: 'front',
        }
      },
    });
    const dataSources = computed(() => store.dataSources.map(item => ({
      value: item.key,
      label: dataSourceOptionLabel(item),
    })));
    const tables = ref([]);
    onMounted(() => store.loadDataSources());
    return {
      rules,
      dateTypes,
      model,
      generating,
      dataSources,
      prefixes,
      suffixes,
      setDataSource: async key => {
        model.source = key;
        model.config.includes = [];
        if (key) {
          const result = await getTables(key);
          tables.value = result.map(item => ({ value: item.name, label: `${item.name} - ${item.comment || item.name}` }));
        } else {
          tables.value = [];
        }
      },
      tables,
      form,
      generate: async () => {
        let hide;
        try {
          await form.value.validate()
          generating.value = true;
          hide = $message.loading('正在生成代码包...', 0);
          const blob = await generate(model);
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a')
          a.href = url
          a.download = '代码.zip';
          a.click()
          window.URL.revokeObjectURL(url)
          $message.success('代码包已开始下载');
        } catch (e) {
          if (e instanceof Error) {
            $message.error(e.message);
          }
        } finally {
          hide?.();
          generating.value = false;
        }
      },
    }
  },
}
</script>

<style scoped lang="less">
.screen-card {
  :deep(.ant-card-body) {
    max-height: calc(100vh - 160px);
    overflow: auto;
  }
}
</style>
