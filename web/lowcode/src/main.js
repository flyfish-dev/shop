import STable, { setLicenseKey } from '@surely-vue/table';
import '@surely-vue/table/dist/index.less';
import { createFlyfishApp } from '@/bootstrap.js';
import license from './license.js';
import logo from '@/assets/logo.png';

setLicenseKey(license);
const icon = document.createElement('link');
icon.rel = 'icon';
icon.href = logo;
document.head.append(icon);
createFlyfishApp().use(STable).mount('#app');
