import { createApp } from 'vue';
import { createPinia } from 'pinia';
import piniaPluginPersistedState from 'pinia-plugin-persistedstate';
import Entry from './App.vue';
import 'ant-design-vue/dist/reset.css';
import '@surely-vue/table/dist/index.less';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import {
  Affix,
  Alert,
  App,
  Upload,
  Avatar,
  Badge,
  Button,
  Card,
  Carousel,
  Checkbox,
  Col,
  ConfigProvider,
  DatePicker,
  Descriptions,
  Divider,
  Drawer,
  Dropdown,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Menu,
  message,
  Modal,
  PageHeader,
  Pagination,
  Popconfirm,
  Popover,
  Radio,
  Result,
  Row,
  Select,
  Space,
  Spin,
  Steps,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  Tree
} from 'ant-design-vue';

import STable, { setLicenseKey } from '@surely-vue/table';

import { get, post } from '@/network/request';
import NavigatorBar from '@/components/NavigatorBar';
import router from '@/router';
import license from '@/license';

dayjs.locale('zh-cn');
setLicenseKey(license);

const app = createApp(Entry)
  .use(App)
  .use(Form)
  .use(Affix)
  .use(Alert)
  .use(Avatar)
  .use(Badge)
  .use(Button)
  .use(Card)
  .use(ConfigProvider)
  .use(Carousel)
  .use(Descriptions)
  .use(Spin)
  .use(Space)
  .use(Table)
  .use(Tree)
  .use(Divider)
  .use(DatePicker)
  .use(Popover)
  .use(Select)
  .use(Checkbox)
  .use(Modal)
  .use(Menu)
  .use(Tabs)
  .use(List)
  .use(Steps)
  .use(Statistic)
  .use(Switch)
  .use(Row)
  .use(Col)
  .use(Dropdown)
  .use(Empty)
  .use(Pagination)
  .use(Popconfirm)
  .use(Drawer)
  .use(Input)
  .use(Radio)
  .use(InputNumber)
  .use(Result)
  .use(Tooltip)
  .use(Typography)
  .use(Tag)
  .use(STable)
  .use(Upload)
  .use(PageHeader);

// 使用pinia
const pinia = createPinia();
pinia.use(piniaPluginPersistedState);
app.use(pinia);

app.use(router);

Object.assign(app.config.globalProperties, {
  $message: message,
  $post: post,
  $get: get
});
app.component('navigator-bar', NavigatorBar);
app.mount('#app');
