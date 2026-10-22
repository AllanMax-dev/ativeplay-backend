-- =========================================================
-- ATIVE PLAY — modelo de dados inicial (PostgreSQL 16)
--
-- Multi-tenancy: banco e schema compartilhados. Toda tabela de
-- negócio tem escritorio_id (o tenant) e a API filtra todas as
-- consultas pelo escritório do usuário autenticado.
--
-- Enumerações são VARCHAR + CHECK (mapeadas como @Enumerated
-- STRING no JPA), o que facilita evoluir valores via migration.
-- =========================================================

-- ---------------------------------------------------------
-- Tenant, identidade e acesso
-- ---------------------------------------------------------

CREATE TABLE escritorios (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome          VARCHAR(160) NOT NULL,
    slug          VARCHAR(80)  NOT NULL UNIQUE,
    cnpj          VARCHAR(14) UNIQUE,
    plano         VARCHAR(20)  NOT NULL DEFAULT 'TRIAL'
                  CHECK (plano IN ('TRIAL', 'BASICO', 'PROFISSIONAL', 'EMPRESARIAL')),
    fuso_horario  VARCHAR(64)  NOT NULL DEFAULT 'America/Sao_Paulo',
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE escritorios IS 'Escritório de BPO/contabilidade: o tenant da plataforma';

CREATE TABLE setores (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID        NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    nome          VARCHAR(80) NOT NULL,
    cor           VARCHAR(7),
    ordem         INTEGER     NOT NULL DEFAULT 0,
    ativo         BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_setores_nome UNIQUE (escritorio_id, nome)
);
COMMENT ON TABLE setores IS 'Setores operacionais: Financeiro, Contábil, Fiscal, Departamento Pessoal...';

CREATE TABLE empresas (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id     UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    razao_social      VARCHAR(200) NOT NULL,
    nome_fantasia     VARCHAR(200),
    cnpj              VARCHAR(14)  NOT NULL,
    regime_tributario VARCHAR(20)  NOT NULL DEFAULT 'SIMPLES_NACIONAL'
                      CHECK (regime_tributario IN ('SIMPLES_NACIONAL', 'LUCRO_PRESUMIDO', 'LUCRO_REAL', 'MEI', 'OUTRO')),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ONBOARDING'
                      CHECK (status IN ('ONBOARDING', 'ATIVA', 'SUSPENSA', 'ENCERRADA')),
    email             VARCHAR(254),
    telefone          VARCHAR(20),
    responsavel_id    UUID,
    honorario_mensal  NUMERIC(12, 2),
    data_inicio       DATE,
    observacoes       TEXT,
    criado_em         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_empresas_cnpj UNIQUE (escritorio_id, cnpj)
);
COMMENT ON TABLE empresas IS 'Clientes atendidos pelo escritório';
COMMENT ON COLUMN empresas.cnpj IS 'Normalizado, 14 caracteres; aceita o CNPJ alfanumérico (IN RFB 2.229/2024)';

CREATE TABLE usuarios (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    empresa_id    UUID REFERENCES empresas (id) ON DELETE CASCADE,
    nome          VARCHAR(160) NOT NULL,
    email         VARCHAR(254) NOT NULL UNIQUE,
    senha_hash    VARCHAR(100) NOT NULL,
    perfil        VARCHAR(20)  NOT NULL DEFAULT 'OPERADOR'
                  CHECK (perfil IN ('ADMIN', 'GESTOR', 'OPERADOR', 'CLIENTE')),
    cargo         VARCHAR(80),
    telefone      VARCHAR(20),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_acesso TIMESTAMPTZ,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- usuário do portal (CLIENTE) sempre representa uma empresa; equipe interna nunca
    CONSTRAINT ck_usuarios_empresa_cliente CHECK ((perfil = 'CLIENTE') = (empresa_id IS NOT NULL))
);
CREATE INDEX ix_usuarios_escritorio_perfil ON usuarios (escritorio_id, perfil);
CREATE INDEX ix_usuarios_empresa ON usuarios (empresa_id);
COMMENT ON COLUMN usuarios.email IS 'Único na plataforma: o login não pede o escritório';

ALTER TABLE empresas
    ADD CONSTRAINT fk_empresas_responsavel FOREIGN KEY (responsavel_id) REFERENCES usuarios (id) ON DELETE SET NULL;
CREATE INDEX ix_empresas_escritorio_status ON empresas (escritorio_id, status);
CREATE INDEX ix_empresas_responsavel ON empresas (responsavel_id);

CREATE TABLE permissoes_perfil (
    escritorio_id UUID        NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    perfil        VARCHAR(20) NOT NULL CHECK (perfil IN ('GESTOR', 'OPERADOR', 'CLIENTE')),
    modulo        VARCHAR(20) NOT NULL
                  CHECK (modulo IN ('PAINEL', 'EMPRESAS', 'TAREFAS', 'CRM', 'DOCUMENTOS', 'RELATORIOS',
                                    'INTEGRACOES', 'USUARIOS', 'CONFIGURACOES', 'AUDITORIA')),
    escrita       BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (escritorio_id, perfil, modulo)
);
COMMENT ON TABLE permissoes_perfil IS 'Módulos liberados por perfil (sem linha = sem acesso; escrita=false = só leitura). ADMIN tem acesso total.';

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id  UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expira_em   TIMESTAMPTZ NOT NULL,
    revogado_em TIMESTAMPTZ,
    user_agent  VARCHAR(255),
    ip          VARCHAR(45),
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_usuario ON refresh_tokens (usuario_id);
COMMENT ON TABLE refresh_tokens IS 'Sessões com rotação; guarda apenas o SHA-256 do token';

CREATE TABLE usuario_setores (
    usuario_id UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    setor_id   UUID NOT NULL REFERENCES setores (id) ON DELETE CASCADE,
    PRIMARY KEY (usuario_id, setor_id)
);
CREATE INDEX ix_usuario_setores_setor ON usuario_setores (setor_id);

CREATE TABLE usuario_empresas (
    usuario_id UUID NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    empresa_id UUID NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    PRIMARY KEY (usuario_id, empresa_id)
);
CREATE INDEX ix_usuario_empresas_empresa ON usuario_empresas (empresa_id);
COMMENT ON TABLE usuario_empresas IS 'Carteira: empresas atendidas por um OPERADOR (ADMIN e GESTOR veem todas)';

-- ---------------------------------------------------------
-- Operação: tarefas
--   padroes_tarefa     catálogo reutilizável do escritório
--   definicoes_tarefa  recorrência configurada para uma empresa
--   ocorrencias_tarefa instância de um dia (o que é executado)
-- ---------------------------------------------------------

CREATE TABLE padroes_tarefa (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id      UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    setor_id           UUID REFERENCES setores (id) ON DELETE SET NULL,
    nome               VARCHAR(160) NOT NULL,
    descricao          TEXT,
    recorrencia        VARCHAR(10)  NOT NULL DEFAULT 'MENSAL'
                       CHECK (recorrencia IN ('DIARIA', 'SEMANAL', 'MENSAL', 'UNICA')),
    dias_semana        INTEGER[]    NOT NULL DEFAULT '{}',
    dia_mes            INTEGER CHECK (dia_mes BETWEEN 1 AND 31),
    apenas_dias_uteis  BOOLEAN      NOT NULL DEFAULT TRUE,
    horario_limite     VARCHAR(5)   NOT NULL DEFAULT '18:00' CHECK (horario_limite ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
    prioridade         VARCHAR(10)  NOT NULL DEFAULT 'MEDIA' CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE')),
    tempo_estimado_min INTEGER CHECK (tempo_estimado_min > 0),
    ordem              INTEGER      NOT NULL DEFAULT 0,
    ativo              BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_padroes_tarefa_ordem ON padroes_tarefa (escritorio_id, ordem);
COMMENT ON COLUMN padroes_tarefa.dias_semana IS '0 = domingo ... 6 = sábado (recorrência SEMANAL)';
COMMENT ON COLUMN padroes_tarefa.dia_mes IS 'Recorrência MENSAL; dias maiores que o mês caem no último dia';
COMMENT ON COLUMN padroes_tarefa.tempo_estimado_min IS 'Meta de execução em minutos (produtividade). O SLA de entrega é o prazo: data + horário limite';

CREATE TABLE definicoes_tarefa (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id      UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    empresa_id         UUID         NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    padrao_id          UUID REFERENCES padroes_tarefa (id) ON DELETE SET NULL,
    setor_id           UUID REFERENCES setores (id) ON DELETE SET NULL,
    responsavel_id     UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    nome               VARCHAR(160) NOT NULL,
    descricao          TEXT,
    recorrencia        VARCHAR(10)  NOT NULL DEFAULT 'MENSAL'
                       CHECK (recorrencia IN ('DIARIA', 'SEMANAL', 'MENSAL', 'UNICA')),
    dias_semana        INTEGER[]    NOT NULL DEFAULT '{}',
    dia_mes            INTEGER CHECK (dia_mes BETWEEN 1 AND 31),
    data_unica         DATE,
    apenas_dias_uteis  BOOLEAN      NOT NULL DEFAULT TRUE,
    horario_limite     VARCHAR(5)   NOT NULL DEFAULT '18:00' CHECK (horario_limite ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
    prioridade         VARCHAR(10)  NOT NULL DEFAULT 'MEDIA' CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE')),
    tempo_estimado_min INTEGER CHECK (tempo_estimado_min > 0),
    visivel_cliente    BOOLEAN      NOT NULL DEFAULT FALSE,
    ordem              INTEGER      NOT NULL DEFAULT 0,
    ativa              BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_definicoes_empresa_ordem ON definicoes_tarefa (escritorio_id, empresa_id, ativa, ordem);
CREATE INDEX ix_definicoes_responsavel ON definicoes_tarefa (responsavel_id);

CREATE TABLE ocorrencias_tarefa (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id      UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    empresa_id         UUID         NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    definicao_id       UUID REFERENCES definicoes_tarefa (id) ON DELETE SET NULL,
    setor_id           UUID REFERENCES setores (id) ON DELETE SET NULL,
    responsavel_id     UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    titulo             VARCHAR(160) NOT NULL,
    descricao          TEXT,
    data               DATE         NOT NULL,
    prazo              TIMESTAMPTZ  NOT NULL,
    prioridade         VARCHAR(10)  NOT NULL DEFAULT 'MEDIA' CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE')),
    status             VARCHAR(15)  NOT NULL DEFAULT 'PENDENTE'
                       CHECK (status IN ('PENDENTE', 'EM_ANDAMENTO', 'PAUSADA', 'CONCLUIDA', 'CANCELADA')),
    tempo_estimado_min INTEGER,
    sla_violado        BOOLEAN      NOT NULL DEFAULT FALSE,
    visivel_cliente    BOOLEAN      NOT NULL DEFAULT FALSE,
    tempo_gasto_ms     BIGINT       NOT NULL DEFAULT 0 CHECK (tempo_gasto_ms >= 0),
    cronometro_inicio  TIMESTAMPTZ,
    iniciada_em        TIMESTAMPTZ,
    concluida_em       TIMESTAMPTZ,
    concluida_por_id   UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    versao             INTEGER      NOT NULL DEFAULT 0,
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- geração idempotente: uma ocorrência por definição e data
    CONSTRAINT uq_ocorrencias_definicao_data UNIQUE (definicao_id, data),
    -- cronômetro só roda em tarefa em andamento
    CONSTRAINT ck_ocorrencias_cronometro CHECK (cronometro_inicio IS NULL OR status = 'EM_ANDAMENTO')
);
CREATE INDEX ix_ocorrencias_dia ON ocorrencias_tarefa (escritorio_id, data, status);
CREATE INDEX ix_ocorrencias_empresa_dia ON ocorrencias_tarefa (escritorio_id, empresa_id, data);
CREATE INDEX ix_ocorrencias_responsavel ON ocorrencias_tarefa (responsavel_id, status);
CREATE INDEX ix_ocorrencias_sla ON ocorrencias_tarefa (prazo) WHERE status IN ('PENDENTE', 'EM_ANDAMENTO', 'PAUSADA') AND NOT sla_violado;
COMMENT ON COLUMN ocorrencias_tarefa.data IS 'Data de competência';
COMMENT ON COLUMN ocorrencias_tarefa.tempo_gasto_ms IS 'Tempo acumulado dos intervalos já encerrados do cronômetro';
COMMENT ON COLUMN ocorrencias_tarefa.cronometro_inicio IS 'Início do intervalo em andamento (nulo = parado)';
COMMENT ON COLUMN ocorrencias_tarefa.sla_violado IS 'Passou do prazo sem conclusão, ou foi concluída após ele';
COMMENT ON COLUMN ocorrencias_tarefa.versao IS 'Controle de concorrência otimista (JPA @Version)';

CREATE TABLE comentarios_ocorrencia (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID        NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    ocorrencia_id UUID        NOT NULL REFERENCES ocorrencias_tarefa (id) ON DELETE CASCADE,
    autor_id      UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    texto         TEXT        NOT NULL,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_comentarios_ocorrencia ON comentarios_ocorrencia (ocorrencia_id, criado_em);

CREATE TABLE documentos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id   UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    empresa_id      UUID         NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    ocorrencia_id   UUID REFERENCES ocorrencias_tarefa (id) ON DELETE CASCADE,
    enviado_por_id  UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    nome            VARCHAR(255) NOT NULL,
    categoria       VARCHAR(60),
    mime_type       VARCHAR(120) NOT NULL,
    tamanho         BIGINT       NOT NULL,
    storage_key     VARCHAR(255) NOT NULL UNIQUE,
    sha256          VARCHAR(64),
    visivel_cliente BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_documentos_empresa ON documentos (escritorio_id, empresa_id, criado_em DESC);
CREATE INDEX ix_documentos_ocorrencia ON documentos (ocorrencia_id);
COMMENT ON TABLE documentos IS 'Documentos da empresa; com ocorrencia_id preenchido são anexos de uma tarefa';

CREATE TABLE sessoes_trabalho (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID        NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    usuario_id    UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    empresa_id    UUID        NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    inicio        TIMESTAMPTZ NOT NULL DEFAULT now(),
    fim           TIMESTAMPTZ,
    CONSTRAINT ck_sessoes_periodo CHECK (fim IS NULL OR fim >= inicio)
);
-- no máximo uma sessão aberta por usuário
CREATE UNIQUE INDEX uq_sessoes_aberta_usuario ON sessoes_trabalho (usuario_id) WHERE fim IS NULL;
CREATE INDEX ix_sessoes_empresa ON sessoes_trabalho (escritorio_id, empresa_id, inicio);

-- ---------------------------------------------------------
-- Comunicação e rastreabilidade
-- ---------------------------------------------------------

CREATE TABLE notificacoes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    usuario_id    UUID         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo          VARCHAR(20)  NOT NULL
                  CHECK (tipo IN ('TAREFA_ATRIBUIDA', 'TAREFA_ATRASADA', 'SLA_VIOLADO', 'COMENTARIO',
                                  'DOCUMENTO', 'CRM', 'INTEGRACAO', 'SISTEMA')),
    titulo        VARCHAR(160) NOT NULL,
    mensagem      TEXT,
    link          VARCHAR(255),
    lida_em       TIMESTAMPTZ,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notificacoes_usuario ON notificacoes (usuario_id, lida_em, criado_em DESC);

CREATE TABLE auditoria (
    id            BIGSERIAL PRIMARY KEY,
    escritorio_id UUID        NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    usuario_id    UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    empresa_id    UUID REFERENCES empresas (id) ON DELETE SET NULL,
    acao          VARCHAR(60) NOT NULL,
    entidade      VARCHAR(60) NOT NULL,
    entidade_id   VARCHAR(64),
    descricao     TEXT,
    dados         JSONB,
    ip            VARCHAR(45),
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_auditoria_linha_tempo ON auditoria (escritorio_id, criado_em DESC);
CREATE INDEX ix_auditoria_entidade ON auditoria (escritorio_id, entidade, entidade_id);
CREATE INDEX ix_auditoria_empresa ON auditoria (empresa_id);
COMMENT ON TABLE auditoria IS 'Trilha imutável (apenas INSERT). acao no padrão entidade.verbo, ex.: tarefa.concluir';

-- ---------------------------------------------------------
-- CRM
-- ---------------------------------------------------------

CREATE TABLE etapas_crm (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID        NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    nome          VARCHAR(80) NOT NULL,
    cor           VARCHAR(7),
    ordem         INTEGER     NOT NULL DEFAULT 0,
    tipo          VARCHAR(10) NOT NULL DEFAULT 'ABERTA' CHECK (tipo IN ('ABERTA', 'GANHA', 'PERDIDA')),
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_etapas_crm_nome UNIQUE (escritorio_id, nome)
);

CREATE TABLE oportunidades_crm (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id       UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    etapa_id            UUID         NOT NULL REFERENCES etapas_crm (id) ON DELETE RESTRICT,
    responsavel_id      UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    empresa_id          UUID REFERENCES empresas (id) ON DELETE SET NULL,
    titulo              VARCHAR(160) NOT NULL,
    razao_social        VARCHAR(200),
    cnpj                VARCHAR(14),
    contato_nome        VARCHAR(160),
    contato_email       VARCHAR(254),
    contato_telefone    VARCHAR(20),
    origem              VARCHAR(60),
    valor_mensal        NUMERIC(12, 2),
    previsao_fechamento DATE,
    motivo_perda        TEXT,
    ordem               INTEGER      NOT NULL DEFAULT 0,
    fechada_em          TIMESTAMPTZ,
    criado_em           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_oportunidades_etapa ON oportunidades_crm (escritorio_id, etapa_id, ordem);
CREATE INDEX ix_oportunidades_responsavel ON oportunidades_crm (responsavel_id);
COMMENT ON COLUMN oportunidades_crm.empresa_id IS 'Empresa criada na conversão da oportunidade ganha';

CREATE TABLE atividades_crm (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id   UUID        NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    oportunidade_id UUID        NOT NULL REFERENCES oportunidades_crm (id) ON DELETE CASCADE,
    autor_id        UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    tipo            VARCHAR(10) NOT NULL DEFAULT 'NOTA' CHECK (tipo IN ('NOTA', 'LIGACAO', 'EMAIL', 'REUNIAO', 'TAREFA')),
    descricao       TEXT        NOT NULL,
    agendada_para   TIMESTAMPTZ,
    concluida_em    TIMESTAMPTZ,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_atividades_oportunidade ON atividades_crm (oportunidade_id, criado_em);

-- ---------------------------------------------------------
-- Integrações com ERPs
-- ---------------------------------------------------------

CREATE TABLE integracoes (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id        UUID        NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    empresa_id           UUID        NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    provedor             VARCHAR(20) NOT NULL CHECK (provedor IN ('CONTA_AZUL', 'OMIE')),
    status               VARCHAR(15) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'ATIVA', 'ERRO', 'DESATIVADA')),
    credenciais_cifradas TEXT,
    configuracoes        JSONB       NOT NULL DEFAULT '{}',
    ultima_sincronizacao TIMESTAMPTZ,
    ultimo_erro          TEXT,
    criado_em            TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_integracoes_empresa_provedor UNIQUE (empresa_id, provedor)
);
CREATE INDEX ix_integracoes_status ON integracoes (escritorio_id, status);
COMMENT ON COLUMN integracoes.credenciais_cifradas IS 'AES-256-GCM; nunca retornadas pela API';

CREATE TABLE sincronizacoes_integracao (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID        NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    integracao_id UUID        NOT NULL REFERENCES integracoes (id) ON DELETE CASCADE,
    recurso       VARCHAR(60) NOT NULL,
    status        VARCHAR(15) NOT NULL DEFAULT 'EM_EXECUCAO' CHECK (status IN ('EM_EXECUCAO', 'SUCESSO', 'ERRO')),
    registros     INTEGER     NOT NULL DEFAULT 0,
    erro          TEXT,
    iniciada_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    finalizada_em TIMESTAMPTZ
);
CREATE INDEX ix_sincronizacoes_integracao ON sincronizacoes_integracao (integracao_id, iniciada_em DESC);
