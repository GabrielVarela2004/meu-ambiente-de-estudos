import { useState } from 'react'
import { MessageSquareIcon, PlusCircleIcon, SearchIcon } from '../components/icons'
import '../styles/pages/settings-forum.css'

type ForumTopic = {
  id: string
  title: string
  author: string
  authorAvatar: string
  tags: string[]
  repliesCount: number
  votesCount: number
  createdAt: string
  isPinned?: boolean
}

const initialTopics: ForumTopic[] = [
  {
    id: '1',
    title: 'Como implementar o Circuit Breaker com Resilience4j em arquiteturas event-driven?',
    author: 'DevSenior99',
    authorAvatar: 'DS',
    tags: ['Arquitetura', 'Java', 'Resiliência'],
    repliesCount: 14,
    votesCount: 38,
    createdAt: 'Há 2 horas',
    isPinned: true,
  },
  {
    id: '2',
    title: 'Qual a melhor estratégia para repetição espaçada em algoritmos de grafos (Dijkstra vs A*)?',
    author: 'AnaCode',
    authorAvatar: 'AC',
    tags: ['Algoritmos', 'Grafos', 'Estudo'],
    repliesCount: 8,
    votesCount: 22,
    createdAt: 'Há 5 horas',
  },
  {
    id: '3',
    title: 'PostgreSQL: diferenças práticas entre READ COMMITTED e SERIALIZABLE em cenários de alta concorrência',
    author: 'DatabaseWizard',
    authorAvatar: 'DW',
    tags: ['PostgreSQL', 'Concorrência', 'SQL'],
    repliesCount: 19,
    votesCount: 45,
    createdAt: 'Ontem',
  },
  {
    id: '4',
    title: 'Compartilhando meu resumo em PDF sobre princípios SOLID e Inversão de Controle no Spring Boot',
    author: 'GabrielTech',
    authorAvatar: 'GT',
    tags: ['Spring Boot', 'Boas Práticas', 'OOP'],
    repliesCount: 6,
    votesCount: 17,
    createdAt: 'Há 2 dias',
  },
]

export function ForumPage() {
  const [topics, setTopics] = useState<ForumTopic[]>(initialTopics)
  const [searchTerm, setSearchTerm] = useState('')
  const [selectedTag, setSelectedTag] = useState<string>('ALL')
  const [newTopicModalOpen, setNewTopicModalOpen] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [newTag, setNewTag] = useState('Geral')

  const allTags = ['ALL', 'Arquitetura', 'Java', 'Algoritmos', 'PostgreSQL', 'Spring Boot']

  const handleCreateTopic = (e: React.FormEvent) => {
    e.preventDefault()
    if (!newTitle.trim()) return

    const newEntry: ForumTopic = {
      id: String(Date.now()),
      title: newTitle.trim(),
      author: 'Você (Aluno)',
      authorAvatar: 'VC',
      tags: [newTag],
      repliesCount: 0,
      votesCount: 1,
      createdAt: 'Agora mesmo',
    }

    setTopics([newEntry, ...topics])
    setNewTitle('')
    setNewTopicModalOpen(false)
  }

  const filteredTopics = topics.filter((t) => {
    const matchesSearch = t.title.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesTag = selectedTag === 'ALL' || t.tags.includes(selectedTag)
    return matchesSearch && matchesTag
  })

  return (
    <div className="page-wrapper">
      <div className="page-header-flex">
        <div>
          <h1 className="page-main-title">Fórum da Comunidade</h1>
          <p className="page-main-subtitle">
            Tire dúvidas, compartilhe resumos e debata resoluções de questões técnicas com outros estudantes.
          </p>
        </div>

        <div className="page-actions-group">
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setNewTopicModalOpen(true)}
          >
            <PlusCircleIcon className="w-4 h-4" />
            <span>Criar Nova Discussão</span>
          </button>
        </div>
      </div>

      {/* Toolbar */}
      <div className="toolbar-card mb-6">
        <div className="search-input-wrapper flex-1">
          <SearchIcon className="search-icon" />
          <input
            type="text"
            className="search-input"
            placeholder="Pesquisar discussões por título ou assunto..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div className="filter-chips-group">
          {allTags.map((tag) => (
            <button
              key={tag}
              type="button"
              className={`chip-btn ${selectedTag === tag ? 'active' : ''}`}
              onClick={() => setSelectedTag(tag)}
            >
              {tag === 'ALL' ? 'Todos os Tópicos' : tag}
            </button>
          ))}
        </div>
      </div>

      {/* Lista de Tópicos */}
      <div className="forum-topics-list">
        {filteredTopics.map((topic) => (
          <div key={topic.id} className="card forum-topic-card">
            <div className="forum-vote-col">
              <span className="vote-arrow">▲</span>
              <span className="vote-number">{topic.votesCount}</span>
            </div>

            <div className="forum-main-col">
              <div className="flex-gap-center mb-1">
                {topic.isPinned && <span className="badge badge-emerald">Fixado</span>}
                {topic.tags.map((t) => (
                  <span key={t} className="badge badge-subtle">
                    #{t}
                  </span>
                ))}
              </div>

              <h2 className="forum-topic-title">{topic.title}</h2>

              <div className="forum-meta-row">
                <span className="author-tag">
                  <span className="author-avatar">{topic.authorAvatar}</span>
                  {topic.author}
                </span>
                <span className="meta-dot">•</span>
                <span className="meta-time">{topic.createdAt}</span>
              </div>
            </div>

            <div className="forum-replies-col">
              <div className="replies-count-box">
                <MessageSquareIcon className="w-4 h-4 text-emerald-400" />
                <span>{topic.repliesCount} respostas</span>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Modal Nova Discussão */}
      {newTopicModalOpen && (
        <div className="modal-backdrop" onClick={() => setNewTopicModalOpen(false)}>
          <div className="modal-dialog-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Criar Nova Discussão no Fórum</h2>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setNewTopicModalOpen(false)}
              >
                &times;
              </button>
            </div>

            <form onSubmit={handleCreateTopic} className="modal-form-body">
              <div className="form-field-group">
                <label htmlFor="forum-title">Título da Dúvida ou Discussão *</label>
                <input
                  id="forum-title"
                  type="text"
                  className="form-input"
                  placeholder="Seja claro e específico no título..."
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  required
                />
              </div>

              <div className="form-field-group">
                <label htmlFor="forum-category">Categoria Principal</label>
                <select
                  id="forum-category"
                  className="form-select"
                  value={newTag}
                  onChange={(e) => setNewTag(e.target.value)}
                >
                  <option value="Arquitetura">Arquitetura de Software</option>
                  <option value="Java">Java & JVM</option>
                  <option value="Algoritmos">Algoritmos & Estruturas</option>
                  <option value="PostgreSQL">PostgreSQL & Bancos</option>
                  <option value="Spring Boot">Spring Boot</option>
                </select>
              </div>

              <div className="modal-footer-actions">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setNewTopicModalOpen(false)}
                >
                  Cancelar
                </button>
                <button type="submit" className="btn btn-primary">
                  Publicar Discussão
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
