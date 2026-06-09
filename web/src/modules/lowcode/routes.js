import { defineAsyncComponent } from 'vue';
import { lowcodeModules } from './manifest.js';

const lazy = loader => defineAsyncComponent(loader);

const routeComponents = {
  'model-design': lazy(() => import('@/modules/lowcode/pages/ModelDesign')),
  'code-generate': lazy(() => import('@/modules/lowcode/pages/CodeGenerate')),
  'online-launch': lazy(() => import('@/modules/lowcode/pages/OnlineLaunch')),
  'integrate-test': lazy(() => import('@/modules/lowcode/pages/IntegrationTest')),
  'select-data-source': lazy(() => import('@/modules/lowcode/pages/ModelDesign/pages/SelectDataSource')),
  'select-data-table': lazy(() => import('@/modules/lowcode/pages/ModelDesign/pages/SelectDataTable')),
  'select-model-design': lazy(() => import('@/modules/lowcode/pages/ModelDesign/pages/SelectModelDesign')),
  'select-save-model': lazy(() => import('@/modules/lowcode/pages/ModelDesign/pages/SelectSaveModel'))
};

const bindRouteComponents = item => ({
  ...item,
  component: routeComponents[item.code],
  children: item.children?.map(bindRouteComponents)
});

const modules = lowcodeModules.map(bindRouteComponents);

export {
  modules
};
