import { useEffect, useCallback } from 'react';

let observer = null;
let refCount = 0;

function updateAriaHidden() {
  const slides = document.querySelectorAll('.slick-slide');

  slides.forEach((slide) => {
    const hidden = slide.getAttribute('aria-hidden') === 'true';
    const anchors = slide.querySelectorAll('a');

    anchors.forEach((anchor) => {
      if (hidden) {
        anchor.setAttribute('inert', 'true');
      } else {
        anchor.removeAttribute('inert');
      }
    });
  });
}

// ==============================|| HOOKS - ARIA HIDDEN ||============================== //

export default function useAriaHidden() {
  const update = useCallback(updateAriaHidden, []);

  useEffect(() => {
    refCount += 1;

    if (!observer) {
      const container = document.querySelector('.slick-slider') || document.body;
      observer = new MutationObserver(updateAriaHidden);
      observer.observe(container, { childList: true, subtree: true });

      // Initial run
      updateAriaHidden();
    }

    return () => {
      refCount -= 1;

      // Cleanup the observer only when the last component unmounts
      if (refCount === 0 && observer) {
        observer.disconnect();
        observer = null;
      }
    };
  }, [update]);

  return update;
}
