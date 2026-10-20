<script lang='jsx'>
import icons from './icons';
import { useAttrs, useSlots } from 'vue';

export default {
  name: 'ToolButton',
  props: {
    title: {
      type: String
    },
    icon: {
      type: String
    }
  },
  emits: ['click'],
  setup(props, { emit }) {
    const handleClick = () => emit('click');
    const attrs = useAttrs();
    const slots = useSlots();
    return () => {
      const Icon = icons[props.icon]
      return (
        <a-tooltip title={props.title}>
          <a-button { ...attrs } onClick={handleClick}>
            { Icon ? <Icon /> : null }
            { slots.default ? slots.default() : null }
          </a-button>
        </a-tooltip>
      );
    };
  }
};


</script>

<style scoped>

</style>
