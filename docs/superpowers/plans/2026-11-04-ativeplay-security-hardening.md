# Plano de Correção de Segurança do Backend AtivePlay

> **Para agentes:** implementar tarefa a tarefa com TDD; obter autorização explícita antes de alterar código. Cada etapa termina com revisão e verificação.

**Objetivo:** corrigir os riscos confirmados e condicionais do primeiro Security Scan, preservando dados, contratos públicos e funcionalidades existentes.

**Arquitetura:** aplicar correções isoladas, priorizadas por risco. Para cifragem, usar identificadores de versão de chave e migração gradual com verificação e rollback. Para autenticação, tornar a autorização revogável no servidor. Endurecer o seeder e explicitar controles de infraestrutura sem presumir configuração externa.

**Tecnologias:** Java 21, Spring Boot 3.5, Spring Security, JPA, PostgreSQL, Maven, Docker Compose e JUnit.

**Especificação:** achados do primeiro Security Scan e solicitação do usuário nesta conversa.

## Restrições globais

- Preservar dados existentes, contratos das APIs e funcionalidades atuais.
- Nunca registrar, imprimir ou incluir segredos em logs, testes, relatórios ou exemplos.
- Não executar migrações em produção nem alterar credenciais sem plano operacional aprovado.
- Cada correção de comportamento segue ciclo TDD: teste falha pela razão esperada, implementação mínima, suíte relevante passa.
- Confirmar configurações reais de produção antes de aplicar mudanças que dependam de proxy, certificados, firewall ou perfis.
- Operações de recifragem devem ser retomáveis, auditáveis, idempotentes e testadas em cópia de dados.

## Foco de revisão

- Rotação interrompida, parcial ou repetida de chaves: preservar legibilidade e integridade das cifras antigas.
- Requisições concorrentes e tokens já emitidos: revogar autoridade após desativação, rebaixamento ou mudança de permissões.
- Perfis `prod` combinados com `seed`: impedir exclusões acidentais no banco selecionado.
- Certificado válido para CA diferente, hostname incorreto e proxy/pooler PostgreSQL: falhar de forma segura sem indisponibilidade silenciosa.
- Proxy não confiável, cabeçalho `Forwarded` falsificado e múltiplas instâncias: não permitir evasão ou bloqueio global por IP incorreto.

---

### Etapa 0: confirmar condições de produção e preparar recuperação

**Arquivos:** nenhum arquivo de aplicação; consultar configuração operacional aprovada e documentação existente.

- [ ] Confirmar se `.env` local é usado em produção, quem pode lê-lo e se a chave coincide com algum ambiente implantado. A igualdade local com `.env.example` não prova exposição em produção.
- [ ] Confirmar política de backups, retenção, restauração testada, volume de linhas cifradas e janela de manutenção.
- [ ] Confirmar versões implantadas, perfis ativos, uso de `seed`, hostname/CA do PostgreSQL, pooling/proxy, réplicas da API e roteamento de IP.
- [ ] Registrar baseline de quantidade e formato dos dados cifrados, sem exportar texto claro ou valores secretos.
- [ ] Não prosseguir com rotação real, mudança de TLS ou alteração de perfil até o responsável operacional validar os pré-requisitos.

**Aceitação:** inventário de fatos confirmados e desconhecidos; cópia restaurável verificada antes de qualquer recifragem.

### Etapa 1: remover a chave compartilhada e migrar cifras com segurança — prioridade crítica se chave ativa fora de dev; alta caso contrário

**Arquivos previstos:** `src/main/java/br/com/ativeplay/shared/util/Cripto.java`, `src/main/java/br/com/ativeplay/modules/senha/service/SenhaEmpresaService.java`, `src/main/java/br/com/ativeplay/modules/integracao/service/IntegracaoService.java`, `src/main/resources/application.yml`, `.env.example`; nova ferramenta/serviço de migração e testes dedicados.

**Interface proposta:** `Cripto.cifrar(texto, chave, keyId)` e `Cripto.decifrar(payload, resolvedorDeChaves)`; envelope versionado armazena versão, `keyId`, IV, ciphertext e tag. Configuração aceita chave ativa e chaves anteriores somente durante migração. Valores existentes sem envelope continuam decifráveis com chave legado configurada explicitamente.

- [ ] Teste RED: decifrar cifras legadas e versionadas, rejeitar payload truncado, chave ausente, ID desconhecido e adulteração de tag.
- [ ] Implementar leitura compatível com formatos antigos e escrita somente com a chave nova. Remover a chave concreta do exemplo; fornecer instrução para gerar segredo único por ambiente, sem valor reutilizável.
- [ ] Teste RED: recifragem mantém texto lógico, não altera contagens, pode ser repetida e retoma após falha sem corromper linhas já processadas.
- [ ] Implementar job operacional em lotes, com transações limitadas, checkpoint por ID, validação de integridade e contagens antes/depois. Não expor endpoint HTTP público nem incluir plaintext em logs.
- [ ] Teste RED: rollback de lote não deixa linha parcialmente atualizada; versão legada continua legível enquanto a chave antiga estiver habilitada.
- [ ] Executar em cópia restaurada; comparar contagens e decifrar amostra controlada em memória. Só então agendar produção.
- [ ] Fazer backup consistente e proteger a chave antiga fora do banco antes da execução. Manter leitura das duas chaves até validação completa e janela de rollback encerrada; depois retirar a chave antiga e destruir cópias segundo política aprovada.

**Rollback:** parar job; restaurar backup consistente de banco e versão correspondente do key store, ou reativar chave antiga enquanto o formato legado ainda for suportado. Nunca restaurar banco sem a chave que decifra suas linhas. Não apagar a chave antiga até aceite formal da verificação.

**Dependência:** confirmação da origem, uso e escopo da chave por ambiente; backup restaurável; mecanismo seguro de distribuição/versão de segredos.

### Etapa 2: revogação efetiva de access JWT — prioridade alta

**Arquivos previstos:** `AuthService.java`, `UsuarioAutenticadoResolver.java`, `UsuarioService.java`, `SecurityConfig.java`, migration aditiva e testes em `src/test/java/br/com/ativeplay/modules/auth`.

**Interface proposta:** claim `ver` no JWT comparada com `versao_sessao` do usuário, ou mecanismo equivalente de versão de credenciais consultado no servidor. Incrementar versão dentro da mesma transação ao desativar usuário, mudar perfil/empresa, redefinir/trocar senha ou alterar permissões que afetem a conta. Preservar formato Bearer e respostas existentes.

- [ ] Teste RED: token emitido antes de desativação, rebaixamento ou troca de empresa recebe 401/403 após a operação; token novo mantém acesso esperado.
- [ ] Teste RED: mudança de permissões revoga tokens afetados, sem revogar sessões de outros tenants/perfis indevidamente.
- [ ] Implementar validação da versão em cada autenticação, com cache somente se houver invalidação coerente entre instâncias; preferir consulta autoritativa até medir custo.
- [ ] Migration aditiva define versão inicial compatível para todas as contas existentes; testar upgrade e downgrade operacional em cópia.
- [ ] Verificar comportamento de indisponibilidade do banco: falhar fechado na validação de revogação.

**Rollback:** manter claim opcional durante transição; feature flag operacional para voltar à validação anterior apenas por janela emergencial documentada. A reversão reabre a janela de validade residual dos JWTs e exige mitigação explícita.

### Etapa 3: impedir execução destrutiva do DemoSeeder em produção — prioridade alta

**Arquivos previstos:** `src/main/java/br/com/ativeplay/seed/DemoSeeder.java`, nova propriedade tipada em `AtivePlayProperties.java`/configuração e testes `src/test/java/br/com/ativeplay/config/PerfilProducaoTest.java`.

- [ ] Teste RED: contexto `prod,seed` recusa inicialização antes de qualquer escrita; `seed` em desenvolvimento segue funcional.
- [ ] Teste RED: habilitação de seed exige confirmação explícita não padrão e continua proibida em `prod`.
- [ ] Adicionar proteção estrutural no código, não apenas instrução no README; verificar ordem de execução para falhar antes do runner e evitar exclusões.
- [ ] Preservar fluxo de demonstração em ambiente isolado e tornar mensagens de erro claras, sem revelar dados.

**Rollback:** reverter apenas a proteção após revisar logs/configuração e confirmar ambiente não produtivo; nunca usar rollback para reabilitar seed em banco de produção.

### Etapa 4: validar TLS PostgreSQL com CA e hostname — prioridade alta quando DB atravessa rede não confiável

**Arquivos previstos:** `src/main/resources/application-prod.yml`, documentação operacional de variáveis e testes `src/test/java/br/com/ativeplay/config/PerfilProducaoTest.java`.

- [ ] Teste RED: modo prod resolve `verify-full` por padrão e rejeita `require`/`disable` em produção, salvo decisão explícita de compatibilidade aprovada.
- [ ] Confirmar hostname DNS usado pelo pooler e CA suportada pelo provedor; configurar cadeia de confiança conforme driver PostgreSQL e rotação de certificado do fornecedor.
- [ ] Manter override documentado apenas se permitir verificação equivalente; não aceitar `verify-ca` como validação de hostname.
- [ ] Validar handshake em staging com hostname correto, hostname divergente e CA inválida; sem testes contra produção nesta etapa.

**Rollback:** reverter propriedade apenas se houver falha de conectividade comprovada; restaurar temporariamente certificado/hostname correto e registrar prazo. Não degradar TLS silenciosamente.

### Etapa 5: restringir portas publicadas do Docker — prioridade condicionada ao uso Compose

**Arquivos previstos:** `docker-compose.yml`, documentação Compose e teste/script estático de configuração, se já houver infraestrutura adequada.

- [ ] Confirmar consumidores externos necessários. Para desenvolvimento local, publicar PostgreSQL como `127.0.0.1:${POSTGRES_PORT:-5432}:5432`; manter Adminer em perfil opcional e loopback. Não restringir a API se houver necessidade declarada de acesso LAN.
- [ ] Verificar sintaxe/renderização do Compose e comparar portas/binds esperados por perfil, sem iniciar serviços.
- [ ] Documentar que firewall/SG do host continuam necessários em implantação remota.

**Rollback:** restaurar bind anterior somente para necessidade operacional validada, acompanhado de regra de firewall restritiva.

### Etapa 6: rate limiting distribuído e confiança em proxy — prioridade média, depende de topologia confirmada

**Arquivos previstos:** `AuthController.java`, `LimiteTentativas.java`, `application.yml`, configuração de proxy/infra e testes auth/config.

**Interface proposta:** resolvedor de IP que só consome `Forwarded`/`X-Forwarded-For` quando a requisição vem de proxy explicitamente confiável; backend de limite compartilhado com chave normalizada e TTL atômico, preservando limites e respostas 429 atuais.

- [ ] Teste RED: cabeçalho forjado por cliente direto não muda IP/chave; proxy confiável resulta no IP cliente esperado; IPv4/IPv6 e cadeia inválida têm fallback seguro.
- [ ] Teste RED: duas instâncias compartilham contador; concorrência não excede limite; expiração e sucesso/falha mantêm política atual.
- [ ] Escolher Redis ou limitador de gateway já operado. Não acrescentar dependência de infraestrutura sem disponibilidade, autenticação, TLS, TTL, fail mode e monitoração definidos.
- [ ] Decidir e testar política fail-closed/fail-open em falha do backend de rate limit conforme risco de disponibilidade e abuso.

**Rollback:** alternar para gateway/limitador anterior; manter configuração de proxy explícita e alarmes. Limitação em memória pode ser fallback temporário apenas se a capacidade de múltiplas réplicas estiver desativada.

## Verificação transversal e encerramento

- [ ] Para cada etapa, rodar teste focado primeiro e depois suíte Maven relevante; não executar migração ou comandos contra produção.
- [ ] Confirmar contratos HTTP: endpoints, DTOs, status existentes e comportamento de clientes não mudam; mudanças de configuração e claims são retrocompatíveis durante rollout.
- [ ] Revisar logs para ausência de plaintext, chaves, refresh tokens e dados pessoais; revisar métricas de rejeição TLS, revogação, rate limit e job de recifragem.
- [ ] Fazer revisão de segurança do diff, validar migrations aditivas e documentar ordem de deploy, critérios de aceite e comando de rollback específico ao ambiente.
- [ ] Obter aprovação operacional para rotação, configuração TLS e infraestrutura somente após staging e restauração de backup comprovadas.

**Itens que exigem confirmação em produção:** presença/uso real da chave compartilhada; exposição e ACL de `.env`; perfis ativos e uso de `seed`; PostgreSQL/pooler, hostname, CA e SSL atual; bind de portas, firewall e acesso remoto; quantidade de réplicas, proxy confiável e rate limiting no gateway; backups e capacidade de restaurar cifra e chave em conjunto.
