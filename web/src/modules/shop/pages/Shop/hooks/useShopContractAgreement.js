import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import { message } from 'ant-design-vue';
import { getItemContracts, signItemContractFile } from '../apis/api.js';

const viewerBase = '/file-viewer/index.html';

export function useShopContractAgreement({ item, user, store, router }) {
  const visible = ref(false);
  const loading = ref(false);
  const signing = ref(false);
  const contracts = ref([]);
  const activeIndex = ref(0);
  const signToken = ref('');
  const agreedFileIds = ref(new Set());
  const readPercent = ref(0);
  const readToEnd = ref(false);
  const agreedCurrent = ref(false);
  let resolver = null;
  let cleanupViewerFrame = null;

  const files = computed(() => contracts.value.flatMap(contract => (contract.files || []).map(file => ({
    ...file,
    contractId: contract.id,
    contractName: contract.name,
    contractTypeName: contract.typeName
  }))));
  const currentFile = computed(() => files.value[activeIndex.value] || null);
  const totalCount = computed(() => files.value.length);
  const agreedCount = computed(() => agreedFileIds.value.size);
  const completed = computed(() => totalCount.value > 0 && agreedCount.value >= totalCount.value);
  const activeKey = computed({
    get: () => String(activeIndex.value),
    set: value => setActiveIndex(Number(value))
  });

  const loadContracts = async () => {
    if (!item.value?.id) {
      contracts.value = [];
      return [];
    }
    loading.value = true;
    try {
      const data = await getItemContracts(item.value.id);
      contracts.value = data || [];
      return contracts.value;
    } finally {
      loading.value = false;
    }
  };

  const resetReadState = () => {
    readPercent.value = 0;
    readToEnd.value = false;
    agreedCurrent.value = false;
    cleanupBoundViewerFrame();
  };

  const setActiveIndex = index => {
    if (index < 0 || index >= totalCount.value) {
      return;
    }
    activeIndex.value = index;
    resetReadState();
  };

  const ensureContractAgreement = async () => {
    if (!item.value?.contractRequired) {
      return {};
    }
    if (!user.value?.id) {
      store.rememberRedirect(location.pathname + location.search);
      router.push('/login');
      return null;
    }
    const data = await loadContracts();
    const needSign = data.some(contract => contract.files?.length);
    if (!needSign) {
      return {};
    }
    activeIndex.value = 0;
    signToken.value = '';
    agreedFileIds.value = new Set();
    resetReadState();
    visible.value = true;
    await nextTick();
    return new Promise(resolve => {
      resolver = resolve;
    });
  };

  const cancelAgreement = () => {
    visible.value = false;
    cleanupBoundViewerFrame();
    if (resolver) {
      resolver(null);
      resolver = null;
    }
  };

  const viewerUrl = file => {
    if (!file?.fileUrl) {
      return '';
    }
    const options = encodeURIComponent(JSON.stringify({
      toolbar: {
        download: true,
        print: true,
        exportHtml: false
      }
    }));
    return `${viewerBase}?url=${encodeURIComponent(file.fileUrl)}&options=${options}`;
  };

  const updateReadProgress = target => {
    if (!target) {
      return;
    }
    const scrollable = Math.max(target.scrollHeight - target.clientHeight, 1);
    const percent = Math.min(100, Math.round((target.scrollTop / scrollable) * 100));
    readPercent.value = percent;
    readToEnd.value = percent >= 98 || target.scrollHeight <= target.clientHeight + 4;
  };

  const handlePreviewScroll = event => updateReadProgress(event.target);

  const cleanupBoundViewerFrame = () => {
    if (cleanupViewerFrame) {
      cleanupViewerFrame();
      cleanupViewerFrame = null;
    }
  };

  const resolveViewerScrollTarget = doc => {
    const root = doc.scrollingElement || doc.documentElement || doc.body;
    const candidates = [
      root,
      doc.documentElement,
      doc.body,
      ...Array.from(doc.querySelectorAll('main, [class*="viewer"], [class*="scroll"], [style*="overflow"]')).slice(0, 80)
    ].filter(Boolean);
    return candidates.reduce((current, candidate) => {
      const currentScrollable = Math.max((current?.scrollHeight || 0) - (current?.clientHeight || 0), 0);
      const candidateScrollable = Math.max((candidate.scrollHeight || 0) - (candidate.clientHeight || 0), 0);
      return candidateScrollable > currentScrollable ? candidate : current;
    }, root);
  };

  const handleViewerFrameLoad = event => {
    cleanupBoundViewerFrame();
    const frame = event?.target;
    let win = null;
    let doc = null;
    try {
      win = frame?.contentWindow;
      doc = frame?.contentDocument;
    } catch {
      return;
    }
    if (!win || !doc) {
      return;
    }

    let scrollTarget = null;
    let targetCleanup = null;
    let resizeObserver = null;
    let mutationObserver = null;
    let stopped = false;

    const update = () => {
      if (stopped) {
        return;
      }
      const target = scrollTarget || resolveViewerScrollTarget(doc);
      updateReadProgress(target);
    };

    const bindScrollTarget = () => {
      if (stopped) {
        return;
      }
      const nextTarget = resolveViewerScrollTarget(doc);
      if (nextTarget === scrollTarget) {
        update();
        return;
      }
      if (targetCleanup) {
        targetCleanup();
      }
      const boundTarget = nextTarget;
      scrollTarget = boundTarget;
      boundTarget?.addEventListener('scroll', update, { passive: true });
      targetCleanup = () => boundTarget?.removeEventListener('scroll', update);
      update();
    };

    win.addEventListener('scroll', update, { passive: true });
    doc.addEventListener('scroll', update, true);
    if (win.ResizeObserver) {
      resizeObserver = new win.ResizeObserver(bindScrollTarget);
      resizeObserver.observe(doc.body || doc.documentElement);
    }
    if (win.MutationObserver) {
      mutationObserver = new win.MutationObserver(bindScrollTarget);
      mutationObserver.observe(doc.body || doc.documentElement, {
        childList: true,
        subtree: true,
        attributes: true
      });
    }

    const interval = win.setInterval(bindScrollTarget, 500);
    const timeout = win.setTimeout(() => win.clearInterval(interval), 10000);
    bindScrollTarget();

    cleanupViewerFrame = () => {
      stopped = true;
      win.clearInterval(interval);
      win.clearTimeout(timeout);
      win.removeEventListener('scroll', update);
      doc.removeEventListener('scroll', update, true);
      targetCleanup?.();
      resizeObserver?.disconnect();
      mutationObserver?.disconnect();
    };
  };

  const agreeCurrentFile = async () => {
    const file = currentFile.value;
    if (!file || !readToEnd.value || !agreedCurrent.value) {
      return;
    }
    signing.value = true;
    try {
      const progress = await signItemContractFile(item.value.id, {
        signToken: signToken.value,
        contractId: file.contractId,
        fileId: file.id,
        readPercent: 100
      });
      signToken.value = progress.signToken;
      agreedFileIds.value = new Set([...agreedFileIds.value, file.id]);
      if (progress.completed || agreedFileIds.value.size >= totalCount.value) {
        visible.value = false;
        cleanupBoundViewerFrame();
        if (resolver) {
          resolver({ contractSignToken: signToken.value });
          resolver = null;
        }
        return;
      }
      setActiveIndex(activeIndex.value + 1);
      await nextTick();
    } catch (e) {
      message.error(e.message || '合同签署失败');
    } finally {
      signing.value = false;
    }
  };

  onBeforeUnmount(cleanupBoundViewerFrame);

  return {
    visible,
    loading,
    signing,
    contracts,
    files,
    agreedFileIds,
    activeIndex,
    activeKey,
    currentFile,
    totalCount,
    agreedCount,
    completed,
    readPercent,
    readToEnd,
    agreedCurrent,
    ensureContractAgreement,
    cancelAgreement,
    setActiveIndex,
    viewerUrl,
    handlePreviewScroll,
    handleViewerFrameLoad,
    agreeCurrentFile
  };
}
