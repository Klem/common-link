function getInitials(name: string): string {
  const words = name.split(/\s+/).filter(Boolean);
  if (!words.length) return '?';
  if (words.length === 1) return words[0][0].toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

interface LandingHeaderProps {
  associationName: string;
}

export function LandingHeader({ associationName }: LandingHeaderProps) {
  return (
    <header className="lp-header-bar">
      <div className="lp-header-inner">
        <div className="lp-header-avatar" aria-hidden="true">
          {getInitials(associationName)}
        </div>
        <span className="lp-header-name">{associationName}</span>
      </div>
    </header>
  );
}
