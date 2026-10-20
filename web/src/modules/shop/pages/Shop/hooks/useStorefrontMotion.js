import { onBeforeUnmount } from 'vue';

export function useStorefrontMotion() {
  let observer;
  const vReveal = {
    mounted(element) {
      if (!window.IntersectionObserver || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
      observer ||= new IntersectionObserver(entries => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            entry.target.classList.remove('reveal-pending');
            observer.unobserve(entry.target);
          }
        });
      }, { threshold: 0.06 });
      element.classList.add('reveal-pending');
      observer.observe(element);
    },
    unmounted(element) { observer?.unobserve(element); }
  };
  onBeforeUnmount(() => observer?.disconnect());
  return { vReveal };
}
