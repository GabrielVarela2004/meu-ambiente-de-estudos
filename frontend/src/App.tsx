import { useCallback, useEffect, useRef, useState } from 'react'
import './App.css'
import { request } from './api/http'
import { Sidebar, type PageId } from './components/Sidebar'
import { IconButton } from './components/ui/Button'
import { useMediaQuery } from './hooks/useMediaQuery'
import { GlobalPerformancePanel } from './features/learning/GlobalPerformancePanel'
import { GlobalReviewsBatteryPanel } from './features/reviews/GlobalReviewsBatteryPanel'
import { TopicsPanel } from './features/knowledge/TopicsPanel'
import { TutorChatPanel } from './features/chat/TutorChatPanel'
import { StudyBlocksPage, type StudyBlock } from './pages/StudyBlocksPage'
import { AddContentPage } from './pages/AddContentPage'
import { PracticalExercisesPage } from './pages/PracticalExercisesPage'
import { TheoreticalQuestionsPage } from './pages/TheoreticalQuestionsPage'
import { BlockWorkspacePage } from './pages/BlockWorkspacePage'
import { GoalsSessionsPage } from './pages/GoalsSessionsPage'
import { SettingsPage } from './pages/SettingsPage'
import { CareerJobFeedPage } from './pages/career/CareerJobFeedPage'
import { CareerPreferencesPage } from './pages/career/CareerPreferencesPage'
import { CareerSkillsPage } from './pages/career/CareerSkillsPage'
import { CareerSourcesPage } from './pages/career/CareerSourcesPage'
import { CareerAgentSchedulePage } from './pages/career/CareerAgentSchedulePage'

type StudyMaterial = {
  id: string
  studyBlockId: string
  title: string
  content: string
  sourceType?: 'TEXT' | 'FILE_TXT' | 'FILE_MD' | 'FILE_PDF' | 'FILE_DOCX' | 'YOUTUBE'
  sourceUrl?: string | null
  externalVideoId?: string | null
  processingStatus?: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  enabledForChat?: boolean
  wordCount?: number
  charCount?: number
  status: 'ACTIVE' | 'ARCHIVED'
  updatedAt: string
}

type DueReviewQuestion = {
  id: string
  studyBlockId: string
  studyBlockTitle: string
  prompt: string
  explanation: string | null
  nextReviewAt: string
  reviewIntervalDays: number
  correctStreak: number
}

const pageTitles: Record<PageId, string> = {
  'global-performance': 'Desempenho Global',
  'review-difficulties': 'Revisar Dificuldades',
  'study-blocks': 'Blocos de Estudo',
  'add-content': 'Adicionar Conteúdo',
  'practical-block': 'Exercícios Práticos',
  'practical-global': 'Exercícios Práticos',
  'theoretical-block': 'Exercícios Teóricos',
  'theoretical-global': 'Exercícios Teóricos',
  'source-mapping': 'Mapeamento de Fontes',
  'goals-sessions': 'Metas e Sessões',
  'career-job-feed': 'Feed de Vagas',
  'career-preferences': 'Filtros de Busca',
  'career-skills': 'Minhas Competências',
  'career-sources': 'Fontes de Vagas',
  'career-agent-schedule': 'Agendamento do Agente',
  settings: 'Configurações',
}

function App() {
  const [currentPage, setCurrentPage] = useState<PageId>('study-blocks')
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false)
  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState(false)
  const [isChatOpen, setIsChatOpen] = useState(false)
  const isMobile = useMediaQuery('(max-width: 768px)')
  const chatTriggerRef = useRef<HTMLButtonElement>(null)
  const chatCloseRef = useRef<HTMLButtonElement>(null)
  const [blocks, setBlocks] = useState<StudyBlock[]>([])
  const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null)
  const [activeWorkspaceBlock, setActiveWorkspaceBlock] = useState<StudyBlock | null>(null)

  const [materials, setMaterials] = useState<StudyMaterial[]>([])
  const [materialsCountByBlock, setMaterialsCountByBlock] = useState<Record<string, number>>({})
  const [questionsCountByBlock, setQuestionsCountByBlock] = useState<Record<string, number>>({})
  const [globalDueReviews, setGlobalDueReviews] = useState<DueReviewQuestion[]>([])

  const [, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadGlobalReviews = useCallback(async () => {
    try {
      const data = await request<DueReviewQuestion[]>('/api/reviews/due')
      setGlobalDueReviews(data)
    } catch {
      // Silencioso se endpoint ainda não respondeu
    }
  }, [])

  const loadBlockCounts = useCallback(async (blockId: string) => {
    try {
      const mats = await request<StudyMaterial[]>(`/api/study-blocks/${blockId}/materials?includeArchived=false`)
      setMaterialsCountByBlock((prev) => ({ ...prev, [blockId]: mats.length }))
    } catch {
      // silencioso
    }
    try {
      const qs = await request<unknown[]>(`/api/study-blocks/${blockId}/questions?includeArchived=false`)
      setQuestionsCountByBlock((prev) => ({ ...prev, [blockId]: qs.length }))
    } catch {
      // silencioso
    }
  }, [])

  const loadBlocks = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await request<StudyBlock[]>('/api/study-blocks?includeArchived=true')
      setBlocks(data)
      // Mantém a seleção apenas se o bloco continuar ativo; senão, usa o primeiro bloco ativo.
      const activeBlocks = data.filter((b) => b.status === 'ACTIVE')
      setSelectedBlockId((current) =>
        current && activeBlocks.some((b) => b.id === current) ? current : (activeBlocks[0]?.id ?? null),
      )
      for (const b of data) {
        void loadBlockCounts(b.id)
      }
      await loadGlobalReviews()
    } catch {
      setError('Não foi possível conectar com o backend em http://localhost:8080. Verifique se o servidor Spring Boot está rodando.')
    } finally {
      setLoading(false)
    }
  }, [loadBlockCounts, loadGlobalReviews])

  useEffect(() => {
    void loadBlocks()
  }, [loadBlocks])

  const loadMaterials = useCallback(async () => {
    if (!selectedBlockId) return
    try {
      const data = await request<StudyMaterial[]>(
        `/api/study-blocks/${selectedBlockId}/materials?includeArchived=false`
      )
      setMaterials(data)
      setMaterialsCountByBlock((prev) => ({ ...prev, [selectedBlockId]: data.length }))
    } catch {
      // silencioso
    }
  }, [selectedBlockId])

  useEffect(() => {
    void loadMaterials()
  }, [loadMaterials])

  const handleSaveBlock = async (
    form: { title: string; description: string; learningObjective: string },
    editingId: string | null
  ) => {
    const payload = {
      title: form.title.trim(),
      description: form.description.trim() ? form.description.trim() : null,
      learningObjective: form.learningObjective.trim() ? form.learningObjective.trim() : null,
    }

    if (editingId) {
      await request<StudyBlock>(`/api/study-blocks/${editingId}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      })
    } else {
      const created = await request<StudyBlock>('/api/study-blocks', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      setSelectedBlockId(created.id)
    }
    await loadBlocks()
  }

  const handleArchiveBlock = async (id: string) => {
    await request<void>(`/api/study-blocks/${id}`, { method: 'DELETE' })
    await loadBlocks()
  }

  const handleUnarchiveBlock = async (id: string) => {
    await request<void>(`/api/study-blocks/${id}/unarchive`, { method: 'POST' })
    await loadBlocks()
  }

  const selectedBlockTitle =
    blocks.find((b) => b.id === selectedBlockId)?.title || null

  const closeChat = useCallback(() => {
    setIsChatOpen(false)
    chatTriggerRef.current?.focus()
  }, [])

  // Drawer do Tutor IA: foco inicial no botão de fechar e fechamento com Esc
  useEffect(() => {
    if (!isChatOpen) return
    chatCloseRef.current?.focus()
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeChat()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isChatOpen, closeChat])

  // Menu lateral em telas pequenas: fecha com Esc
  const showMobileSidebar = isMobile && isMobileSidebarOpen
  useEffect(() => {
    if (!showMobileSidebar) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsMobileSidebarOpen(false)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [showMobileSidebar])

  const isEffectivelyCollapsed = isSidebarCollapsed && !isMobile
  const mobileTitle = activeWorkspaceBlock ? activeWorkspaceBlock.title : pageTitles[currentPage]

  return (
    <div className={`app-layout-root ${isEffectivelyCollapsed ? 'sidebar-collapsed' : ''}`}>
      {/* 1. Sidebar Fixa e Preservada */}
      <Sidebar
        currentPage={currentPage}
        onSelectPage={(page) => {
          setActiveWorkspaceBlock(null)
          setCurrentPage(page)
          setIsMobileSidebarOpen(false)
        }}
        isCollapsed={isEffectivelyCollapsed}
        isMobileOpen={showMobileSidebar}
        onToggleCollapse={() => {
          if (isMobile) {
            setIsMobileSidebarOpen(false)
          } else {
            setIsSidebarCollapsed((prev) => !prev)
          }
        }}
        blocks={blocks.filter((b) => b.status === 'ACTIVE').map((b) => ({
          id: b.id,
          title: b.title,
          sourceCount: materialsCountByBlock[b.id] ?? 0,
        }))}
        selectedBlockId={selectedBlockId}
        onSelectBlockId={(id) => {
          setSelectedBlockId(id)
          const found = blocks.find((b) => b.id === id)
          if (found && currentPage === 'study-blocks') {
            setActiveWorkspaceBlock(found)
          }
        }}
        dueReviewsCount={globalDueReviews.length}
      />

      {showMobileSidebar && (
        <div
          className="mobile-sidebar-backdrop"
          onClick={() => setIsMobileSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* 2. Área de Conteúdo Principal (Ocupa 100% do espaço restante sem divisão lateral de chat) */}
      <main className="main-content-viewport">
        {isMobile && (
          <div className="mobile-topbar">
            <IconButton
              label="Abrir menu de navegação"
              aria-expanded={showMobileSidebar}
              aria-controls="app-sidebar"
              onClick={() => setIsMobileSidebarOpen(true)}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                <line x1="4" y1="6" x2="20" y2="6" />
                <line x1="4" y1="12" x2="20" y2="12" />
                <line x1="4" y1="18" x2="20" y2="18" />
              </svg>
            </IconButton>
            <span className="mobile-topbar-title">{mobileTitle}</span>
          </div>
        )}

        {error && (
          <div className="error-banner" role="alert">
            <span>{error}</span>
            <button
              type="button"
              className="banner-dismiss"
              aria-label="Fechar aviso"
              onClick={() => setError(null)}
            >
              ✕
            </button>
          </div>
        )}

        {/* Exibição condicional das páginas */}
        {activeWorkspaceBlock ? (
          <BlockWorkspacePage
            block={activeWorkspaceBlock}
            onBack={() => setActiveWorkspaceBlock(null)}
            onNavigateToAddContent={(bId) => {
              setSelectedBlockId(bId)
              setActiveWorkspaceBlock(null)
              setCurrentPage('add-content')
            }}
            onNavigateToPractical={() => {
              setActiveWorkspaceBlock(null)
              setCurrentPage('practical-block')
            }}
            onNavigateToTheoretical={() => {
              setActiveWorkspaceBlock(null)
              setCurrentPage('theoretical-block')
            }}
            onNavigateToTopics={() => {
              setActiveWorkspaceBlock(null)
              setCurrentPage('source-mapping')
            }}
          />
        ) : (
          <>
            {currentPage === 'study-blocks' && (
              <StudyBlocksPage
                blocks={blocks}
                selectedBlockId={selectedBlockId}
                onSelectBlock={(id) => setSelectedBlockId(id)}
                onOpenBlockWorkspace={(block) => {
                  setSelectedBlockId(block.id)
                  setActiveWorkspaceBlock(block)
                }}
                onSaveBlock={handleSaveBlock}
                onArchiveBlock={handleArchiveBlock}
                onUnarchiveBlock={handleUnarchiveBlock}
                onNavigateToAddContent={(blockId) => {
                  if (blockId) setSelectedBlockId(blockId)
                  setCurrentPage('add-content')
                }}
                materialsCountByBlock={materialsCountByBlock}
                questionsCountByBlock={questionsCountByBlock}
              />
            )}

            {currentPage === 'global-performance' && (
              <div className="page-wrapper">
                <GlobalPerformancePanel
                  onSelectBlock={(blockId) => {
                    setSelectedBlockId(blockId)
                    const found = blocks.find((b) => b.id === blockId)
                    if (found) {
                      setActiveWorkspaceBlock(found)
                      setCurrentPage('study-blocks')
                    }
                  }}
                />
              </div>
            )}

            {currentPage === 'review-difficulties' && (
              <div className="page-wrapper">
                <GlobalReviewsBatteryPanel
                  onReviewCompleted={() => {
                    void loadBlocks()
                    void loadGlobalReviews()
                  }}
                />
              </div>
            )}

            {currentPage === 'add-content' && (
              <AddContentPage
                blocks={blocks}
                selectedBlockId={selectedBlockId}
                onSelectBlock={(id) => setSelectedBlockId(id)}
                onSourceAdded={(bId) => {
                  void loadBlockCounts(bId)
                  void loadMaterials()
                }}
                onNavigateToBlock={(bId) => {
                  setSelectedBlockId(bId)
                  const found = blocks.find((b) => b.id === bId)
                  if (found) {
                    setActiveWorkspaceBlock(found)
                    setCurrentPage('study-blocks')
                  }
                }}
              />
            )}

            {currentPage === 'practical-block' && (
              <PracticalExercisesPage
                mode="block"
                blocks={blocks}
                selectedBlockId={selectedBlockId}
                onSelectBlock={(id) => setSelectedBlockId(id)}
              />
            )}

            {currentPage === 'practical-global' && (
              <PracticalExercisesPage
                mode="global"
                blocks={blocks}
                selectedBlockId={selectedBlockId}
                onSelectBlock={(id) => setSelectedBlockId(id)}
              />
            )}

            {currentPage === 'theoretical-block' && (
              <TheoreticalQuestionsPage
                mode="block"
                blocks={blocks}
                selectedBlockId={selectedBlockId}
                onSelectBlock={(id) => setSelectedBlockId(id)}
              />
            )}

            {currentPage === 'theoretical-global' && (
              <TheoreticalQuestionsPage
                mode="global"
                blocks={blocks}
                selectedBlockId={selectedBlockId}
                onSelectBlock={(id) => setSelectedBlockId(id)}
              />
            )}

            {currentPage === 'source-mapping' && (
              <div className="page-wrapper">
                {selectedBlockId ? (
                  <TopicsPanel
                    blockId={selectedBlockId}
                    questions={[]}
                    evidenceVersion={0}
                  />
                ) : (
                  <div className="card empty-state-box">
                    <h3>Selecione um Bloco de Estudo</h3>
                    <p>Escolha um dos blocos na barra lateral para visualizar o mapa conceitual de tópicos e dependências.</p>
                  </div>
                )}
              </div>
            )}

            {currentPage === 'goals-sessions' && (
              <GoalsSessionsPage
                blocks={blocks}
                selectedBlockId={selectedBlockId}
                onSelectBlock={(id) => setSelectedBlockId(id)}
              />
            )}

            {/* Módulo Carreira */}
            {currentPage === 'career-job-feed' && (
              <CareerJobFeedPage onNavigateToPreferences={() => setCurrentPage('career-preferences')} />
            )}
            {currentPage === 'career-preferences' && <CareerPreferencesPage />}
            {currentPage === 'career-skills' && <CareerSkillsPage />}
            {currentPage === 'career-sources' && <CareerSourcesPage />}
            {currentPage === 'career-agent-schedule' && <CareerAgentSchedulePage />}

            {currentPage === 'settings' && <SettingsPage onNavigateToGoals={() => setCurrentPage('goals-sessions')} />}
          </>
        )}
      </main>

      {/* 3. Botão Flutuante Discreto para o Tutor IA */}
      {/* Permanece montado (coberto pelo drawer quando aberto) para receber o foco ao fechar */}
      <button
        ref={chatTriggerRef}
        type="button"
        className="floating-chat-trigger"
        onClick={() => setIsChatOpen(true)}
        title="Abrir Tutor IA fundamentado nos seus materiais (NotebookLM)"
        aria-haspopup="dialog"
        aria-expanded={isChatOpen}
      >
        <span className="chat-trigger-icon" aria-hidden="true">💬</span>
        <span>Tutor IA</span>
        <span className="chat-trigger-status-dot" aria-hidden="true" />
      </button>

      {/* 4. Slide-over Drawer do Tutor IA (Abre por cima, sem espremer ou dividir a tela) */}
      {isChatOpen && (
        <div className="chat-drawer-backdrop" onClick={closeChat}>
          <div
            id="tutor-chat-drawer"
            className="chat-drawer-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="tutor-chat-drawer-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="chat-drawer-header">
              <div className="chat-drawer-heading">
                <span className="chat-drawer-heading-icon" aria-hidden="true">💬</span>
                <div style={{ minWidth: 0 }}>
                  <h3 id="tutor-chat-drawer-title" className="chat-drawer-title">
                    Tutor IA (NotebookLM)
                  </h3>
                  <small className="chat-drawer-context">
                    {selectedBlockTitle ? `Bloco: ${selectedBlockTitle}` : 'Livre / Multi-Bloco'}
                  </small>
                </div>
              </div>
              <button
                ref={chatCloseRef}
                type="button"
                className="chat-drawer-close-btn"
                onClick={closeChat}
                title="Fechar Chat"
                aria-label="Fechar Tutor IA"
              >
                ✕
              </button>
            </div>
            <div className="chat-drawer-body">
              <TutorChatPanel
                selectedBlockId={selectedBlockId}
                selectedBlockTitle={selectedBlockTitle}
                allBlocks={blocks.map((b) => ({ id: b.id, title: b.title }))}
                activeSources={materials as any}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default App
