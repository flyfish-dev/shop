import Sortable from 'sortablejs';

/**
 * 拖拽混入
 */
export default {
  mounted() {
    const { selector } = this;
    // 初始化拖拽
    const elem = document.querySelector(selector);
    Sortable.create(elem, {
      filter: 'input',
      // 设置这个属性，防止input输入时间被prevent掉，这个属于原生事件
      preventOnFilter: false,
      onEnd: ({ newIndex, oldIndex }) => {
        const { draggedList: fields } = this;
        const current = fields.splice(oldIndex, 1)[0];
        fields.splice(newIndex, 0, current);
      },
      animation: 150,
      handle: '.ant-table-row',
      easing: 'cubic-bezier(1, 0, 0, 1)'
    });
  }
}
