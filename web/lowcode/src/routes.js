import { defineAsyncComponent } from 'vue';
import { lowcodeModules } from './manifest.js';

const lazy = loader => defineAsyncComponent(loader);

const routeComponents = {
  'model-design': lazy(() => import('@lowcode/pages/ModelDesign')),
  'code-generate': lazy(() => import('@lowcode/pages/CodeGenerate')),
  'online-launch': lazy(() => import('@lowcode/pages/OnlineLaunch')),
  'integrate-test': lazy(() => import('@lowcode/pages/IntegrationTest')),
  'select-data-source': lazy(() => import('@lowcode/pages/ModelDesign/pages/SelectDataSource')),
  'select-data-table': lazy(() => import('@lowcode/pages/ModelDesign/pages/SelectDataTable')),
  'select-model-design': lazy(() => import('@lowcode/pages/ModelDesign/pages/SelectModelDesign')),
  'select-save-model': lazy(() => import('@lowcode/pages/ModelDesign/pages/SelectSaveModel'))
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
