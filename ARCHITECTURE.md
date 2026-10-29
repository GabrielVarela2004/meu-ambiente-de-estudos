# Arquitetura

## Estado da decisão

A arquitetura proposta para a primeira implementação é um monólito modular com frontend web separado logicamente do backend e princípios de Ports and Adapters. Microsserviços não serão adotados inicialmente, pois aumentariam a complexidade operacional antes de existirem fluxos funcionais estáveis.

As decisões iniciais foram aprovadas: Java 21 e Spring Boot, React/TypeScript/Vite, PostgreSQL via Docker Compose, OpenAI como primeiro provedor, uso local para um único usuário sem login inicial e armazenamento local de arquivos por meio de uma abstração. A inicialização do Git foi autorizada sem criação automática de commit.

## Tecnologias propostas

### Backend

- Java 21 LTS.
- Spring Boot.
- Spring Web e Validation.
- Spring Data JPA.
- Spring Security preparado para evolução.
- Flyway para migrações.

Java foi priorizado porque o projeto já é reconhecido pelo IntelliJ como um módulo Java genérico, oferece tipagem forte e possui ecossistema maduro para APIs, persistência, segurança e testes.

### Frontend

- React.
- TypeScript.
- Vite.
- React Router.
- Componentes acessíveis e reutilizáveis.

### Persistência

- PostgreSQL como banco principal.
- Armazenamento de arquivos abstraído por uma porta `FileStorage`.
- Armazenamento local apenas para desenvolvimento inicial, se aprovado.
- `pgvector` como opção para embeddings quando a recuperação semântica for implementada.

## Organização lógica

```text
Interface/API
    ↓
Aplicação
    ↓
Domínio
    ↓
Portas
    ↓
Adaptadores de persistência e integrações externas
```

### Interface/API

Controllers REST, DTOs, validação de entrada, respostas HTTP e tratamento padronizado de erros.

### Aplicação

Casos de uso, orquestração, transações e coordenação entre domínio, persistência e integrações.

### Domínio

Entidades, objetos de valor e regras independentes de framework, interface ou fornecedor externo.

### Adaptadores

Implementações para PostgreSQL, armazenamento de arquivos, processamento de documentos, provedores de IA e mecanismos de recuperação.

## Módulos previstos

```text
backend/
├── studyblock
├── material
├── documentprocessing
├── conversation
├── ai
├── question
├── exercise
├── performance
├── review
├── knowledge
├── studysession
├── goal
├── planning
└── common
```

Cada módulo deverá possuir somente componentes necessários ao seu domínio. A separação não deverá criar camadas vazias ou abstrações sem uso real.

## Fluxos principais

### Importação de material

```text
Upload ou texto
    ↓
Validação do arquivo e do bloco
    ↓
Armazenamento da versão
    ↓
Extração do conteúdo
    ↓
Divisão em trechos com metadados de origem
    ↓
Identificação ou associação de tópicos
    ↓
Status persistido do processamento
```

Falhas devem ser registradas explicitamente. OCR só será considerado suportado após implementação e testes reais.

### Chat fundamentado em materiais

```text
Pergunta
    ↓
Validação do bloco e dos materiais autorizados
    ↓
Recuperação de trechos relevantes
    ↓
Construção de contexto limitado
    ↓
Aplicação das instruções da aplicação
    ↓
Chamada ao provedor selecionado
    ↓
Validação da resposta e das referências
    ↓
Persistência da conversa
```

O conteúdo importado é dado não confiável. Ele não poderá substituir ou alterar as instruções de sistema da aplicação.

## Estratégia de persistência

As entidades previstas incluem:

- Blocos de estudo.
- Materiais, versões e trechos.
- Tópicos, subtópicos e relações.
- Conversas, mensagens e referências.
- Questões, alternativas, respostas e tentativas.
- Exercícios e tentativas.
- Registros de desempenho e dificuldades.
- Revisões.
- Sessões e etapas.
- Metas e atividades planejadas.
- Configurações de provedores e modelos.
- Registros de auditoria.

Materiais deverão ser versionados. Respostas, avaliações e referências devem apontar para a versão do material utilizada, evitando alteração histórica silenciosa. Arquivamento ou exclusão lógica deverá ser usado quando a exclusão física ameaçar a integridade do histórico.

Migrações serão versionadas com Flyway. Índices, paginação e consultas específicas serão considerados conforme os fluxos forem implementados.

## Abstração de IA

As funcionalidades dependerão de interfaces internas, não de SDKs espalhados pelo domínio. A camada deverá contemplar:

- Geração de texto.
- Geração estruturada.
- Embeddings.
- Consulta de modelos.
- Limites de contexto.
- Timeout.
- Retentativas com backoff.
- Classificação de erros.
- Registro técnico sem segredos.

Implementações de provedores serão adaptadores substituíveis. Fallback automático será opcional, configurável e informado ao usuário.

## Processamento e recuperação

Apache Tika, PDFBox e Apache POI serão avaliados para os formatos suportados. O pipeline deverá validar tipo e tamanho, extrair texto, preservar página/seção/posição quando possível, dividir documentos extensos e registrar falhas.

A recuperação deverá filtrar por bloco e materiais autorizados, combinar relevância textual e, quando aprovado, semântica vetorial. O sistema não deverá enviar documentos inteiros ao modelo sem necessidade.

## Segurança

- Segredos somente em variáveis de ambiente ou mecanismo seguro.
- Validação de uploads, tamanho, tipo e conteúdo.
- Armazenamento de arquivos fora da área pública.
- Separação entre instruções do sistema e conteúdo externo.
- Mensagens de erro sem dados sensíveis.
- Controle de acesso por bloco quando houver múltiplos usuários.
- Nenhuma execução de código de usuário no ambiente principal.
- Execução de código somente após infraestrutura isolada, com limites de recursos e autorização arquitetural.

## Estratégia de testes

- Unitários para regras de domínio e cálculos.
- Integração para banco, migrações, arquivos e API.
- Contrato para respostas estruturadas e API.
- Frontend para componentes, navegação e estados.
- Segurança para uploads, credenciais, conteúdo externo e isolamento.
- End-to-end para fluxos completos depois que houver funcionalidades reais.

## Estrutura prevista do repositório

```text
backend/
frontend/
docs/
README.md
ARCHITECTURE.md
PROJECT_STATUS.md
.env.example
.gitignore
```

A estrutura física será criada somente na fase de preparação, após aprovação das decisões pendentes.

## Decisões pendentes

- Escopo inicial de usuário único ou preparação imediata para múltiplos usuários.
- Primeiro provedor de IA.
- Estratégia inicial de armazenamento de arquivos.
- Execução local do PostgreSQL ou Docker.
- Estratégia de autenticação.
- Biblioteca visual do frontend.
- Formatos priorizados na primeira entrega.
- Necessidade inicial de busca vetorial.
