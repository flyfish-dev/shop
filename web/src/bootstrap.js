import { createApp } from 'vue';
import { createPinia } from 'pinia';
import piniaPluginPersistedState from 'pinia-plugin-persistedstate';
import Entry from './App.vue';
import 'ant-design-vue/dist/reset.css';
import 'dayjs/locale/zh-cn';
import 'dayjs/locale/en';
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
  Progress,
  Radio,
  Result,
  Row,
  Segmented,
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

import { get, post } from '@/network/request';
import NavigatorBar from '@/components/NavigatorBar';
import router from '@/router';
import { installI18n } from '@/i18n/index.js';

export function createFlyfishApp() {
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
    .use(Progress)
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
    .use(Segmented)
    .use(InputNumber)
    .use(Result)
    .use(Tooltip)
    .use(Typography)
    .use(Tag)
    .use(Upload)
    .use(PageHeader);

  // 使用pinia
  installI18n(app);

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
  return app;
}
