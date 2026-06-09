<template>
  <a :href="to" @click="go"><slot></slot></a>
</template>

<script>
import { useRouter } from '@/router/use';
import { computed, toRefs } from 'vue';

export default {
  name: 'RouterLink',
  props: {
    href: {
      type: String,
      default: '/'
    }
  },
  setup(props) {
    const { href } = toRefs(props);
    const router = useRouter();
    const external = computed(() => /^https?:\/\//.test(href.value));
    const to = computed(() => external.value ? href.value : router.resolve(href.value).fullPath);
    return {
      to,
      go: event => {
        if (external.value || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) {
          return;
        }
        event.preventDefault();
        router.push(href.value);
      },
    }
  }
}
</script>

<style scoped>
a {
  cursor: pointer;
}
</style>
