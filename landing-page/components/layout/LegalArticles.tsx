interface Article {
  title: string;
  paragraphs?: string[];
  items?: string[];
}

export function LegalArticles({ articles }: { articles: Article[] }) {
  return (
    <>
      {articles.map((article, i) => (
        <div key={i}>
          <h2>{article.title}</h2>
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
