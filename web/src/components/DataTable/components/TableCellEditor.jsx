import TableSearchValueInput from './TableSearchValueInput.vue';
import { reactive } from 'vue';

const status = reactive({
  oldValue: null,
  value: null,
});

const cacheValue = value => {
  if (!status.oldValue && value) {
    status.oldValue = value;
    status.value = value;
  }
};

const clearCache = () => {
  status.oldValue = null;
  status.value = null;
}

const ESC = 27;
const ENTER = 13;

/**
 * 纯函数式组件，基于一个reactive缓存对象刷新
 * @param props 静态属性
 * @returns {JSX.Element} vNode
 * @constructor 接收属性
 */
const TableCellEditor = function(props) {
  const { column, modelValue, editorRef, closeEditor, getPopupContainer, onInput, onSave } = props;
  const isPop = column.dbType === 'date' || column.dbType === 'boolean';
  // 缓存初始值
  cacheValue(modelValue.value)
  const emit = e => {
    if (status.value === status.oldValue) {
      closeEditor(e);
    } else {
      closeEditor(e);
      onInput(e, status.value)
    }
    clearCache();
  }
  // 取消逻辑，如果修改过，还原值
  const cancel = e => {
    if (e.keyCode === ESC) {
      status.value = status.oldValue;
      emit(e);
    } else if (e.keyCode === ENTER) {
      // 回车触发提交
      emit(e);
      // 事件提交
      onSave && onSave();
    }
  }
  const change = v => {
    status.value = v;
    if (isPop) {
      editorRef.value.blur();
    }
  }
  return (
    <TableSearchValueInput
      style={{ width: '100%' }}
      allowClear={false}
      rawRef={editorRef}
      type={column.dbType}
      bordered={false}
      value={status.value}
      onUpdate:value={change}
      open
      onBlur={emit}
      onKeydown={cancel}
      onClickStop={closeEditor}
      getPopupContainer={getPopupContainer}
    />
  )
}

// 阻止函数式组件被消费
TableCellEditor.inheritAttrs = false;

export default TableCellEditor;
