import { lowcodeModules } from './manifest.js';

const moduleIcons = {
  'model-design': new URL('@/assets/icons/model.png', import.meta.url),
  'code-generate': new URL('@/assets/icons/generate.png', import.meta.url),
  'online-launch': new URL('@/assets/icons/launch.png', import.meta.url),
  'integrate-test': new URL('@/assets/icons/test.png', import.meta.url)
};

export const lowcodeNavItems = lowcodeModules.map(item => ({
  ...item,
  icon: moduleIcons[item.code]
}));
