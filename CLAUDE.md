# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Plataforma pessoal de estudos com IA (estilo NotebookLM): blocos de estudo, fontes (arquivos, YouTube, páginas web),
chat fundamentado nas fontes ativas, geração e correção de questões, repetição espaçada e radar de vagas.
Responda sempre em português do Brasil.

## Comandos

Windows/PowerShell. Banco: PostgreSQL 17 via `docker compose up -d postgres` (porta 5432, usuário `estudos`).

```powershell
# Backend (backend/) — porta 8080, aplica as migrações Flyway ao subir
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test                                   # todos os testes (usa o banco estudos_test)
.\mvnw.cmd test "-Dtest=CareerModuleIntegrationTest"          # uma classe
.\mvnw.cmd test "-Dtest=CareerModuleIntegrationTest#testPreferencesCrud"  # um método
.\mvnw.cmd -o test                                # offline (ver nota de TLS abaixo)

# Frontend (frontend/) — porta 5173, proxy /api → 8080
npm run dev
npm run lint        # oxlint
npm run build       # tsc -b && vite build

# Ambos em janelas separadas
.\start-dev.ps1     # ou start-dev.bat
```

- O banco de teste precisa existir uma vez:
  `docker exec programa-estudos-ia-postgres psql -U estudos -d postgres -c "CREATE DATABASE estudos_test OWNER estudos;"`.
  Os testes de integração apagam tabelas: nunca aponte o perfil `test` para o banco `estudos`.
- Com o Cloudflare WARP ativo, o Maven falha em TLS ao baixar dependências: use
  `$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'` uma vez e depois `-o`.
- A máquina tem pouca RAM livre: o Surefire roda com heap limitado (`backend/pom.xml`). Redirecione logs longos de
  Maven/npm para arquivo e leia só o resumo; não rode vários builds pesados ao mesmo tempo.

## Arquitetura

**Backend** (`backend/`, Java 21, Spring Boot 4, JPA, Flyway): pacotes por domínio em
`br.com.estudos.estudos_backend` — `studyblock`, `material`, `chat`, `question`, `exercise`, `knowledge`,
`learning`, `goal`, `studysession`, `career`, `ai`, `security`, `config`. A maioria dos pacotes é plana
(entidade + repository + service + controller); `career` segue portas e adaptadores (`adapter/in/web`,
`adapter/out/persistence`, `domain`, `service`). Migrações em `src/main/resources/db/migration` (`V1`…`V25`);
mudança de schema sempre em migração nova.

- **Usuário:** sem login. `security/CurrentUserResolver` lê o cabeçalho `X-Study-User-Id` (o frontend não envia;
  cai em `default-user`). Todo dado é filtrado pelo usuário resolvido ali — use-o em services novos.
- **IA:** todo uso passa pela porta `chat/AiTutorProvider`. O bean principal é `ai/AiProviderRouter`, que escolhe o
  provedor por `AiOperation` (OpenAI, Anthropic, Gemini), aplica fallback e limite diário e registra consumo
  (`ai_usage_records`); a configuração fica em banco e é editada na tela Configurações › Provedores de IA. Chaves
  só em variáveis de ambiente (`.env` na raiz). Sem chave, os endpoints de IA respondem 503 e o resto funciona.
  Chamadas de IA ficam fora de `@Transactional`; conteúdo de fontes/vagas/respostas vai delimitado como dado
  (proteção contra prompt injection). Vários recursos têm fallback sem IA (regras/extrativo).
- **Fontes:** `material/` importa arquivos (`MaterialFileStorage`: PDF, DOCX, PPTX, código-fonte…), transcrições do
  YouTube e páginas web; só fontes com chat ativo entram no contexto da IA.
- **Desempenho:** percentuais, notas e médias são calculados no backend (`learning/`, `question/`); a IA só gera
  conteúdo e avalia/interpreta.
- **Carreira:** agente agendado (`@EnableScheduling` via `config/SchedulingConfiguration`, `app.scheduling.enabled`)
  busca vagas em Remotive/Arbeitnow e calcula compatibilidade de forma determinística contra os blocos de estudo.

**Testes:** integração com `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` contra o
PostgreSQL real; serviços de IA testados com `AiTutorProvider` mockado. Nenhum teste chama APIs externas reais.

**Frontend** (`frontend/`, React 19 + TypeScript + Vite): sem React Router — `App.tsx` guarda a página atual e o
bloco selecionado, e `components/Sidebar.tsx` define o tipo das páginas e o menu. Chamadas HTTP via
`api/http.ts` (`request<T>()`, lança `ApiError` com o `message` do backend). Telas em `pages/` (inclusive
`pages/career/`) e `features/`. Estilo: tokens em `styles/tokens.css` (sem cores hexadecimais nas telas),
componentes em `styles/components.css` (todo `<button>` precisa de classe `btn …`), CSS por página em
`styles/pages/*.css`, componentes base em `components/ui/`.

## Documentos de referência

- `docs/VISAO-PRODUTO.md` — visão do produto e plano de etapas em execução (diretrizes obrigatórias).
- `docs/PLANO-EXECUCAO.md` — estado de cada etapa; referência para retomar uma sessão interrompida.
- `docs/DECISOES-PENDENTES.md` — decisões que aguardam o usuário.
- `docs/AUDITORIA.md` — problemas conhecidos; marque nele o que for resolvido.

## Regras de trabalho

Resumo das regras gerais do `docs/VISAO-PRODUTO.md` (leia o documento antes de começar uma etapa):

- Executar as etapas na ordem, atualizando `docs/PLANO-EXECUCAO.md` (`[ ]`, `[x]`, `[!]`) ao fim de cada uma.
- Ler o código envolvido e confirmar rotas, métodos HTTP e formatos nos controllers antes de editar.
- Mudanças pequenas e focadas; não alterar regras de negócio além do pedido, não remover funcionalidades nem dados.
- Ao fim de cada etapa: `.\mvnw.cmd test` no backend; `npm run lint`, `npm run build` (e `npm run test:run`, quando
  existir) no frontend. Só avançar com tudo passando.
- Um commit por etapa (ou subitem), Conventional Commits em português. Sem push (ver D1 em `DECISOES-PENDENTES.md`:
  há um hook Stop que faz commit e push automáticos).
- Nenhuma chave real em código, testes ou commits; nenhum teste chama APIs externas reais.
- Pontos de decisão: não implementar; registrar em `docs/DECISOES-PENDENTES.md` (contexto, opções, prós/contras,
  recomendação), marcar `[!]` e seguir.
- Etapas que pedem plano: escrever o plano resumido no `docs/PLANO-EXECUCAO.md` antes de codificar.
