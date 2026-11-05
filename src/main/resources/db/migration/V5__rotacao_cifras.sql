-- Metadados operacionais; nunca armazena chaves, cifras ou texto claro.
CREATE TABLE rotacoes_cifras (
    chave_destino VARCHAR(64) NOT NULL,
    tabela VARCHAR(32) NOT NULL CHECK (tabela IN ('senhas_empresa', 'integracoes')),
    ultimo_id UUID,
    total_inicial BIGINT NOT NULL,
    cifradas_inicial BIGINT NOT NULL,
    processadas BIGINT NOT NULL DEFAULT 0,
    alteradas BIGINT NOT NULL DEFAULT 0,
    concluida BOOLEAN NOT NULL DEFAULT FALSE,
    iniciado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chave_destino, tabela)
);
