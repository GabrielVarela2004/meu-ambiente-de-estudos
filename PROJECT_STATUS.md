# Status do projeto

## Etapa atual

Aplicação 100% concluída para todos os requisitos funcionais essenciais planejados — validada com persistência real em PostgreSQL e testes automatizados.

## Tarefa atual

Conclusão das 3 entregas finais:
1. Extração e importação segura de arquivos `.pdf` e `.docx` (`MaterialFileStorage.java`).
2. Geração assistida de questões por IA a partir dos materiais ativos do bloco (`AiQuestionGeneratorService.java` e `POST /api/study-blocks/{studyBlockId}/questions/generate`).
3. Pré-requisitos entre tópicos no Mapa do Conhecimento (`V14__add_topic_prerequisites.sql`, validação contra ciclos e alerta de pré-requisito não consolidado em `KnowledgeTopicService.java` e `TopicsPanel.tsx`).

## Tarefas concluídas

- Fase 0.1: inspeção do diretório e das configurações existentes.
- Consolidação inicial dos requisitos funcionais e não funcionais.
- Avaliação preliminar de tecnologias.
- Proposta de monólito modular com frontend React/TypeScript e backend Java/Spring Boot.
- Definição preliminar da estratégia de persistência, IA, documentos e testes.
- Criação desta documentação inicial.
- Aprovação da base tecnológica e das decisões iniciais da Fase 1.
- Instalação do JDK 21, Node.js LTS e Docker Desktop.
- Inicialização do Git sem criação de commit.
- Criação do backend Spring Boot com Maven Wrapper.
- Criação do frontend React/TypeScript com Vite.
- Configuração inicial do Docker Compose para PostgreSQL.
- Configuração de variáveis de ambiente de exemplo.
- Substituição da tela padrão do Vite por uma tela inicial sem funcionalidades fictícias.
- Habilitação do WSL 2 e Virtual Machine Platform.
- Atualização do kernel do WSL 2.
- Inicialização do Docker Desktop e do engine Linux.
- Inicialização do PostgreSQL via Docker Compose.
- Configuração da conexão PostgreSQL no backend.
- Configuração do Flyway e criação da migração de infraestrutura V1.
- Configuração explícita do ciclo de migração Flyway no backend.
- Implementação inicial do módulo de blocos de estudo com persistência e API REST.
- Implementação de arquivamento controlado e validação de entrada para blocos.
- Implementação da interface web de blocos com proxy Vite para a API.
- Implementação de criação, edição, listagem de ativos/arquivados e arquivamento pela interface.
- Implementação do módulo de materiais de texto vinculado aos blocos.
- Migração V3 e API para criação, edição, listagem e arquivamento de materiais.
- Implementação do módulo de questões de múltipla escolha.
- Migração V4 e API para criação, listagem, arquivamento e resposta de questões.
- Migração V5 e API para persistência de tentativas e estatísticas por bloco.
- Migração V6 e API para agendamento básico de revisão espaçada.
- Integração do chat real com OpenAI, protegida por variável de ambiente no backend.
- Importação de arquivos `.txt` e `.md` com armazenamento local privado.
- Contexto seguro de materiais ativos no tutor, limitado por tamanho e separado das instruções do sistema.
- Sessões de estudo persistidas por bloco, com transições controladas de estado.
- Exercícios práticos com tentativas textuais e autoavaliação persistida.
- Metas de estudo por bloco com progresso e conclusão automática.
- Histórico de desempenho com estatísticas filtráveis por período.
- Revisão espaçada progressiva baseada em sequência de acertos, com limite de 365 dias.
- Correção de BOM UTF-8 em materiais importados.
- Controles de arquivamento de questões, exercícios e metas na interface.
- Área global "Revisar minhas dificuldades" (`/api/reviews/due`) com ordenação por prioridade de dificuldade, justificativa explícita da recomendação e reagendamento manual (`/api/reviews/questions/{id}/reschedule`).
- Diferenciação clara na interface quando não há amostra suficiente de respostas (`0 tentativas`), evitando confundir ausência de dados com 0% de desempenho.
- Isolamento do banco de dados de testes (`estudos_test` com perfil `@ActiveProfiles("test")`), garantindo que o método `cleanDatabase` dos testes nunca apague os dados reais do banco principal `estudos`.
- Migração V11 (`knowledge_topics` e `knowledge_topic_questions`) e módulo `knowledge` para criação de tópicos e subtópicos (2 níveis), vínculo com questões, bloqueio de arquivamento de tópico pai com subtópicos ativos e cálculo determinístico de estados de aprendizagem (`NOT_STUDIED`, `INSUFFICIENT_DATA`, `LEARNING`, `NEEDS_REVIEW`, `CONSISTENT`).
- Componente dedicado `TopicsPanel.tsx` e cliente HTTP compartilhado `src/api/http.ts` no frontend.
- Migração V12 (`chat_messages`), interface `AiTutorProvider` + adaptador `OpenAiTutorProvider`, persistência do histórico de conversas por bloco com materiais referenciados, endpoint de status da integração (`GET /api/chat/status`) e componente `TutorChatPanel.tsx`.
- Migração V13 (`difficulty`, `question_format`, `expected_answer` em `study_questions` e `discursive_answer` em `study_question_attempts`), suporte completo aos formatos `MULTIPLE_CHOICE`, `TRUE_FALSE` e `DISCURSIVE` e níveis `EASY`, `INTERMEDIATE` e `HARD` no backend e na interface.
- Importação e extração segura de documentos `.pdf` (Apache PDFBox 3.0.3) e `.docx` (leitura segura de `word/document.xml` com proteção contra XXE e Zip Bomb) em `MaterialFileStorage.java`.
- Geração assistida de questões por IA (`AiQuestionGeneratorService.java` e `POST /api/study-blocks/{studyBlockId}/questions/generate`) com suporte aos 3 formatos e 3 níveis de dificuldade baseados exclusivamente nos materiais ativos do bloco.
- Migração V14 (`prerequisite_topic_id` em `knowledge_topics`), detecção de ciclos de pré-requisito e alerta automático quando o tópico pré-requisito não está no estado `CONSISTENT`.

## Tarefas em andamento

- Nenhuma tarefa pendente. Todos os módulos planejados foram implementados e testados.

## Funcionalidades implementadas

- **Blocos de Estudo**: CRUD completo, listagem de ativos/arquivados e arquivamento controlado.
- **Materiais de Estudo e Extração de Documentos**:
  - Criação manual em texto, edição e arquivamento.
  - Importação e extração de texto de arquivos `.txt`, `.md` (até 1 MB, com remoção de BOM UTF-8), `.pdf` e `.docx` (até 5 MB, com proteção contra PDFs criptografados, XML externo/XXE e Zip Bomb).
- **Questões, Avaliações e Geração por IA**:
  - Formatos suportados: **Múltipla escolha** (`MULTIPLE_CHOICE`, 2 a 6 opções, 1 correta), **Verdadeiro ou falso** (`TRUE_FALSE`, 2 opções, 1 correta) e **Discursiva** (`DISCURSIVE`, com `expectedAnswer` obrigatório para avaliação por critérios explícitos).
  - Níveis de dificuldade suportados: **Fácil** (`EASY`), **Intermediária** (`INTERMEDIATE`) e **Difícil** (`HARD`).
  - Geração assistida de questões por IA (`POST /api/study-blocks/{studyBlockId}/questions/generate`) baseada nos materiais ativos do bloco.
  - Correção determinística para questões objetivas e verificação transparente de critérios-chave para questões discursivas, com persistência completa das tentativas (`option_id` ou `discursive_answer`).
- **Tópicos, Pré-requisitos e Estados de Aprendizagem (Mapa do Conhecimento)**:
  - Cadastro de tópicos e subtópicos por bloco, definição de pré-requisitos com proteção contra ciclos, vínculo de questões aos tópicos, alerta quando o pré-requisito ainda não está consolidado e avaliação automática do estado de aprendizagem baseada em evidências reais.
- **Desempenho e Estatísticas**: Estatísticas acumuladas e filtradas por período (7, 30 e 90 dias) com distinção explícita de ausência de dados.
- **Revisão Espaçada e "Revisar minhas dificuldades"**: Agendamento progressivo baseado em sequência de acertos (`correctStreak`), área global "Revisar minhas dificuldades" com motivo da recomendação, suporte a resposta objetiva ou discursiva e reagendamento manual.
- **Sessões de Estudo**: Planejamento em minutos, início, conclusão e cancelamento persistidos.
- **Exercícios Práticos**: Criação com enunciado e dicas, registro de tentativas textuais e autoavaliação de 1 a 5.
- **Metas de Estudo**: Criação com unidades-alvo, incremento de progresso e conclusão automática ao atingir a meta.
- **Chat Modular com IA e Histórico Persistido**:
  - Porta `AiTutorProvider` desacoplada com implementação `OpenAiTutorProvider`.
  - Consulta de status da integração (`GET /api/chat/status`) sem expor segredos.
  - Persistência das conversas na tabela `chat_messages` (`GET /api/study-blocks/{id}/chat/messages`), incluindo provedor, modelo e títulos dos materiais ativos consultados na ocasião.

## Testes executados

- Backend: `.\mvnw.cmd test` com JDK 21 e PostgreSQL 17 em container Docker (usando o banco isolado `estudos_test`).
- Total de 26 testes automatizados:
  - **15 testes de integração (`EstudosBackendApplicationTests`)** cobrindo blocos, sessões, exercícios, metas, materiais (`.txt`, `.md`, `.pdf` e `.docx`), questões nos formatos Múltipla Escolha, Verdadeiro/Falso e Discursiva com níveis de dificuldade, revisão espaçada global, tópicos/subtópicos/pré-requisitos, status do provedor de IA e histórico persistido de chat.
  - **9 testes unitários (`TopicLearningStateEvaluatorTest`)** cobrindo todos os critérios de transição de estado de aprendizagem.
  - **1 teste unitário (`OpenAiChatServiceTest`)** validando a injeção segura de materiais de referência no prompt e a gravação de `ChatConversationEntry` com referências verificáveis.
  - **1 teste unitário (`AiQuestionGeneratorServiceTest`)** validando a geração e persistência de questões estruturadas via `AiTutorProvider`.
- Frontend: `npm run lint` e `npm run build`.

## Resultados dos testes

- **Backend**: `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS` (Migrações Flyway V1 a V14 validadas e aplicadas em `estudos_test`).
- **Frontend Build**: Aprovado (`tsc -b && vite build` concluído sem erros em 272 ms).
- **Frontend Lint**: Aprovado com `0 errors`.

## Problemas conhecidos e limitações

- O chat e a geração de questões por IA requerem a variável `OPENAI_API_KEY` definida no ambiente para realizar chamadas reais à OpenAI; sem ela, o backend retorna HTTP 503 com mensagem clara e sem simular respostas falsas.
- OCR para PDFs compostos exclusivamente por imagens digitalizadas e execução de código de usuário não estão habilitados por segurança.
