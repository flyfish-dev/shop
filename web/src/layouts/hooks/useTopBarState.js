import { computed, onMounted } from 'vue';
import { storeToRefs } from 'pinia';
import useClientStore from '@/modules/auth/store/client.js';
import { useRouter } from '@/router/use';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';

const lerp = (start, end, progress) => start + (end - start) * progress;
const px = value => `${value.toFixed(3)}px`;

export const useTopBarState = () => {
  const router = useRouter();
  const { collapsed, topbarProgress, user, width } = storeToRefs(useClientStore());
  const { hasLowcode, hasShop, portalTitle, defaultEntryPath, loadPortalCapabilities } = usePortalCapabilities();

  const isHome = computed(() => router.currentRoute.value === '/');
  const brandTitle = computed(() => {
    return portalTitle.value;
  });
  const gutter = computed(() => topbarProgress.value > 0.72 ? 10 : 20);
  const compactLogin = computed(() => topbarProgress.value > 0.84 && width.value < 560);
  const compactUser = computed(() => topbarProgress.value > 0.84 && width.value < 1180);
  const showStart = computed(() => isHome.value && topbarProgress.value < 0.82);
  const navInteractive = computed(() => !isHome.value || topbarProgress.value > 0.72);

  const topbarStyle = computed(() => {
    const progress = topbarProgress.value;
    const viewport = Math.max(width.value || 0, 320);
    const mobile = viewport < 640;
    const hasUser = Boolean(user.value?.id);
    const compactUserAtRest = hasUser && viewport < 1180;
    const expandedHeight = 445;
    const collapsedHeight = mobile ? 112 : 72;
    const expandedLogo = Math.min(280, viewport * 0.72);
    const collapsedLogo = mobile ? 56 : 60;
    const collapsedLogoLeft = mobile ? 12 : 20;
    const titleCollapsedLeft = collapsedLogoLeft + collapsedLogo + (mobile ? 8 : 18);
    const titleExpandedTop = 40 + expandedLogo + 20;
    const collapsedToolRight = mobile ? 14 : 26;
    const collapsedToolWidth = hasUser
      ? (compactUserAtRest ? 42 : 210)
      : (showStart.value ? 190 : 104);
    const collapsedNavRight = mobile ? 0 : collapsedToolRight + collapsedToolWidth + 10;
    const collapsedTitleReserve = mobile ? 176 : collapsedNavRight + 320;
    const titleCollapsedMax = mobile
      ? Math.max(96, viewport - 176)
      : Math.min(440, Math.max(160, viewport - titleCollapsedLeft - collapsedTitleReserve));
    const navOpacity = isHome.value ? Math.max(0, Math.min(1, (progress - 0.58) / 0.22)) : 1;

    return {
      '--topbar-progress': progress.toFixed(4),
      '--topbar-height': px(lerp(expandedHeight, collapsedHeight, progress)),
      '--topbar-shadow-alpha': (0.45 * progress).toFixed(4),
      '--logo-size': px(lerp(expandedLogo, collapsedLogo, progress)),
      '--logo-left': px(lerp((viewport - expandedLogo) / 2, collapsedLogoLeft, progress)),
      '--logo-top': px(lerp(40, mobile ? 42 : 6, progress)),
      '--logo-radius': px(lerp(0, mobile ? 18 : 20, progress)),
      '--title-left': px(lerp(viewport / 2, titleCollapsedLeft, progress)),
      '--title-top': px(lerp(titleExpandedTop, mobile ? 52 : 24, progress)),
      '--title-translate': `${lerp(-50, 0, progress).toFixed(3)}%`,
      '--title-font-size': px(lerp(30, 18, progress)),
      '--title-max-width': px(lerp(Math.min(viewport - 48, 760), titleCollapsedMax, progress)),
      '--tool-left': px(lerp(viewport / 2, viewport - collapsedToolRight, progress)),
      '--tool-top': px(lerp(titleExpandedTop + 62, mobile ? 68 : 18, progress)),
      '--tool-translate': `${lerp(-50, -100, progress).toFixed(3)}%`,
      '--nav-top': px(lerp(12, mobile ? 6 : 18, progress)),
      '--nav-right': px(lerp(40, collapsedNavRight, progress)),
      '--nav-opacity': navOpacity.toFixed(4),
      '--nav-scale': (0.96 + navOpacity * 0.04).toFixed(4)
    };
  });

  const navStyle = computed(() => {
    const style = topbarStyle.value;
    return {
      opacity: style['--nav-opacity'],
      right: style['--nav-right'],
      top: style['--nav-top'],
      transform: `scale(${style['--nav-scale']})`
    };
  });

  const login = () => {
    router.push('/login');
  };
  const start = () => {
    router.push(defaultEntryPath.value);
  };

  onMounted(() => {
    loadPortalCapabilities().catch(() => {});
  });

  return {
    brandTitle,
    collapsed,
    compactLogin,
    compactUser,
    gutter,
    isHome,
    login,
    navInteractive,
    navStyle,
    showStart,
    start,
    topbarStyle,
    user
  };
};
