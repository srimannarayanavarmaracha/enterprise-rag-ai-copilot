import { useEffect, useState } from 'react';

interface KnowledgeDoc {
  id: number;
  title: string;
  chunkCount: number;
  createdAt: string;
}

interface Citation {
  index: number;
  documentTitle: string;
  chunkIndex: number;
  score: number;
  snippet: string;
}

interface RagAnswer {
  answer: string;
  provider: string;
  retrievalLatencyMs: number;
  topMatchScore: number;
  citations: Citation[];
}

const API_BASE = import.meta.env.VITE_API_BASE || '';

export default function App() {
  const [token, setToken] = useState<string>(localStorage.getItem('rag_jwt') || '');
  const [email, setEmail] = useState('demo@sriman.dev');
  const [password, setPassword] = useState('Demo@1234');
  const [docs, setDocs] = useState<KnowledgeDoc[]>([]);
  const [docTitle, setDocTitle] = useState('');
  const [docContent, setDocContent] = useState('');
  const [question, setQuestion] = useState('How does our platform prevent duplicate charges across microservices?');
  const [answer, setAnswer] = useState<RagAnswer | null>(null);
  const [error, setError] = useState('');

  async function login(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    const res = await fetch(`${API_BASE}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    if (!res.ok) {
      setError('Login failed. Check credentials.');
      return;
    }
    const data = await res.json();
    localStorage.setItem('rag_jwt', data.token);
    setToken(data.token);
  }

  async function loadDocs(jwt: string) {
    const res = await fetch(`${API_BASE}/api/v1/documents`, {
      headers: { Authorization: `Bearer ${jwt}` }
    });
    if (res.ok) setDocs(await res.json());
  }

  useEffect(() => {
    if (token) loadDocs(token).catch(() => undefined);
  }, [token]);

  async function ingestText(e: React.FormEvent) {
    e.preventDefault();
    if (!docTitle.trim() || !docContent.trim()) return;
    const res = await fetch(`${API_BASE}/api/v1/documents/text`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ title: docTitle, content: docContent })
    });
    if (res.ok) {
      setDocTitle('');
      setDocContent('');
      await loadDocs(token);
    }
  }

  async function askCopilot(e: React.FormEvent) {
    e.preventDefault();
    const res = await fetch(`${API_BASE}/api/v1/chat/ask`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ question, topK: 3 })
    });
    if (res.ok) {
      setAnswer(await res.json());
    }
  }

  if (!token) {
    return (
      <div className="container" style={{ maxWidth: 440, marginTop: 60 }}>
        <div className="card">
          <h1 style={{ marginTop: 0 }}>Enterprise RAG Copilot</h1>
          <p style={{ color: '#475569', fontSize: 14 }}>
            Sign in with JWT auth (pre-seeded demo: demo@sriman.dev / Demo@1234)
          </p>
          <form onSubmit={login}>
            <label>Email</label>
            <input className="input" value={email} onChange={(e) => setEmail(e.target.value)} />
            <label>Password</label>
            <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            {error && <p style={{ color: '#b91c1c', fontSize: 13 }}>{error}</p>}
            <button className="btn" type="submit">Sign In</button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div>
          <h1 style={{ margin: 0 }}>Enterprise RAG &amp; Citation Copilot</h1>
          <p style={{ margin: '4px 0 0', color: '#475569' }}>
            Spring Boot + Spring AI + Hybrid Vector/Lexical Retrieval
          </p>
        </div>
        <button className="btn" onClick={() => { localStorage.removeItem('rag_jwt'); setToken(''); }}>
          Sign Out
        </button>
      </div>

      <div className="grid">
        <div className="card">
          <h2 style={{ marginTop: 0 }}>Knowledge Base</h2>
          <form onSubmit={ingestText}>
            <label>Document Title</label>
            <input className="input" placeholder="e.g. sql-tuning.md" value={docTitle} onChange={(e) => setDocTitle(e.target.value)} />
            <label>Content</label>
            <textarea className="textarea" rows={5} placeholder="Paste runbook or architecture notes..." value={docContent} onChange={(e) => setDocContent(e.target.value)} />
            <button className="btn" type="submit">Ingest &amp; Vectorize</button>
          </form>
          <hr style={{ margin: '16px 0', border: 'none', borderTop: '1px solid #e2e8f0' }} />
          <h3 style={{ margin: '0 0 8px' }}>Indexed Documents ({docs.length})</h3>
          {docs.map((d) => (
            <div key={d.id} style={{ padding: '6px 0', borderBottom: '1px solid #f1f5f9', fontSize: 13 }}>
              <strong>{d.title}</strong> ({d.chunkCount} chunks)
            </div>
          ))}
        </div>

        <div className="card">
          <h2 style={{ marginTop: 0 }}>Ask the Knowledge Base</h2>
          <form onSubmit={askCopilot}>
            <input className="input" value={question} onChange={(e) => setQuestion(e.target.value)} />
            <button className="btn" type="submit">Run Hybrid Retrieval &amp; Synthesize</button>
          </form>

          {answer && (
            <div style={{ marginTop: 20 }}>
              <div style={{ fontSize: 12, color: '#475569', marginBottom: 8 }}>
                Provider: <strong>{answer.provider}</strong> | Retrieval Latency: <strong>{answer.retrievalLatencyMs} ms</strong> | Top Score: <strong>{answer.topMatchScore.toFixed(3)}</strong>
              </div>
              <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.5, background: '#f8fafc', padding: 14, borderRadius: 8 }}>
                {answer.answer}
              </div>
              <h3>Citations ({answer.citations.length})</h3>
              {answer.citations.map((c) => (
                <div className="citation" key={c.index}>
                  <strong>[{c.index}] {c.documentTitle}</strong> (chunk #{c.chunkIndex}, score {c.score.toFixed(3)})
                  <div style={{ marginTop: 4, color: '#334155' }}>{c.snippet}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
