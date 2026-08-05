'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';

interface Props {
  campaignName: string;
  selectedAmount: number | undefined;
}

export function StickyBar({ campaignName, selectedAmount }: Props) {
  const tLanding = useTranslations('landing');
  const tWidget = useTranslations('widget');
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    function check() {
      const hero = document.querySelector('.lp-hero');
      const don = document.getElementById('don');
      if (!hero || !don) return;
      const heroBottom = hero.getBoundingClientRect().bottom;
      const donTop = don.getBoundingClientRect().top;
      setVisible(heroBottom < 0 && donTop > window.innerHeight / 2);
    }

    window.addEventListener('scroll', check, { passive: true });
    check();
    return () => window.removeEventListener('scroll', check);
  }, []);

  const ctaLabel = selectedAmount
    ? tLanding('sticky.cta', { amount: selectedAmount })
    : tWidget('submit');

  return (
    <div
      className={`lp-sticky-bar${visible ? ' lp-sticky-bar--visible' : ''}`}
      aria-hidden={!visible}
    >
      <div className="lp-sticky-bar-left">
        <span className="lp-sticky-bar-label">{tLanding('sticky.label')}</span>
        <span className="lp-sticky-bar-campaign">{campaignName}</span>
      </div>
      <a href="#don" className="lp-sticky-bar-btn">{ctaLabel}</a>
    </div>
  );
}
