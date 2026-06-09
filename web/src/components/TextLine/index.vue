<script setup>
import { nextTick, onMounted, ref } from 'vue';

const content = ref();

const calculateWidth = () => {
  const node = content.value;
  const { width: contentWidth } = node.getBoundingClientRect();
  const { width: parentWidth } = node.parentElement.getBoundingClientRect();
  const calculated = (parentWidth - contentWidth) / 2 - 10;
  node.parentElement.style.setProperty('--line-width', `${calculated}px`);
};

nextTick(() => calculateWidth());

onMounted(() => {
  calculateWidth();
});
</script>

<template>
  <div class='text-line'>
    <div class='content'>
      <span ref='content'><slot /></span>
    </div>
  </div>
</template>

<style scoped lang='less'>
.text-line {
  position: relative;
  line-height: 45px;
  text-align: center;
  font-size: 14px;
  min-width: 160px;
  margin: 0 auto;

  .content:before, .content:after {
    position: absolute;
    top: 50%;
    width: var(--line-width, 50%);
    height: 1px;
    background: #9d9d9d;
    content: '';
  }

  /* 调整背景横线的左右距离 */
  .content::before {
    left: 0;
  }

  .content::after {
    right: 0;
  }
}
</style>
