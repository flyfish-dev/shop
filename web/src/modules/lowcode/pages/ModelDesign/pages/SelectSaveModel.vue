<template>
  <div class="select-save-model">
    <a-result :status="status" :title="tips">
      <template #subTitle>
        <pre>{{msg}}</pre>
      </template>
      <template #extra>
        <a-button v-if="status === 'info'" key="sync" type="primary" @click="save" :loading="loading">
          <template #icon><cloud-sync-outlined /></template>
          同步数据库
        </a-button>
        <a-button v-else-if="status === 'error'" key="retry" type="primary" danger @click="save" :loading="loading">
          <template #icon><reload-outlined /></template>
          重新尝试
        </a-button>
        <a-button v-else-if="status === 'success'" key="continue" type="primary" @click="work">
          <template #icon><rocket-filled /></template>
          继续工作
        </a-button>
        <a-button key="back" @click="back">
          <template #icon><rollback-outlined /></template>
          重新编辑
        </a-button>
      </template>
    </a-result>
  </div>
</template>

<script>
import { Modal } from 'ant-design-vue';
import { saveTable } from '@/modules/lowcode/pages/ModelDesign/apis';
import { computed, inject, ref } from 'vue';
import { CloudSyncOutlined, RollbackOutlined, RocketFilled, ReloadOutlined } from '@ant-design/icons-vue';

export default {
  name: 'SelectSaveModel',
  components: {
    CloudSyncOutlined,
    RollbackOutlined,
    RocketFilled,
    ReloadOutlined,
  },
  setup() {
    const dataSource = inject('dataSource')
    const model = inject('model')
    const setCurrent = inject('setCurrent');
    const setDataSource = inject('setDataSource');
    const setTable = inject('setTable');
    const status = inject('status');
    const loadModel = inject('loadModel');
    const msg = inject('info');
    const loading = ref(false);

    // 询问并持久化
    const confirm = async () => {
      return new Promise((resolve, reject) => {
        Modal.confirm({
          title: '确认操作',
          content: '即将提交到数据库并持久化修改，是否确认？',
          okText: '同步数据库',
          cancelText: '先不了',
          onOk: close => {
            resolve();
            close();
          },
          onCancel: close => {
            close();
            reject('用户取消');
          },
        })
      })
    }
    return {
      loading,
      status,
      msg,
      tips: computed(() => {
        switch (status.value) {
          case 'success':
            return '您的修改已经保存到数据库。'
          case 'error':
            return '好像出了点问题，不要紧，好好检查一下'
          case 'info':
          default:
            return '恭喜，您即将完成本次建模任务！请确认信息。'
        }
      }),
      back: () => setCurrent(2),
      work: () => setDataSource({ key: dataSource.value }),
      save: async () => {
        loading.value = true;
        try {
          await confirm();
          const { name, oldName, comment, fields: columns, indexes, related } = model.value;
          await saveTable(dataSource.value, { name, oldName, comment, columns, indexes, related });
          status.value = 'success';
          msg.value = '';
          setTable(name);
          await loadModel(model.value);
        } catch (e) {
          if (e !== '用户取消') {
            status.value = 'error';
            msg.value = e.message;
          }
        } finally {
          loading.value = false;
        }
      }
    }
  }
}
</script>

<style scoped>
.msg {
  max-height: 700px;
  overflow: auto;
}
</style>
