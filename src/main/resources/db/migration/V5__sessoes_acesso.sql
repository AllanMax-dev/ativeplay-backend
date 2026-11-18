
CREATE TABLE sessoes_acesso (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    escritorio_id UUID NOT NULL
        REFERENCES escritorios(id) ON DELETE CASCADE,

    usuario_id UUID NOT NULL
        REFERENCES usuarios(id) ON DELETE CASCADE,

    inicio TIMESTAMPTZ NOT NULL DEFAULT now(),

    fim TIMESTAMPTZ,

    CONSTRAINT ck_sessoes_acesso_periodo
        CHECK (fim IS NULL OR fim >= inicio)
);

CREATE INDEX ix_sessoes_acesso_usuario_inicio
    ON sessoes_acesso (usuario_id, inicio DESC);

ALTER TABLE refresh_tokens
    ADD COLUMN sessao_acesso_id UUID
    REFERENCES sessoes_acesso(id) ON DELETE SET NULL;

CREATE INDEX ix_refresh_tokens_sessao_acesso
    ON refresh_tokens(sessao_acesso_id);
