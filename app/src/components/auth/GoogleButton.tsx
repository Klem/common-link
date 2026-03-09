'use client';

import { useRef } from 'react';
import { GoogleLogin } from '@react-oauth/google';

interface GoogleButtonProps {
  onSuccess: (idToken: string) => void;
  label?: string;
  loading?: boolean;
}

export function GoogleButton({ onSuccess, label, loading = false }: GoogleButtonProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  const handleCustomClick = () => {
    if (loading) return;
    const googleBtn = containerRef.current?.querySelector('[role="button"]') as HTMLElement | null;
    googleBtn?.click();
  };

  return (
    <div className="relative">
      {/* Hidden Google Login — triggered programmatically */}
      <div ref={containerRef} className="absolute opacity-0 pointer-events-none w-px h-px overflow-hidden">
        <GoogleLogin
          onSuccess={(credentialResponse) => {
            if (credentialResponse.credential) {
              onSuccess(credentialResponse.credential);
            }
          }}
          onError={() => undefined}
          type="standard"
          theme="filled_black"
          size="large"
        />
      </div>

      {/* Custom-styled button matching the design system */}
      <button
        type="button"
        onClick={handleCustomClick}
        disabled={loading}
        aria-label={label ?? 'Continuer avec Google'}
        className="flex items-center gap-[13px] w-full px-4 py-3 bg-bg-3 border border-border rounded-[10px] text-text font-body text-[13.5px] font-medium cursor-pointer transition-all duration-[250ms] ease-[cubic-bezier(0.22,1,0.36,1)] hover:border-cyan/40 hover:bg-bg-3/90 hover:-translate-y-px hover:shadow-[0_6px_20px_rgba(0,0,0,.25)] active:translate-y-0 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <span className="w-5 h-5 flex-shrink-0 flex items-center justify-center">
          <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
          </svg>
        </span>
        <span className="flex-1 text-left">{label ?? 'Continuer avec Google'}</span>
        <span className="text-[14px] text-muted transition-all duration-[200ms] group-hover:text-green group-hover:translate-x-[3px]">→</span>
      </button>
    </div>
  );
}
