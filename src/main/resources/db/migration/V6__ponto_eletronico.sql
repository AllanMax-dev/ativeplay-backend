-- Ponto eletrônico: último sinal do navegador e registros pendentes (sem sinal por X minutos).
ALTER TABLE sessoes_acesso
    ADD COLUMN ultimo_sinal TIMESTAMPTZ,
    ADD COLUMN pendente BOOLEAN NOT NULL DEFAULT false;

-- Um registro aberto por usuário: antes do índice, os abertos mais antigos de quem tem
-- mais de um viram pendentes (o mais recente continua aberto).
UPDATE sessoes_acesso s
   SET pendente = true
 WHERE s.fim IS NULL
   AND EXISTS (
       SELECT 1
         FROM sessoes_acesso o
        WHERE o.usuario_id = s.usuario_id
          AND o.fim IS NULL
          AND (o.inicio > s.inicio OR (o.inicio = s.inicio AND o.id > s.id))
   );

CREATE UNIQUE INDEX uq_sessoes_acesso_aberta_usuario
    ON sessoes_acesso (usuario_id)
    WHERE fim IS NULL AND pendente = false;
