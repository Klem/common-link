interface LegalContentProps {
  title: string;
  lastUpdated?: string;
  children: React.ReactNode;
}

export function LegalContent({ title, lastUpdated, children }: LegalContentProps) {
  return (
    <main>
      <section className="bg-primary-dark text-white py-16">
        <div className="max-w-container mx-auto px-8">
          <h1 className="font-ui text-[2rem] md:text-[2.5rem] font-bold text-white mb-2">{title}</h1>
          {lastUpdated && (
            <p className="text-white/60 text-[0.9rem]">Dernière mise à jour : {lastUpdated}</p>
          )}
        </div>
      </section>

      <section className="py-16">
        <div className="max-w-[800px] mx-auto px-8">
          <div className="legal-prose">{children}</div>
        </div>
      </section>
    </main>
  );
}
