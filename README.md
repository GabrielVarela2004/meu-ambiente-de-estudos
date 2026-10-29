# Plataforma de Estudos com Inteligência Artificial

## Objetivo

Plataforma pessoal para organizar estudos em blocos independentes, importar materiais (`.txt`, `.md`, `.pdf`, `.docx`), conversar com provedores de inteligência artificial, praticar com questões e exercícios, acompanhar desempenho, revisar dificuldades, mapear tópicos e pré-requisitos e planejar sessões de estudo.

O projeto segue desenvolvimento incremental com persistência real em PostgreSQL e validação automatizada a cada etapa.

## Estado do projeto

### Implementado (100% dos módulos planejados)

- Backend Java 21 / Spring Boot com PostgreSQL 17, Flyway (migrações V1 a V14) e API REST validada (com banco isolado `estudos_test` para testes).
- Frontend React 19 / TypeScript / Vite integrado à API via proxy.
- **Blocos de estudo**: Criação, edição, listagem de ativos/arquivados e arquivamento controlado.
- **Materiais de estudo e documentos**: Cadastro em texto, edição, arquivamento e importação de arquivos `.txt`, `.md` (até 1 MB, com remoção de UTF-8 BOM), `.pdf` e `.docx` (até 5 MB, com extração de texto segura e armazenamento local privado).
- **Questões, níveis de dificuldade e geração por IA**:
  - Formatos **Múltipla escolha** (`MULTIPLE_CHOICE`, 2 a 6 alternativas), **Verdadeiro ou falso** (`TRUE_FALSE`, 2 alternativas) e **Discursiva** (`DISCURSIVE`, com critérios/gabarito esperado).
  - Níveis de dificuldade **Fácil** (`EASY`), **Intermediária** (`INTERMEDIATE`) e **Difícil** (`HARD`).
  - Geração assistida de questões por IA (`POST /api/study-blocks/{id}/questions/generate`) a partir dos materiais ativos do bloco.
  - Correção determinística para questões objetivas e avaliação por critérios explícitos para questões discursivas.
- **Mapa do Conhecimento (Tópicos, Pré-requisitos e Estados de Aprendizagem)**:
  - Criação de tópicos e subtópicos (2 níveis), definição de pré-requisitos entre tópicos com bloqueio de dependência circular, alerta quando o pré-requisito ainda não está consolidado, associação com questões e cálculo automático do estado de aprendizagem (`NOT_STUDIED`, `INSUFFICIENT_DATA`, `LEARNING`, `NEEDS_REVIEW`, `CONSISTENT`) baseado em evidências reais.
- **Desempenho e estatísticas**: Histórico persistido de tentativas, aproveitamento acumulado e filtro por períodos (7, 30 e 90 dias), diferenciando explicitamente ausência de dados (`0 tentativas`) de desempenho baixo.
- **Revisão espaçada e área global "Revisar minhas dificuldades"**:
  - Algoritmo progressivo baseado em sequência de acertos (`correctStreak`) até 365 dias; erros reiniciam o intervalo em 1 dia.
  - Painel global **Revisar minhas dificuldades** (`/api/reviews/due`) que lista questões pendentes de todos os blocos ativos ordenadas por prioridade, exibe a justificativa da recomendação, permite responder diretamente (objetiva ou discursiva) e oferece reagendamento manual (adiar 1 ou 3 dias).
- **Sessões de estudo**: Planejamento por duração e notas, início, conclusão e cancelamento persistidos.
- **Exercícios práticos**: Enunciado, dicas, registro de tentativas textuais e autoavaliação de 1 a 5.
- **Metas de estudo**: Unidades-alvo, registro de progresso e conclusão automática.
- **Chat modular com IA e histórico persistido**:
  - Porta `AiTutorProvider` desacoplada com adaptador `OpenAiTutorProvider` protegido por variável de ambiente no backend.
  - Persistência do histórico de conversas por bloco (`chat_messages`) com indicação explícita dos materiais de referência consultados.

## Tecnologias utilizadas

- **Backend**: Java 21 LTS, Spring Boot 4, Spring Data JPA, Validation, Flyway, Apache PDFBox.
- **Frontend**: React 19, TypeScript, Vite.
- **Banco de Dados**: PostgreSQL 17 (via Docker Compose, bancos `estudos` e `estudos_test`).
- **Testes**: JUnit 5, Mockito, Spring MockMvc, Oxlint.

## Pré-requisitos

- JDK 21.
- Node.js LTS.
- Docker Desktop (para o container PostgreSQL).
- Git.

## Configuração e execução

### 1. Banco de dados (PostgreSQL)

```powershell
docker compose up -d postgres
```

### 2. Executar testes do backend

```powershell
cd backend
.\mvnw.cmd test
```

### 3. Validar e compilar o frontend

```powershell
cd frontend
cmd /c "npm run lint && npm run build"
```

### 4. Variáveis de ambiente e cuidados com credenciais

Copie `.env.example` ou defina a variável de ambiente `OPENAI_API_KEY` no sistema/IDE antes de iniciar o backend para habilitar respostas reais no chat com IA e na geração de questões com IA. Sem a chave configurada, o sistema funciona normalmente e informa de maneira transparente que a integração com IA está indisponível (HTTP 503). Nunca inclua chaves de API diretamente no código-fonte ou em commits.
