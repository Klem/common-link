interface Article {
  title: string;
  callout?: string;
  paragraphs?: string[];
  items?: string[];
}

export function LegalArticles({ articles }: { articles: Article[] }) {
  return (
    <>
      {articles.map((article, i) => (
        <div key={i}>
          <h3>{article.title}</h3>
          {article.callout && <div className="legal-callout">{article.callout}</div>}
          {article.paragraphs?.map((p, j) => <p key={j}>{p}</p>)}
          {article.items && article.items.length > 0 && (
            <ul>
              {article.items.map((item, j) => <li key={j}>{item}</li>)}
            </ul>
          )}
        </div>
      ))}
    </>
  );
}
