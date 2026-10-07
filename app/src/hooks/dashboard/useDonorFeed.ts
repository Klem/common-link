'use client';

import { useState, useEffect } from 'react';
import { getDonorFeed, markDonorFeedSeen } from '@/lib/api/donor';
import type { DonorFeedItemDto } from '@/types/donor';

interface UseDonorFeedReturn {
  feed: DonorFeedItemDto[];
  isLoading: boolean;
  error: string | null;
}

/**
 * Fetches the donor's engagement feed once on mount, then marks it as seen.
 *
 * The GET is read-only; `markDonorFeedSeen` is called only after the feed has actually been
 * fetched and rendered here -- never before, so an interrupted load never clears the block without
 * the donor having seen it. Gated by the same `cancelled` flag as the state updates, so React's
 * development double-invoke of effects (StrictMode) -- which cleans up the first instance before
 * its promise resolves -- never marks the feed as seen twice for a single mount.
 */
export function useDonorFeed(): UseDonorFeedReturn {
  const [feed, setFeed] = useState<DonorFeedItemDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getDonorFeed()
      .then((data) => {
        if (cancelled) return;
        setFeed(data);
        markDonorFeedSeen().catch(() => {
          // Best-effort: a failed mark-as-seen only means the block reappears next visit.
        });
      })
      .catch(() => {
        if (!cancelled) setError('common.errors.serverError');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return { feed, isLoading, error };
}
