import { useState } from 'react'
import {
  BarChartIcon,
  BriefcaseIcon,
  ChevronDownIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  CodeIcon,
  FileTextIcon,
  FolderIcon,
  LayersIcon,
  LogoIcon,
  MessageSquareIcon,
  NetworkIcon,
  PlusCircleIcon,
  SettingsIcon,
  TargetIcon,
} from './icons'

export type PageId =
  | 'global-performance'
  | 'review-difficulties'
  | 'study-blocks'
  | 'add-content'
  | 'practical-block'
  | 'practical-global'
  | 'theoretical-block'
  | 'theoretical-global'
  | 'source-mapping'
  | 'career-job-feed'
  | 'career-preferences'
  | 'career-skills'
  | 'career-sources'
  | 'career-agent-schedule'
  | 'forum'
  | 'settings'

export type BlockOption = {
  id: string
  title: string
  sourceCount?: number
}

type SidebarProps = {
  currentPage: PageId
  onSelectPage: (page: PageId) => void
  isCollapsed: boolean
  onToggleCollapse: () => void
  blocks: BlockOption[]
  selectedBlockId: string | null
  onSelectBlockId: (id: string | null) => void
  dueReviewsCount?: number
  /** Em telas pequenas, indica se a sidebar está aberta como menu deslizante. */
  isMobileOpen?: boolean
}

export function Sidebar({
  currentPage,
  onSelectPage,
  isCollapsed,
  onToggleCollapse,
  blocks,
  selectedBlockId,
  onSelectBlockId,
  dueReviewsCount = 0,
  isMobileOpen = false,
}: SidebarProps) {
  const [practicalOpen, setPracticalOpen] = useState(
    currentPage === 'practical-block' || currentPage === 'practical-global'
  )
  const [theoreticalOpen, setTheoreticalOpen] = useState(
    currentPage === 'theoretical-block' || currentPage === 'theoretical-global'
  )
  const [careerOpen, setCareerOpen] = useState(
    currentPage.startsWith('career-')
  )
  const [contextDropdownOpen, setContextDropdownOpen] = useState(false)

  const activeBlock = blocks.find((b) => b.id === selectedBlockId)
  const contextTitle = activeBlock ? activeBlock.title : 'Todos os Blocos'
  const contextSubtitle = activeBlock
    ? `${activeBlock.sourceCount ?? 0} fontes ativas`
    : `${blocks.length} blocos ativos`

  return (
    <aside
      id="app-sidebar"
      className={`sidebar-container ${isCollapsed ? 'collapsed' : 'expanded'} ${isMobileOpen ? 'mobile-open' : ''}`}
    >
      {/* 1. Cabeçalho */}
      <div className="sidebar-header">
        <div className="sidebar-brand">
          <div className="sidebar-logo-badge">
            <LogoIcon className="brand-logo-icon" />
          </div>
          {!isCollapsed && (
            <div className="sidebar-brand-text">
              <span className="brand-title">Estudos IA</span>
              <span className="brand-tag">PRO PLATFORM</span>
            </div>
          )}
        </div>
        <button
          type="button"
          className="collapse-toggle-btn"
          onClick={onToggleCollapse}
          title={isCollapsed ? 'Expandir barra lateral' : 'Recolher barra lateral'}
          aria-label={isCollapsed ? 'Expandir barra lateral' : 'Recolher barra lateral'}
        >
          {isCollapsed ? <ChevronRightIcon className="w-4 h-4" /> : <ChevronLeftIcon className="w-4 h-4" />}
        </button>
      </div>

      {/* 2. Seletor de Contexto de Estudo */}
      <div className="context-selector-wrapper">
        <button
          type="button"
          className={`context-card ${contextDropdownOpen ? 'active' : ''}`}
          onClick={() => setContextDropdownOpen(!contextDropdownOpen)}
          title={isCollapsed ? contextTitle : undefined}
          aria-expanded={!isCollapsed && contextDropdownOpen}
        >
          <div className="context-card-icon">
            <FolderIcon className="w-4 h-4" />
          </div>
          {!isCollapsed && (
            <div className="context-card-info">
              <span className="context-title">{contextTitle}</span>
              <span className="context-sub">{contextSubtitle}</span>
            </div>
          )}
          {!isCollapsed && (
            <div className={`context-chevron ${contextDropdownOpen ? 'open' : ''}`}>
              <ChevronDownIcon className="w-4 h-4" />
            </div>
          )}
        </button>

        {/* Dropdown de Seleção de Bloco */}
        {!isCollapsed && contextDropdownOpen && (
          <div className="context-dropdown-menu">
            <button
              type="button"
              className={`context-dropdown-item ${selectedBlockId === null ? 'selected' : ''}`}
              onClick={() => {
                onSelectBlockId(null)
                setContextDropdownOpen(false)
              }}
            >
              <div className="dropdown-item-title">Visão Global</div>
              <div className="dropdown-item-sub">Todos os blocos integrados</div>
            </button>

            <div className="context-dropdown-divider" />

            {blocks.map((block) => (
              <button
                key={block.id}
                type="button"
                className={`context-dropdown-item ${selectedBlockId === block.id ? 'selected' : ''}`}
                onClick={() => {
                  onSelectBlockId(block.id)
                  setContextDropdownOpen(false)
                }}
              >
                <div className="dropdown-item-title">{block.title}</div>
                <div className="dropdown-item-sub">{block.sourceCount ?? 0} fontes canônicas</div>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 3. Navegação Principal */}
      <nav className="sidebar-nav">
        {/* Item 1: Desempenho Global */}
        <button
          type="button"
          className={`nav-item ${currentPage === 'global-performance' ? 'active' : ''}`}
          onClick={() => onSelectPage('global-performance')}
          title={isCollapsed ? 'Desempenho Global' : undefined}
        >
          <BarChartIcon className="nav-item-icon" />
          {!isCollapsed && <span className="nav-item-text">Desempenho Global</span>}
        </button>

        {/* Item 2: Revisar Dificuldades */}
        <button
          type="button"
          className={`nav-item ${currentPage === 'review-difficulties' ? 'active' : ''}`}
          onClick={() => onSelectPage('review-difficulties')}
          title={isCollapsed ? 'Revisar Dificuldades' : undefined}
        >
          <TargetIcon className="nav-item-icon" />
          {!isCollapsed && <span className="nav-item-text">Revisar Dificuldades</span>}
          {!isCollapsed && dueReviewsCount > 0 && (
            <span className="badge-notification">{dueReviewsCount}</span>
          )}
        </button>

        {/* Item 3: Blocos de Estudo */}
        <button
          type="button"
          className={`nav-item ${currentPage === 'study-blocks' ? 'active' : ''}`}
          onClick={() => onSelectPage('study-blocks')}
          title={isCollapsed ? 'Blocos de Estudo' : undefined}
        >
          <LayersIcon className="nav-item-icon" />
          {!isCollapsed && <span className="nav-item-text">Blocos de Estudo</span>}
        </button>

        {/* Item 4: Adicionar Conteúdo (com badge NOVO) */}
        <button
          type="button"
          className={`nav-item ${currentPage === 'add-content' ? 'active' : ''}`}
          onClick={() => onSelectPage('add-content')}
          title={isCollapsed ? 'Adicionar Conteúdo' : undefined}
        >
          <PlusCircleIcon className="nav-item-icon" />
          {!isCollapsed && <span className="nav-item-text">Adicionar Conteúdo</span>}
          {!isCollapsed && <span className="badge-novo">NOVO</span>}
        </button>

        {/* Item 5: Exercícios Práticos (Acordeão) */}
        <div className="accordion-group">
          <button
            type="button"
            className={`nav-item accordion-trigger ${
              currentPage === 'practical-block' || currentPage === 'practical-global' ? 'parent-active' : ''
            }`}
            onClick={() => {
              if (isCollapsed) {
                onSelectPage('practical-block')
              } else {
                setPracticalOpen(!practicalOpen)
              }
            }}
            title={isCollapsed ? 'Exercícios Práticos' : undefined}
          >
            <CodeIcon className="nav-item-icon" />
            {!isCollapsed && <span className="nav-item-text">Exercícios Práticos</span>}
            {!isCollapsed && (
              <ChevronDownIcon className={`accordion-chevron ${practicalOpen ? 'open' : ''}`} />
            )}
          </button>

          {!isCollapsed && practicalOpen && (
            <div className="accordion-submenu">
              <button
                type="button"
                className={`submenu-item ${currentPage === 'practical-block' ? 'active' : ''}`}
                onClick={() => onSelectPage('practical-block')}
              >
                <span className="submenu-bullet" />
                <span>Por Bloco Específico</span>
              </button>
              <button
                type="button"
                className={`submenu-item ${currentPage === 'practical-global' ? 'active' : ''}`}
                onClick={() => onSelectPage('practical-global')}
              >
                <span className="submenu-bullet" />
                <span>Geral (Todos os Conteúdos)</span>
              </button>
            </div>
          )}
        </div>

        {/* Item 6: Exercícios Teóricos (Acordeão) */}
        <div className="accordion-group">
          <button
            type="button"
            className={`nav-item accordion-trigger ${
              currentPage === 'theoretical-block' || currentPage === 'theoretical-global' ? 'parent-active' : ''
            }`}
            onClick={() => {
              if (isCollapsed) {
                onSelectPage('theoretical-block')
              } else {
                setTheoreticalOpen(!theoreticalOpen)
              }
            }}
            title={isCollapsed ? 'Exercícios Teóricos' : undefined}
          >
            <FileTextIcon className="nav-item-icon" />
            {!isCollapsed && <span className="nav-item-text">Exercícios Teóricos</span>}
            {!isCollapsed && (
              <ChevronDownIcon className={`accordion-chevron ${theoreticalOpen ? 'open' : ''}`} />
            )}
          </button>

          {!isCollapsed && theoreticalOpen && (
            <div className="accordion-submenu">
              <button
                type="button"
                className={`submenu-item ${currentPage === 'theoretical-block' ? 'active' : ''}`}
                onClick={() => onSelectPage('theoretical-block')}
              >
                <span className="submenu-bullet" />
                <span>Por Bloco Específico</span>
              </button>
              <button
                type="button"
                className={`submenu-item ${currentPage === 'theoretical-global' ? 'active' : ''}`}
                onClick={() => onSelectPage('theoretical-global')}
              >
                <span className="submenu-bullet" />
                <span>Geral (Todos os Conteúdos)</span>
              </button>
            </div>
          )}
        </div>

        {/* Item 7: Mapeamento de Fontes */}
        <button
          type="button"
          className={`nav-item ${currentPage === 'source-mapping' ? 'active' : ''}`}
          onClick={() => onSelectPage('source-mapping')}
          title={isCollapsed ? 'Mapeamento de Fontes' : undefined}
        >
          <NetworkIcon className="nav-item-icon" />
          {!isCollapsed && <span className="nav-item-text">Mapeamento de Fontes</span>}
        </button>

        {/* Item 8: Módulo Carreira (Acordeão) */}
        <div className="accordion-group">
          <button
            type="button"
            className={`nav-item accordion-trigger ${
              currentPage.startsWith('career-') ? 'parent-active' : ''
            }`}
            onClick={() => {
              if (isCollapsed) {
                onSelectPage('career-job-feed')
              } else {
                setCareerOpen(!careerOpen)
              }
            }}
            title={isCollapsed ? 'Módulo Carreira' : undefined}
          >
            <BriefcaseIcon className="nav-item-icon" />
            {!isCollapsed && <span className="nav-item-text">Módulo Carreira</span>}
            {!isCollapsed && (
              <ChevronDownIcon className={`accordion-chevron ${careerOpen ? 'open' : ''}`} />
            )}
          </button>

          {!isCollapsed && careerOpen && (
            <div className="accordion-submenu">
              <button
                type="button"
                className={`submenu-item ${currentPage === 'career-job-feed' ? 'active' : ''}`}
                onClick={() => onSelectPage('career-job-feed')}
              >
                <span className="submenu-bullet" />
                <span>Feed de Vagas</span>
              </button>
              <button
                type="button"
                className={`submenu-item ${currentPage === 'career-preferences' ? 'active' : ''}`}
                onClick={() => onSelectPage('career-preferences')}
              >
                <span className="submenu-bullet" />
                <span>Filtros de Busca</span>
              </button>
              <button
                type="button"
                className={`submenu-item ${currentPage === 'career-skills' ? 'active' : ''}`}
                onClick={() => onSelectPage('career-skills')}
              >
                <span className="submenu-bullet" />
                <span>Minhas Competências</span>
              </button>
              <button
                type="button"
                className={`submenu-item ${currentPage === 'career-sources' ? 'active' : ''}`}
                onClick={() => onSelectPage('career-sources')}
              >
                <span className="submenu-bullet" />
                <span>Fontes de Vagas</span>
              </button>
              <button
                type="button"
                className={`submenu-item ${currentPage === 'career-agent-schedule' ? 'active' : ''}`}
                onClick={() => onSelectPage('career-agent-schedule')}
              >
                <span className="submenu-bullet" />
                <span>Agendamento do Agente</span>
              </button>
            </div>
          )}
        </div>

        {/* 4. Divisor e Seção Secundária COMUNIDADE */}
        <div className="sidebar-divider" />

        {!isCollapsed && <div className="category-header">COMUNIDADE</div>}

        <button
          type="button"
          className={`nav-item ${currentPage === 'forum' ? 'active' : ''}`}
          onClick={() => onSelectPage('forum')}
          title={isCollapsed ? 'Fórum da Comunidade' : undefined}
        >
          <MessageSquareIcon className="nav-item-icon" />
          {!isCollapsed && <span className="nav-item-text">Fórum</span>}
        </button>

        <button
          type="button"
          className={`nav-item ${currentPage === 'settings' ? 'active' : ''}`}
          onClick={() => onSelectPage('settings')}
          title={isCollapsed ? 'Configurações' : undefined}
        >
          <SettingsIcon className="nav-item-icon" />
          {!isCollapsed && <span className="nav-item-text">Configurações</span>}
        </button>
      </nav>
    </aside>
  )
}
