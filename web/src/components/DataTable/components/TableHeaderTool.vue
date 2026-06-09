<script lang='jsx'>
import icons from './icons';
import { useSlots } from 'vue';

export default {
  name: 'TableHeaderTool',
  inheritAttrs: false,
  slots: ['title', 'content'],
  emits: ['click', 'update:selected'],
  props: {
    prefixClass: {
      type: String,
    },
    selected: {
      type: Boolean,
      default: false,
    },
    icon: {
      type: String,
      default: 'table',
    },
    name: {
      type: String,
    },
    menus: {
      type: Array,
    },
    current: {
      type: String
    },
    popover: {
      type: Boolean,
      default: false,
    },
    primary: {
      type: Boolean,
      default: false
    },
    danger: {
      type: Boolean,
      default: false,
    },
    confirm: {
      type: String,
      default: null,
    },
    disabled: {
      type: Boolean,
      default: false,
    }
  },
  setup(props, { emit }) {
    const slots = useSlots();
    const renderSize = () => {
      const { menus, current } = props;
      return (
        <a-menu selectedKeys={current ? [current] : []} style="min-width: 70px">
          {
            menus.map(menu => (
              <a-menu-item key={menu.key}>
                <a onClick={() => handleMenuClick(menu.key)}>{menu.name}</a>
              </a-menu-item>
            ))
          }
        </a-menu>
      )
    }

    const handleClick = () => {
      if (!props.menus && !props.popover && !props.confirm) {
        emit('click');
        emit('update:selected', !props.selected)
      }
    }

    const handleMenuClick = key => emit('click', key);

    const renderIcon = () => {
      const { icon, selected, primary, disabled, danger } = props;
      const Icon = icons[icon]
      const className = {'ant-space-header-tool': true, selected, primary, disabled, danger }
      return (
        <div class={className} onClick={handleClick}>
          {Icon ? <Icon /> : null}
        </div>
      )
    }

    const renderContent = () => {
      const { menus, popover, confirm } = props;
      return menus ? (
        <a-dropdown trigger={['click']}>
          {
            {
              overlay: renderSize,
              default: renderIcon
            }
          }
        </a-dropdown>
      ) : popover ? (
        <a-popover placement='bottomRight' trigger={['click']}>
          {
            {
              content: () => slots.content ? slots.content() : '',
              title: () => slots.title ? slots.title() : '',
              default: renderIcon
            }
          }
        </a-popover>
      ) : confirm ? (
        <a-popconfirm
          title={confirm}
          placement='bottom'
          onConfirm={() => emit('click')}>
          {renderIcon()}
        </a-popconfirm>
      ) :renderIcon();
    }
    return () => {
      const { name } = props;
      if (name) {
        return <a-tooltip title={name} placement='bottom'>{renderContent()}</a-tooltip>
      }
      return renderContent();
    }
  }
};
</script>

<style lang="less" scoped>
.ant-space-item {
  margin-right: 12px;
  transition: all;

  &:nth-last-child(1) {
    margin-right: 0;
  }
}

.ant-space-header-tool {
  margin: 0 4px;
  color: rgba(0, 0, 0, .75);
  font-size: 16px;
  cursor: pointer;

  &:hover, &.selected {
    color: #1890ff;
  }

  &.primary {
    font-weight: bold;
    color: #1677ff;
  }

  &.disabled {
    color: darkgrey;
    pointer-events: none;
  }
}
</style>

<style lang='less'>
.active {
  .ant-space-header-tool {

    &.primary {
      color: #1677ff;
    }

    &.danger {
      color: #ff8406;
    }

    &.disabled {
      color: lightgray;
      pointer-events: none;
    }
  }
}
</style>
