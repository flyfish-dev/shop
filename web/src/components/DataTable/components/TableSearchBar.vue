<script lang='jsx'>

import TableSearchItem from '@/components/DataTable/components/TableSearchItem';
import ToolButton from '@/components/DataTable/components/ToolButton';
import { links, searchValueCount } from './TableSearchItem';
import { filterDeep } from '@/utils/utils';

// 单元
const criteria = () => ({
  _id: Date.now(),
  // 字段
  field: undefined,
  // 操作符
  opt: undefined,
  // 值1
  value: undefined,
  // 值2，用于between
  otherValue: undefined,
  // 用于支持括号
  children: [],
  // 连接符
  link: 'AND',
  // 是否group
  nested: false,
});

// 组
const criteriaGroup = () => ({
  _id: Date.now(),
  // 儿子
  children: [],
  // 连接符
  link: 'AND',
  // 是否group
  nested: true,
});

export default {
  name: 'TableSearchBar',
  components: { ToolButton, TableSearchItem },
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
    columns: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      criteria: [],
      blacklist: [],
      message: '',
    };
  },
  computed: {
    value() {
      return filterDeep(this.criteria, item => !this.blacklist.includes(item._id));
    },
  },
  methods: {
    handleAdd(context) {
      if (context?.children) {
        context.children = [ ...context.children, criteria() ];
      } else {
        this.criteria = [ ...this.criteria, criteria() ];
      }
      this.refreshLast(context);
    },
    handleRemove(item, context) {
      if (context) {
        context.children = context.children.filter(({ _id }) => item._id !== _id);
      } else {
        this.criteria = this.criteria.filter(({ _id }) => item._id !== _id);
      }
      this.refreshLast(context);
    },
    handleAddGroup(context) {
      if (context?.children) {
        context.children = [ ...context.children, criteriaGroup() ];
      } else {
        this.criteria = [ ...this.criteria, criteriaGroup() ];
      }
      this.refreshLast(context);
    },
    handleClear() {
      this.criteria = [];
      this.submit();
    },
    handleSearch() {
      this.submit();
    },
    submit() {
      if (this.validate()) {
        this.$emit('search', this.value);
      }
    },
    handleCheck(key, checked, context) {
      this.blacklist = checked ? this.blacklist.filter(item => item !== key) : [ ...this.blacklist, key ];
      this.refreshLast(context);
    },
    refreshLast(context) {
      const collection = context && context.children || this.criteria;
      const { blacklist } = this;
      if (collection.length) {
        collection.forEach((item, index) => {
          if (blacklist.includes(item._id)) {
            item.link = null;
            return;
          }
          // 判断下一个是黑名单或者是最后一个
          if (index === collection.length - 1) {
            item.link = null;
          } else {
            let next;
            // 查找下一个，如果存在非黑名单的，显示and，要么直到最后都没找见，那就置空
            while ((next = collection[++index])) {
              if (!blacklist.includes(next._id)) {
                item.link = 'AND';
                return;
              }
            }
            item.link = null;
          }
        });
      }
    },
    validate() {
      const result = this.validateInternal(this.value);
      if (!result) {
        this.message = '';
      }
      return !result;
    },
    validateInternal(values) {
      return values.some((item, index) => {
        // 为嵌套，递归判断
        if (item.nested) {
          if (!item.children || !item.children.length) {
            this.message = '嵌套查询必须包含条件！';
            return true;
          }
          return this.validateInternal(item.children);
        } else {
          if (!item.opt || !item.field) {
            this.message = '必须选择列和判断条件！';
            return true;
          }
          const count = searchValueCount(item.opt);
          if (count > 0 && !item.value || count > 1 && !item.otherValue) {
            this.message = '请输入所有需要输入的值！';
            return true;
          }
        }
        if (index !== values.length - 1 && !item.link) {
          this.message = '两个条件之间必须选择连接方式！';
          return true;
        }
        return false;
      });
    },
    reset() {
      this.criteria = [];
      this.blacklist = [];
      this.message = '';
    },
    renderItem(item, parent) {
      const { columns, handleRemove, handleAdd, handleAddGroup, handleCheck, blacklist } = this;
      const remove = () => handleRemove(item, parent);
      if (item.nested) {
        return (
          <div key={item._id} class="table-search-nested">
            <div class="table-search-nested-block">
              <a-checkbox checked={!blacklist.includes(item._id)}
                          onChange={e => handleCheck(item._id, e.target.checked, parent)}
                          class="margin-10" />
            </div>
            <div class="table-search-nested-block">
              <div>(</div>
              <div class="table-search-nested-inner">
                {item.children.map(target => this.renderItem(target, item))}
                <tool-button title="添加子条件" class="margin-10" icon="plus" shape="round" type="primary"
                             onClick={() => handleAdd(item)} />
                <tool-button title="添加子嵌套条件" class="margin-10" shape="round" type="primary"
                             onClick={() => handleAddGroup(item)}>()+
                </tool-button>
                <tool-button title="移除嵌套条件" icon="minus" shape="round" type="primary" danger onClick={remove} />
              </div>
              <div>
                )
                {item.link && <a-select onChange={link => item.link = link} value={item.link}
                                        options={links} style="margin: 8px 0 0 8px; width: 80px" />}
              </div>
            </div>
          </div>
        );
      }
      return <table-search-item key={item._id} columns={columns} blacklist={blacklist} item={item} onRemove={remove}
                                onCheck={(key, checked) => handleCheck(key, checked, parent)}
      />;
    },
  },
  render() {
    const { visible, criteria, handleAdd, handleAddGroup, handleClear, handleSearch, message } = this;
    if (visible) {
      return (
        <a-card class="table-search-bar">
          {
            criteria.length ? criteria.map(item => this.renderItem(item)) : null
          }
          <tool-button title="添加条件" class="margin-10" icon="plus" shape="round" type="primary" onClick={handleAdd} />
          <tool-button title="添加嵌套条件" class="margin-10" shape="round" type="primary" onClick={handleAddGroup}>
            ()+
          </tool-button>
          <tool-button title="查询" class="margin-10" shape="round" type="primary" icon="check"
                       onClick={handleSearch} />
          <tool-button title="重置条件" shape="round" icon="reload" onClick={handleClear} />
          {
            message ? (
              <div style="max-width: 800px; margin-top: 20px">
                <a-alert show-icon message={message} type="error" />
              </div>
            ) : null
          }
        </a-card>
      );
    }
    return null;
  },
};
</script>

<style lang="less">
.table-search-bar {
  margin-top: 20px;
  max-height: 300px;
  overflow-y: auto;

  .margin-10 {
    margin-right: 10px;
  }

  .table-search-nested {
    display: flex;
    justify-content: flex-start;
    margin-bottom: 15px;

    .table-search-nested-block {
      display: inline-block;
      position: relative;
      height: 100%;
      top: 0;

      .table-search-nested-inner {
        display: inline-block;
        padding-left: 10px;
      }
    }
  }

}
</style>
