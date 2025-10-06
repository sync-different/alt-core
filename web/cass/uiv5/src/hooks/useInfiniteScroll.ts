import { useEffect, useRef, useCallback } from 'react';

interface UseInfiniteScrollOptions {
  hasMore: boolean;
  isLoading: boolean;
  onLoadMore: () => void;
  threshold?: number;
  root?: HTMLElement | null;
}

/**
 * Custom hook for implementing infinite scroll
 * Uses IntersectionObserver to detect when sentinel element is visible
 */
export const useInfiniteScroll = ({
  hasMore,
  isLoading,
  onLoadMore,
  threshold = 100,
  root = null,
}: UseInfiniteScrollOptions) => {
  const observerRef = useRef<HTMLDivElement | null>(null);
  const observerInstance = useRef<IntersectionObserver | null>(null);

  const handleObserver = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      const target = entries[0];
      if (target.isIntersecting && hasMore && !isLoading) {
        console.log('IntersectionObserver triggered - loading more files');
        onLoadMore();
      }
    },
    [hasMore, isLoading, onLoadMore]
  );

  useEffect(() => {
    const options = {
      root,
      rootMargin: `${threshold}px`,
      threshold: 0,
    };

    observerInstance.current = new IntersectionObserver(handleObserver, options);

    const currentObserver = observerInstance.current;
    const currentRef = observerRef.current;

    if (currentRef) {
      currentObserver.observe(currentRef);
      console.log('IntersectionObserver setup complete', { hasMore, isLoading, root });
    }

    return () => {
      if (currentRef && currentObserver) {
        currentObserver.unobserve(currentRef);
      }
    };
  }, [handleObserver, threshold, root, hasMore, isLoading]);

  return observerRef;
};
