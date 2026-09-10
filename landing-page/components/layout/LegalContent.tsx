interface LegalContentProps {
  title: string;
  meta?: string;
  children: React.ReactNode;
}

export function LegalContent({ title, meta, children }: LegalContentProps) {
  return (
    <main>
      <section className="bg-background-alt py-14">
        <div className="max-w-container mx-auto px-8">
          <h1 className="font-ui text-[2rem] md:text-[2.5rem] font-extrabold text-foreground-dark mb-2">{title}</h1>
          {meta && <p className="text-foreground-muted text-[0.9rem]">{meta}</p>}
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
