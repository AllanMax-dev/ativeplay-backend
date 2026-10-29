# Ative Play — API

API REST da plataforma Ative Play: gestão operacional multi-tenant para escritórios de BPO Financeiro e Contabilidade.

| | |
| --- | --- |
| Linguagem | Java 21 (LTS) |
| Framework | Spring Boot 3.5 (Web, Data JPA, Security, Validation, Actuator) |
| Banco de dados | PostgreSQL 16 (Docker) |
| Migrations | Flyway (SQL versionado) |
| Autenticação | JWT de acesso (15 min, HS256) + refresh token com rotação |
| Documentação | OpenAPI/Swagger UI em `/docs` |
| Testes | JUnit 5 + AssertJ (unitários) e Testcontainers (integração com Postgres real) |
| Build | Maven (wrapper incluso: `./mvnw`) |

## Como executar

Requisitos: **JDK 21+** e **Docker**. Maven não é necessário (use `./mvnw`, ou `mvnw.cmd` no Windows).

```bash
cp .env.example .env                                       # ajuste segredos se quiser
docker compose up -d db                                    # Postgres em 127.0.0.1:5432 (ou POSTGRES_PORT)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,seed # cria as tabelas, carrega a demo e sobe a API
```

- API: http://localhost:8080/api/v1
- Documentação interativa: http://localhost:8080/docs
- Saúde: http://localhost:8080/actuator/health

Nas próximas vezes, `./mvnw spring-boot:run` basta (o perfil `seed` recria o escritório de demonstração a cada inicialização).

A aplicação lê o mesmo `.env` usado pelo Docker Compose. Para subir **tudo em containers**: `docker compose --profile api up -d --build`. Cliente SQL web: `docker compose --profile tools up -d adminer` → http://localhost:8081 (servidor `db`).

No Compose, `POSTGRES_PORT` e `SERVER_PORT` do `.env` definem as portas publicadas no computador. Entre containers, a API sempre usa `db:5432` e escuta em `8080`, mesmo que o `.env` contenha `PORT` para um deploy no Render.

> No Windows com WSL, um Postgres do WSL pode ocupar `localhost:5432`; por isso a conexão usa `127.0.0.1`. Se a porta estiver ocupada (ex.: Postgres do Windows ou do WSL), troque `POSTGRES_PORT` no `.env` (ex.: `5433`) antes de subir o `db`; a API lê o mesmo `.env`.

### Acessos de demonstração

Senha de todos: `Ativeplay@123`

| E-mail | Perfil | O que vê |
| --- | --- | --- |
| admin@ativeplay.dev | ADMIN | Tudo, inclusive usuários, permissões e configurações |
| gestor@ativeplay.dev | GESTOR | Toda a operação, CRM e relatórios |
| operador@ativeplay.dev | OPERADOR | Só as empresas da sua carteira |
| operador2@ativeplay.dev | OPERADOR | Outra carteira |
| cliente@ativeplay.dev | CLIENTE | Portal da empresa Pão Dourado |

## Arquitetura

Organização **por domínio** (package-by-feature), com camadas dentro de cada módulo:

```text
ativeplay-backend/
├── docker-compose.yml           # Postgres (+ API e Adminer por profile)
├── Dockerfile                   # build multi-stage (Maven → JRE 21)
├── pom.xml  mvnw  mvnw.cmd
├── docs/banco-de-dados.md       # decisões, diagrama ER e dicionário das tabelas
└── src/
    ├── main/java/br/com/ativeplay/
    │   ├── AtivePlayApplication.java
    │   ├── config/              # segurança (JWT), OpenAPI, MVC, propriedades validadas
    │   ├── shared/              # código transversal, sem regra de negócio
    │   │   ├── seguranca/       # UsuarioAutenticado, @RequerModulo, @Perfis, AcessoInterceptor
    │   │   ├── escopo/          # isolamento por tenant e carteira de empresas
    │   │   ├── erro/            # ApiException + respostas ProblemDetail (RFC 9457)
    │   │   ├── dominio/         # superclasses de entidade (id, criado_em, atualizado_em)
    │   │   ├── armazenamento/   # arquivos em disco (trocável por S3)
    │   │   ├── util/ validacao/ web/
    │   ├── modules/<domínio>/
    │   │   ├── domain/          # entidades JPA e enums
    │   │   ├── repository/      # Spring Data JPA
    │   │   ├── service/         # regras de negócio e transações
    │   │   └── web/             # controllers REST e DTOs (records + Bean Validation)
    │   └── seed/                # dados de demonstração (perfil "seed")
    ├── main/resources/
    │   ├── application.yml
    │   └── db/migration/        # migrations Flyway (V1__schema_inicial.sql, ...)
    └── test/                    # unitários + integração (Testcontainers)
```

| Módulo | Responsabilidade |
| --- | --- |
| `auth` | Cadastro de escritório (onboarding), login, refresh com rotação, logout, `me`, troca de senha |
| `escritorio` | Dados do tenant e matriz de permissões por perfil |
| `usuario`, `setor` | Equipe, perfis, setores e carteira de empresas |
| `empresa` | Clientes do escritório (CNPJ alfanumérico) e equipe responsável |
| `tarefa` | Padrões → definições recorrentes → ocorrências diárias; cronômetro, SLA, comentários, agendador |
| `sessao` | Sessões de trabalho por empresa: contador geral do dia (`/sessoes/resumo`) e horas × honorário (`/sessoes/rentabilidade`) |
| `checklist` | Checklist dentro de cada cliente (observações pontuais com prazo, responsável e sub-itens) e checklist modelo do escritório, copiado para toda empresa nova |
| `senha` | Senhas salvas de cada cliente, cifradas com AES-256-GCM e com visualização auditada (módulo `SENHAS`) |
| `documento` | Upload/download de documentos e anexos de tarefas |
| `crm` | Funil (etapas), oportunidades, atividades e conversão em empresa |
| `integracao` | Conta Azul / Omie com credenciais cifradas e histórico de sincronização |
| `painel` | Indicadores do dia, SLA, produtividade, MRR e pipeline |
| `portal` | Rotas do cliente final (tarefas visíveis e troca de documentos) |
| `notificacao`, `auditoria` | Sino de avisos e trilha de auditoria |

### Segurança e multi-tenancy

- Todas as rotas exigem `Authorization: Bearer <token>`, exceto cadastro, login, refresh, logout, `/docs` (também `/swagger-ui.html`) e `/actuator/health`.
- O token carrega `escritorioId` e `perfil`; **toda consulta é filtrada pelo escritório do token**, nunca por um id vindo do cliente.
- `@RequerModulo(Modulo.X)` verifica a matriz `permissoes_perfil` (GET exige leitura; demais métodos, escrita). ADMIN tem acesso total.
- OPERADOR só acessa as empresas da sua carteira (`EscopoService`). CLIENTE só acessa `/api/v1/portal/**`.
- Senhas com BCrypt (custo 12); refresh tokens guardados como hash SHA-256 e rotacionados (reuso de token antigo revoga todas as sessões); credenciais de ERP cifradas com AES-256-GCM.
- Mutações relevantes são registradas na tabela `auditoria`.

### Rotinas agendadas

Com `JOBS_ENABLED=true`:
- **00:05 (horário de Brasília)** e na inicialização: gera as ocorrências do dia a partir das definições ativas (idempotente).
- **A cada minuto:** marca `sla_violado` nas ocorrências abertas com prazo vencido e notifica os responsáveis.

Com mais de uma instância da API, o ShedLock coordena as rotinas pela tabela `shedlock`, incluindo a geração na inicialização. As instâncias devem compartilhar o mesmo banco; os tempos máximos das travas devem exceder a duração dos jobs.

## Produção (Render + Supabase)

Ative o perfil `prod` com `SPRING_PROFILES_ACTIVE=prod` (arquivo `application-prod.yml`). Ele:

- **fecha a documentação**: `/docs`, `/swagger-ui.html` e `/v3/api-docs` não são servidos;
- **conecta ao banco com SSL** (`sslmode=require`): `jdbc:postgresql://DB_HOST:POSTGRES_PORT/POSTGRES_DB?sslmode=DB_SSLMODE`;
- **não carrega o seed**: o perfil `seed` só roda se for pedido explicitamente.

Variáveis a configurar no painel do Render (nunca no Git): `DB_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_ACCESS_SECRET`, `ENCRYPTION_KEY`, `CORS_ORIGINS` (URL do front na Vercel) e `SPRING_PROFILES_ACTIVE`. A porta vem de `PORT`, que o Render injeta sozinho.

Supabase: use o **session pooler** (porta `5432`; `POSTGRES_USER` no formato `postgres.<ref-do-projeto>`) com SSL.

**Primeiro deploy com os logins de demonstração** (projeto acadêmico, a banca usa os acessos deste README): suba com `SPRING_PROFILES_ACTIVE=prod,seed`. O seed **apaga e recria o escritório de demonstração a cada inicialização**, então depois do primeiro start volte para `SPRING_PROFILES_ACTIVE=prod`, ou os dados criados na demonstração serão perdidos no próximo deploy.

### Arquivos no Supabase Storage

O disco do Render é efêmero, então em produção use o bucket do Supabase pelo protocolo S3: `STORAGE_TIPO=s3`,
`S3_ENDPOINT=https://<ref-do-projeto>.storage.supabase.co/storage/v1/s3`, `S3_REGION` (a região do projeto, ex.: `sa-east-1`),
`S3_BUCKET` (bucket privado criado no painel) e `S3_ACCESS_KEY` / `S3_SECRET_KEY` (gerados em *Storage > S3 Connection*).
Essas chaves ficam só no painel do Render, nunca no Git. Em dev e nos testes o padrão continua sendo o disco local.

## Testes

```bash
./mvnw test
```

Os testes de integração sobem um PostgreSQL 16 temporário via Testcontainers (Docker precisa estar rodando) e exercitam a API de ponta a ponta: autenticação, rotação de sessão, isolamento entre escritórios, carteira do operador e portal do cliente.

## Convenções da API

- Prefixo `/api/v1`; JSON em `camelCase`; mensagens em português.
- Erros no formato ProblemDetail: `{ "status": 400, "detail": "Dados inválidos", "campos": { "cnpj": "CNPJ inválido" } }`.
- Listagens paginadas: `?pagina=1&porPagina=20` → `{ dados, total, pagina, porPagina }` (máx. 100 por página).
- Datas de competência em `yyyy-mm-dd`; instantes em ISO 8601 UTC.
- Valores monetários (`honorarioMensal`, `valorMensal`) como número decimal.
- CNPJ aceito com ou sem máscara, inclusive alfanumérico; armazenado sem pontuação.

## Variáveis de ambiente

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` / `POSTGRES_PORT` | `ativeplay` / — / `ativeplay` / `5432` | Banco (compartilhado com o Compose) |
| `DB_HOST` | `127.0.0.1` | Host do banco (`db` dentro do Compose) |
| `PORT` / `SERVER_PORT` | `8080` | Porta HTTP; `PORT` (injetada pelo Render) tem prioridade sobre `SERVER_PORT` |
| `DB_SSLMODE` | `require` | Só no perfil `prod`: modo SSL da conexão (`require` para o Supabase) |
| `JWT_ACCESS_SECRET` | — | Segredo HS256, mínimo 32 caracteres |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL_DAYS` | `15m` / `30` | Validade dos tokens |
| `ENCRYPTION_KEY` | — | 32 bytes em base64 para cifrar credenciais de ERP (`openssl rand -base64 32`) |
| `UPLOAD_DIR` | `./storage` | Pasta dos arquivos enviados |
| `STORAGE_TIPO` | `local` | Onde guardar os arquivos: `local` (disco, `UPLOAD_DIR`) ou `s3` (bucket S3 compatível, ex.: Supabase Storage) |
| `S3_ENDPOINT` / `S3_REGION` / `S3_BUCKET` | — / `us-east-1` / — | Só com `STORAGE_TIPO=s3`: endpoint, região e bucket |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | — | Só com `STORAGE_TIPO=s3`: chaves de acesso S3 do bucket |
| `UPLOAD_MAX_MB` | `20` | Tamanho máximo por arquivo |
| `CORS_ORIGINS` | `http://localhost:5173` | Origens do frontend, separadas por vírgula |
| `JOBS_ENABLED` | `true` | Liga as rotinas agendadas |
