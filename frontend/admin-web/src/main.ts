import { createApp } from 'vue';
import { Alert } from 'tdesign-vue-next/es/alert';
import { Button } from 'tdesign-vue-next/es/button';
import { Drawer } from 'tdesign-vue-next/es/drawer';
import { Icon } from 'tdesign-vue-next/es/icon';
import { Input } from 'tdesign-vue-next/es/input';
import { Select } from 'tdesign-vue-next/es/select';
import { Switch } from 'tdesign-vue-next/es/switch';
import { Tag } from 'tdesign-vue-next/es/tag';
import { Tooltip } from 'tdesign-vue-next/es/tooltip';
import 'tdesign-vue-next/es/style/index.css';
import App from './App.vue';
import { router } from './app/router';
import './styles/base.css';
import './styles/admin-tokens.css';
import './styles/admin-shell.css';

createApp(App)
  .use(Alert)
  .use(Button)
  .use(Drawer)
  .use(Icon)
  .use(Input)
  .use(Select)
  .use(Switch)
  .use(Tag)
  .use(Tooltip)
  .use(router)
  .mount('#app');
