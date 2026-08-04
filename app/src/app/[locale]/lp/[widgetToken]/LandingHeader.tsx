import { apiUrl } from '@/lib/api';

function getInitials(name: string): string {
  const words = name.split(/\s+/).filter(Boolean);
  if (!words.length) return '?';
  if (words.length === 1) return words[0][0].toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

interface LandingHeaderProps {
  associationName: string;
  /**
   * Public serving path of the association logo, or null. When present it replaces the initials
   * placeholder — never the association name, which keeps its place next to it.
   */
  landingLogo?: string | null;
}

export function LandingHeader({ associationName, landingLogo }: LandingHeaderProps) {
  return (
    <header className="lp-header-bar">
      <div className="lp-header-inner">
        {landingLogo ? (
          // eslint-disable-next-line @next/next/no-img-element -- served by the API, not by /public
          <img className="lp-header-logo" src={apiUrl(landingLogo)} alt={associationName} />
        ) : (
          <div className="lp-header-avatar" aria-hidden="true">
            {getInitials(associationName)}
          </div>
        )}
        <span className="lp-header-name">{associationName}</span>
      </div>
    </header>
  );
}
