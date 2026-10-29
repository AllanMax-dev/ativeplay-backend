-- =====================================================================
-- V3: checklist por cliente, senhas salvas por cliente e módulo SENHAS
-- =====================================================================

-- ---------------------------------------------------------------------
-- Checklist do cliente: observações pontuais com prazo e responsável
-- (ex.: "Cliente pendente de envio do acesso bancário").
-- ---------------------------------------------------------------------
CREATE TABLE checklist_itens (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id    UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    empresa_id       UUID         NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    titulo           VARCHAR(200) NOT NULL,
    observacoes      TEXT,
    prazo            DATE,
    prioridade       VARCHAR(10)  NOT NULL DEFAULT 'MEDIA' CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE')),
    responsavel_id   UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    criado_por_id    UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    concluido        BOOLEAN      NOT NULL DEFAULT FALSE,
    concluido_em     TIMESTAMPTZ,
    concluido_por_id UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    ordem            INTEGER      NOT NULL DEFAULT 0,
    criado_em        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- item concluído sempre tem a data de conclusão, e vice-versa
    CONSTRAINT ck_checklist_conclusao CHECK (concluido = (concluido_em IS NOT NULL))
);
CREATE INDEX ix_checklist_empresa ON checklist_itens (escritorio_id, empresa_id, concluido, prazo);
CREATE INDEX ix_checklist_responsavel ON checklist_itens (responsavel_id) WHERE NOT concluido;

-- Sub-itens do checklist (ex.: "Extrato Itaú", "Extrato Santander").
CREATE TABLE checklist_subitens (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id   UUID         NOT NULL REFERENCES checklist_itens (id) ON DELETE CASCADE,
    texto     VARCHAR(200) NOT NULL,
    concluido BOOLEAN      NOT NULL DEFAULT FALSE,
    ordem     INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX ix_checklist_subitens_item ON checklist_subitens (item_id, ordem);

-- ---------------------------------------------------------------------
-- Senhas salvas do cliente: bancos, certificados, portais (energia,
-- prefeitura, ERP...). A senha é cifrada com AES-256-GCM
-- (ENCRYPTION_KEY) e só é devolvida pela rota de revelar, que audita.
-- ---------------------------------------------------------------------
CREATE TABLE senhas_empresa (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id   UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    empresa_id      UUID         NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    acesso          VARCHAR(150) NOT NULL,
    categoria       VARCHAR(20)  NOT NULL DEFAULT 'OUTRO'
                    CHECK (categoria IN ('BANCO', 'CERTIFICADO', 'PORTAL', 'ERP', 'EMAIL', 'GOVERNO', 'OUTRO')),
    usuario         VARCHAR(200),
    senha_cifrada   TEXT,
    url             VARCHAR(500),
    observacoes     TEXT,
    criado_por_id   UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    atualizado_por_id UUID REFERENCES usuarios (id) ON DELETE SET NULL,
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_senhas_empresa ON senhas_empresa (escritorio_id, empresa_id, acesso);

-- ---------------------------------------------------------------------
-- Novo módulo SENHAS na matriz de permissões
-- ---------------------------------------------------------------------
ALTER TABLE permissoes_perfil DROP CONSTRAINT permissoes_perfil_modulo_check;
ALTER TABLE permissoes_perfil ADD CONSTRAINT permissoes_perfil_modulo_check
    CHECK (modulo IN ('PAINEL', 'EMPRESAS', 'TAREFAS', 'CRM', 'DOCUMENTOS', 'RELATORIOS',
                      'INTEGRACOES', 'USUARIOS', 'CONFIGURACOES', 'AUDITORIA', 'SENHAS'));

-- Escritórios já existentes recebem o padrão: GESTOR e OPERADOR com escrita
-- (o operador precisa das senhas para executar os pagamentos dos clientes).
INSERT INTO permissoes_perfil (escritorio_id, perfil, modulo, escrita)
SELECT e.id, p.perfil, 'SENHAS', TRUE
FROM escritorios e
CROSS JOIN (VALUES ('GESTOR'), ('OPERADOR')) AS p (perfil)
ON CONFLICT DO NOTHING;
