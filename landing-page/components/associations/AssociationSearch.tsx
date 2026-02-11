'use client';

import { useState, useCallback, useRef, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { AssociationResult, searchAssociations } from '@/lib/api';
import { SearchBox } from './SearchBox';
import { AssociationCard } from './AssociationCard';
import { RegistrationForm } from './RegistrationForm';
import { AssociationDetailModal } from './AssociationDetailModal';

const ITEMS_PER_BATCH = 20;
const API_PER_PAGE = 25;

const QUICK_FILTERS = [
  { key: 'sport', emoji: '🏃' },
  { key: 'culture', emoji: '🎭' },
  { key: 'social', emoji: '🤝' },
  { key: 'environnement', emoji: '🌿' },
  { key: 'education', emoji: '📚' },
  { key: 'sante', emoji: '🏥' },
] as const;

interface LocalFilters {
  postalCode: string;
  city: string;
  employees: string;
}

const emptyFilters: LocalFilters = {
  postalCode: '',
  city: '',
  employees: '',
};

export function AssociationSearch() {
  const t = useTranslations('associations.search');

  // Core search state
  const [allResults, setAllResults] = useState<AssociationResult[]>([]);
  const [filteredResults, setFilteredResults] = useState<AssociationResult[]>([]);
  const [displayedCount, setDisplayedCount] = useState(0);
  const [totalApiResults, setTotalApiResults] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [hasMoreApi, setHasMoreApi] = useState(true);
  const [loading, setLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const [lastQuery, setLastQuery] = useState('');

  // UI state
  const [selected, setSelected] = useState<AssociationResult | null>(null);
  const [modalItem, setModalItem] = useState<AssociationResult | null>(null);
  const [activeQuickFilters, setActiveQuickFilters] = useState<Set<string>>(new Set());
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [filters, setFilters] = useState<LocalFilters>(emptyFilters);

  // Refs
  const sentinelRef = useRef<HTMLDivElement>(null);
  const isLoadingRef = useRef(false);
  const gridRef = useRef<HTMLDivElement>(null);

  // --- Local filtering ---
  const applyLocalFilters = useCallback(
    (results: AssociationResult[], localFilters: LocalFilters) => {
      const filtered = results.filter((item) => {
        const siege = item.siege || {};

        if (
          localFilters.postalCode &&
          (!siege.code_postal || !siege.code_postal.startsWith(localFilters.postalCode))
        )
          return false;
        if (
          localFilters.city &&
          (!siege.libelle_commune ||
            !siege.libelle_commune.toLowerCase().includes(localFilters.city.toLowerCase()))
        )
          return false;
        if (localFilters.employees) {
          const codes = localFilters.employees.split(',');
          if (!codes.includes(item.tranche_effectif_salarie || '')) return false;
        }

        return true;
      });

      setFilteredResults(filtered);
      setDisplayedCount(0);
      return filtered;
    },
    []
  );

  // --- Search callback from SearchBox ---
  const handleSearch = useCallback(
    async (query: string) => {
      if (!query.trim()) return;

      setLoading(true);
      setHasSearched(true);
      setSelected(null);
      setDisplayedCount(0);
      setCurrentPage(1);
      setHasMoreApi(true);
      setLastQuery(query);

      try {
        const data = await searchAssociations(query, API_PER_PAGE, 1);
        const results = data.results;
        setAllResults(results);
        setTotalApiResults(data.total_results);
        setHasMoreApi(results.length < data.total_results);
        setCurrentPage(2);

        const filtered = applyLocalFilters(results, filters);
        // Show first batch immediately
        setDisplayedCount(Math.min(ITEMS_PER_BATCH, filtered.length));
      } catch {
        setAllResults([]);
        setFilteredResults([]);
        setTotalApiResults(0);
      } finally {
        setLoading(false);
      }
    },
    [filters, applyLocalFilters]
  );

  // --- Load more from API ---
  const loadMoreFromApi = useCallback(async () => {
    if (!hasMoreApi || isLoadingRef.current || !lastQuery) return;
    isLoadingRef.current = true;

    try {
      const data = await searchAssociations(lastQuery, API_PER_PAGE, currentPage);
      const newResults = [...allResults, ...data.results];
      setAllResults(newResults);
      setHasMoreApi(newResults.length < data.total_results);
      setCurrentPage((p) => p + 1);

      const filtered = applyLocalFilters(newResults, filters);
      setDisplayedCount((prev) => Math.min(prev + ITEMS_PER_BATCH, filtered.length));
    } catch {
      // silently fail
    } finally {
      isLoadingRef.current = false;
    }
  }, [hasMoreApi, lastQuery, currentPage, allResults, filters, applyLocalFilters]);

  // --- Show more from existing filtered results ---
  const showMoreLocal = useCallback(() => {
    setDisplayedCount((prev) => Math.min(prev + ITEMS_PER_BATCH, filteredResults.length));
  }, [filteredResults.length]);

  // --- Infinite scroll observer ---
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !hasSearched || loading || selected) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !isLoadingRef.current) {
          if (displayedCount < filteredResults.length) {
            showMoreLocal();
          } else if (hasMoreApi && !hasActiveFilters(filters)) {
            loadMoreFromApi();
          }
        }
      },
      { rootMargin: '300px' }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [
    hasSearched,
    loading,
    selected,
    displayedCount,
    filteredResults.length,
    hasMoreApi,
    filters,
    showMoreLocal,
    loadMoreFromApi,
  ]);

  // --- When filters change, re-apply locally ---
  useEffect(() => {
    if (allResults.length > 0) {
      const filtered = applyLocalFilters(allResults, filters);
      setDisplayedCount(Math.min(ITEMS_PER_BATCH, filtered.length));
    }
  }, [filters, allResults, applyLocalFilters]);

  // --- Quick filter toggle ---
  const toggleQuickFilter = (key: string) => {
    const newActive = new Set(activeQuickFilters);
    if (newActive.has(key)) {
      newActive.delete(key);
    } else {
      newActive.add(key);
    }
    setActiveQuickFilters(newActive);

    // Build query from quick filters
    const filterTerms = Array.from(newActive).join(' ');
    if (filterTerms) {
      handleSearch(filterTerms);
    }
  };

  // --- Clear all advanced filters ---
  const clearFilters = () => {
    setFilters(emptyFilters);
  };

  const handleSelect = useCallback((association: AssociationResult) => {
    setSelected(association);
  }, []);

  const handleChangeAssociation = useCallback(() => {
    setSelected(null);
  }, []);

  const activeFilterCount = countActiveFilters(filters);
  const visibleResults = filteredResults.slice(0, displayedCount);
  const canShowMore =
    displayedCount < filteredResults.length || (hasMoreApi && !hasActiveFilters(filters));

  return (
    <div className="max-w-[900px] mx-auto">
      <SearchBox onSearch={handleSearch} />

      {/* Quick filters */}
      <div className="flex gap-2 flex-wrap mt-4 mb-3">
        {QUICK_FILTERS.map(({ key, emoji }) => (
          <button
            key={key}
            onClick={() => toggleQuickFilter(key)}
            className={`font-ui text-[0.8rem] px-4 py-2 rounded-[20px] border cursor-pointer transition-all duration-200 ${
              activeQuickFilters.has(key)
                ? 'bg-secondary border-secondary text-white'
                : 'bg-white border-border text-foreground-muted hover:border-secondary hover:text-secondary'
            }`}
          >
            {emoji} {t(`quickFilters.${key}`)}
          </button>
        ))}
      </div>

      {/* Advanced filters toggle */}
      <button
        onClick={() => setShowAdvanced(!showAdvanced)}
        className="flex items-center justify-center gap-2 w-full py-3 text-foreground-muted font-ui text-[0.85rem] cursor-pointer transition-colors hover:text-secondary bg-transparent border-none"
      >
        <span>{t('advancedToggle')}</span>
        <svg
          width="12"
          height="12"
          viewBox="0 0 12 12"
          fill="none"
          className={`transition-transform duration-300 ${showAdvanced ? 'rotate-180' : ''}`}
        >
          <path
            d="M2 4L6 8L10 4"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
          />
        </svg>
        {activeFilterCount > 0 && (
          <span className="inline-flex items-center gap-1 ml-2 px-2 py-0.5 bg-secondary/10 border border-secondary rounded-[20px] text-secondary text-[0.75rem]">
            {t('filters.activeCount', { count: activeFilterCount })}
            <button
              onClick={(e) => {
                e.stopPropagation();
                clearFilters();
              }}
              className="bg-transparent border-none text-secondary cursor-pointer p-0 text-sm leading-none hover:text-primary"
            >
              ✕
            </button>
          </span>
        )}
      </button>

      {/* Advanced filters panel */}
      <div
        className="overflow-hidden transition-all duration-400"
        style={{ maxHeight: showAdvanced ? '500px' : '0px' }}
      >
        <div className="p-6 bg-white border border-border rounded-lg mb-5">
          <div
            className="grid gap-5"
            style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}
          >
            {/* Postal Code */}
            <div className="flex flex-col gap-2">
              <label className="font-ui text-[0.7rem] uppercase tracking-wider text-foreground-muted">
                {t('filters.postalCode')}
              </label>
              <input
                type="text"
                value={filters.postalCode}
                onChange={(e) => setFilters({ ...filters, postalCode: e.target.value })}
                placeholder={t('filters.postalCodePlaceholder')}
                className="font-ui text-[0.85rem] bg-background-alt border border-border rounded-md px-3 py-2.5 text-foreground-dark outline-none transition-colors focus:border-secondary placeholder:text-foreground-muted"
              />
            </div>

            {/* City */}
            <div className="flex flex-col gap-2">
              <label className="font-ui text-[0.7rem] uppercase tracking-wider text-foreground-muted">
                {t('filters.city')}
              </label>
              <input
                type="text"
                value={filters.city}
                onChange={(e) => setFilters({ ...filters, city: e.target.value })}
                placeholder={t('filters.cityPlaceholder')}
                className="font-ui text-[0.85rem] bg-background-alt border border-border rounded-md px-3 py-2.5 text-foreground-dark outline-none transition-colors focus:border-secondary placeholder:text-foreground-muted"
              />
            </div>

            {/* Employees */}
            <div className="flex flex-col gap-2">
              <label className="font-ui text-[0.7rem] uppercase tracking-wider text-foreground-muted">
                {t('filters.employees')}
              </label>
              <select
                value={filters.employees}
                onChange={(e) => setFilters({ ...filters, employees: e.target.value })}
                className="font-ui text-[0.85rem] bg-background-alt border border-border rounded-md px-3 py-2.5 text-foreground-dark outline-none transition-colors focus:border-secondary"
              >
                <option value="">{t('filters.employeesAll')}</option>
                <option value="00">{t('filters.employees0')}</option>
                <option value="01,02,03">{t('filters.employees1to9')}</option>
                <option value="11,12,21">{t('filters.employees10to49')}</option>
                <option value="22,31,32">{t('filters.employees50to199')}</option>
                <option value="41,42,51,52,53">{t('filters.employees200plus')}</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      {/* Loading */}
      {loading && (
        <div className="mt-8">
          <div className="w-9 h-9 border-[3px] border-border border-t-primary rounded-full animate-spin mx-auto" />
          <p className="text-center font-ui text-[0.9rem] text-foreground-muted mt-4">
            {t('loading')}
          </p>
        </div>
      )}

      {/* Results */}
      {!loading && hasSearched && !selected && (
        <>
          {/* Results header */}
          <div className="font-ui text-[0.9rem] text-foreground-muted mt-6 pb-4 border-b border-border-light flex justify-between items-center flex-wrap gap-2">
            <div>
              {t.rich('resultsCount', {
                count: filteredResults.length,
                strong: (chunks) => <strong className="text-primary">{chunks}</strong>,
              })}
              {activeFilterCount > 0 && (
                <span className="inline-flex items-center gap-2 ml-3 px-3 py-1 bg-secondary/10 border border-secondary rounded-[20px] text-secondary text-[0.75rem]">
                  {t('filters.activeCount', { count: activeFilterCount })}
                  <button
                    onClick={clearFilters}
                    className="bg-transparent border-none text-secondary cursor-pointer p-0 text-sm leading-none hover:text-primary"
                  >
                    ✕
                  </button>
                </span>
              )}
            </div>
            <span className="font-ui text-[0.75rem] text-foreground-muted">
              {displayedCount < filteredResults.length
                ? t('displayedInfo', {
                    displayed: displayedCount,
                    total: filteredResults.length,
                  })
                : canShowMore
                  ? `${displayedCount} · ${t('scrollMore')}`
                  : t('displayedAll', { displayed: displayedCount })}
            </span>
          </div>

          {/* No results */}
          {filteredResults.length === 0 && allResults.length === 0 && (
            <p className="text-center text-foreground-muted py-12">{t('noResults')}</p>
          )}

          {/* No results with filters */}
          {filteredResults.length === 0 && allResults.length > 0 && (
            <div className="text-center py-12">
              <p className="text-foreground-muted mb-4">{t('noResultsWithFilters')}</p>
              <button
                onClick={clearFilters}
                className="font-ui text-[0.85rem] font-semibold text-white bg-secondary border-none px-6 py-3 rounded-md cursor-pointer transition-colors hover:bg-secondary-light"
              >
                {t('clearFilters')}
              </button>
            </div>
          )}

          {/* Results grid */}
          {visibleResults.length > 0 && (
            <div
              ref={gridRef}
              className="grid gap-8 mt-8"
              style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))' }}
            >
              {visibleResults.map((item, index) => (
                <AssociationCard
                  key={`${item.siren}-${index}`}
                  association={item}
                  onSelect={handleSelect}
                  onClick={setModalItem}
                  animationDelay={(index % ITEMS_PER_BATCH) * 30}
                />
              ))}
            </div>
          )}

          {/* Scroll sentinel for infinite scroll */}
          {visibleResults.length > 0 && (
            <div
              ref={sentinelRef}
              className="h-16 flex items-center justify-center mt-5"
            >
              {canShowMore ? (
                <div className="w-6 h-6 border-2 border-border border-t-primary rounded-full animate-spin" />
              ) : (
                <span className="font-ui text-[0.75rem] text-foreground-muted">
                  {t('endOfResults')}
                </span>
              )}
            </div>
          )}
        </>
      )}

      {/* Selected → Registration form */}
      {selected && (
        <RegistrationForm
          association={selected}
          onChangeAssociation={handleChangeAssociation}
        />
      )}

      {/* Detail modal */}
      <AssociationDetailModal
        association={modalItem}
        onClose={() => setModalItem(null)}
      />
    </div>
  );
}

function hasActiveFilters(filters: LocalFilters): boolean {
  return !!(filters.postalCode || filters.city || filters.employees);
}

function countActiveFilters(filters: LocalFilters): number {
  return [filters.postalCode, filters.city, filters.employees].filter(Boolean).length;
}
