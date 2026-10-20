<template>
  <div class="criteria-group">
    <a-checkbox :checked="!blacklist.includes(item._id)" @change="e => handleCheck(item._id, e.target.checked)" class="margin-10" />
    <a-input-group class="criteria-input" compact>
      <a-select v-model:value="item.field" :options="fields" class="table-search-field" />
      <a-select v-model:value="item.opt" :options="operations(type(item))" class="table-search-opt" />
      <table-search-value-input v-if="count(item.opt) >= 1" v-model:value="item.value" :opt="item.opt"
                                :type="type(item)"
                                class="table-search-value" />
      <table-search-value-input v-if="count(item.opt) >= 2" v-model:value="item.otherValue" :opt="item.opt"
                                :type="type(item)"
                                class="table-search-value" />
    </a-input-group>
    <a-select v-if="item.link" v-model:value="item.link" :options="links" class="table-search-opt margin-10" />
    <a-button shape="circle" type="primary" danger @click="handleRemove(item)">
      <minus-outlined />
    </a-button>
  </div>
</template>

<script>

import { memoizeOne } from '@/utils/utils';
import TableSearchValueInput from './TableSearchValueInput';
import { MinusOutlined } from '@ant-design/icons-vue';

export const links = [ { value: 'AND', label: 'and' }, { value: 'OR', label: 'or' } ];

const Operations = {
  EQ: [ '=', 1 ],
  NE: [ '!=', 1 ],
  LT: [ '<', 1 ],
  LTE: [ '<=', 1 ],
  GT: [ '>', 1 ],
  GTE: [ '>=', 1 ],
  LIKE: [ '包含', 1 ],
  NOT_LIKE: [ '不包含', 1 ],
  LIKE_LEFT: [ '开始以', 1 ],
  NOT_LIKE_LEFT: [ '开始不是以', 1 ],
  LIKE_RIGHT: [ '结束以', 1 ],
  NOT_LIKE_RIGHT: [ '结束不是以', 1 ],
  IS_NULL: [ '是null', 0 ],
  NOT_NULL: [ '不是null', 0 ],
  IS_EMPTY: [ '是空的', 0 ],
  NOT_EMPTY: [ '不是空的', 0 ],
  BETWEEN: [ '介于', 2 ],
  NOT_BETWEEN: [ '不介于', 2 ],
  IN: [ '在列表', 1 ],
  NOT_IN: [ '不在列表', 1 ],
  CUSTOM: [ '自定义', 1 ],
};

const mappedOpts = (() => {
  const meta = {
    number: [ 'IS_NULL', 'NOT_NULL', 'EQ', 'NE', 'LT', 'LTE', 'GT', 'GTE', 'BETWEEN', 'NOT_BETWEEN', 'IN', 'NOT_IN', 'CUSTOM' ],
    string: [ 'IS_NULL', 'NOT_NULL', 'EQ', 'NE', 'LIKE', 'NOT_LIKE', 'LIKE_LEFT', 'NOT_LIKE_LEFT', 'LIKE_RIGHT', 'NOT_LIKE_RIGHT',
      'IS_EMPTY', 'NOT_EMPTY', 'IN', 'NOT_IN', 'CUSTOM' ],
    date: [ 'IS_NULL', 'NOT_NULL', 'EQ', 'NE', 'LT', 'LTE', 'GT', 'GTE', 'BETWEEN', 'NOT_BETWEEN', 'CUSTOM' ],
    default: [ 'IS_NULL', 'NOT_NULL', 'EQ', 'NE', 'CUSTOM' ],
  };
  return memoizeOne(type => (meta[type] || meta.default).map(key => ({
    value: key,
    label: Operations[key][0],
  })), type => type);
})();

export const searchValueCount = type => {
  return (Operations[type] || [ '', 0 ])[1];
}

export default {
  name: 'TableSearchItem',
  components: { TableSearchValueInput, MinusOutlined },
  props: {
    item: {
      type: Object,
      default: () => ({}),
    },
    columns: {
      type: Array,
      default: () => [],
    },
    blacklist: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      Operations,
      operations: mappedOpts,
      links,
    };
  },
  computed: {
    fields() {
      return this.columns.map(({ dataIndex, title }) => ({ value: dataIndex, label: title }));
    },
  },
  methods: {
    handleRemove(key) {
      this.$emit('remove', key);
    },
    handleCheck(key, value) {
      this.$emit('check', key, value)
    },
    count: searchValueCount,
    type(item) {
      const { field } = item;
      const type = this.columns.filter(item => item.dataIndex === field)
        .map(item => item.dbType.toLowerCase()).join('');
      item.type = type;
      return type;
    },
  },
};
</script>

<style lang="less" scoped>
.margin-10 {
  margin-right: 10px;
}

.criteria-group {
  display: block;
  margin-bottom: 15px;

  .table-search-field {
    width: 140px;
  }

  .table-search-opt {
    width: 110px;
  }

  .table-search-value {
    width: 200px;
  }

  .criteria-input {
    margin-right: 10px;
    display: inline-block;
    width: auto;
    line-height: 32px;
  }
}
</style>
