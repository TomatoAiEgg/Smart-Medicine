import { createApp } from 'vue';
import { Alert } from 'tdesign-vue-next/es/alert';
import { Button } from 'tdesign-vue-next/es/button';
import { Tag } from 'tdesign-vue-next/es/tag';
import App from './App.vue';
import { router } from './app/router';
import './styles/base.css';

createApp(App).use(Alert).use(Button).use(Tag).use(router).mount('#app');
