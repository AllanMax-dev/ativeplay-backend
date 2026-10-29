-- =====================================================================
-- V4: checklist modelo do escritório
-- Toda empresa nova recebe uma cópia dos itens do modelo no próprio
-- checklist; as cópias não guardam vínculo com o modelo.
-- =====================================================================

CREATE TABLE checklist_modelo_itens (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escritorio_id UUID         NOT NULL REFERENCES escritorios (id) ON DELETE CASCADE,
    titulo        VARCHAR(200) NOT NULL,
    observacoes   TEXT,
    prioridade    VARCHAR(10)  NOT NULL DEFAULT 'MEDIA' CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE')),
    -- prazo da cópia = data de criação da empresa + prazo_dias (nulo = sem prazo)
    prazo_dias    INTEGER CHECK (prazo_dias IS NULL OR prazo_dias BETWEEN 0 AND 365),
    ordem         INTEGER      NOT NULL DEFAULT 0,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_checklist_modelo ON checklist_modelo_itens (escritorio_id, ordem);

CREATE TABLE checklist_modelo_subitens (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID         NOT NULL REFERENCES checklist_modelo_itens (id) ON DELETE CASCADE,
    texto   VARCHAR(200) NOT NULL,
    ordem   INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX ix_checklist_modelo_subitens ON checklist_modelo_subitens (item_id, ordem);
