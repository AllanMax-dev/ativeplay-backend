# Rotação de chaves das credenciais cifradas

Esta versão grava apenas envelopes `apc:1:<keyId>:<base64>` com AES-256-GCM.
O corpo contém IV de 12 bytes, ciphertext e tag de 128 bits. O cabeçalho completo
é autenticado como AAD. A leitura de Base64 legado exige a chave legada explícita.
IDs e versões desconhecidos falham sem tentar outras chaves.

## Configuração antes do deploy

| Variável | Uso |
| --- | --- |
| `ENCRYPTION_ACTIVE_KEY_ID` | ID imutável da chave ativa, de 1 a 64 caracteres: letras, números, `_` ou `-`. |
| `ENCRYPTION_ACTIVE_KEY` | Nova chave de 32 bytes em Base64, exclusiva do ambiente. Obrigatória. |
| `ENCRYPTION_KEY` | Chave que já decifra os registros sem envelope. Somente leitura; preservar durante a migração. |
| `ENCRYPTION_PREVIOUS_KEYS` | Objeto JSON de IDs anteriores para suas chaves Base64. Opcional; vazio quando não houver versões anteriores. |

Gere cada chave nova com `openssl rand -base64 32` em ambiente protegido e armazene
o resultado diretamente no gerenciador de segredos. Não coloque o valor em Git,
logs, argumentos de comandos ou mensagens. Nunca associe um ID existente a outra
chave. A inicialização recusa chave ativa ausente/inválida ou igual à legada ou
a alguma chave anterior.

**A configuração antiga, sozinha, não inicia esta versão.** Antes do deploy,
cadastre o ID e a chave ativa nova e preserve o valor atual de `ENCRYPTION_KEY`.
Não substitua esse valor pela nova chave: isso perderia a leitura das cifras antigas.
Para uma instalação sem dados antigos, a chave legada pode ficar vazia.

Os serviços de senhas, integrações e o seeder usam a mesma configuração. A API
mantém seus endpoints e DTOs. Não há endpoint de migração, agendamento automático
ou recifragem durante a inicialização. A migration Flyway V5 adiciona somente a
tabela de controle `rotacoes_cifras`; não modifica cifras existentes.

## Ensaio e condições de execução

1. Confirmar origem das chaves e recuperar um backup consistente do banco e as
   versões correspondentes do armazenamento de segredos. Testar a restauração
   em ambiente isolado antes de qualquer rotação real.
2. Em uma cópia restaurada, registrar totais e quantidades de colunas não nulas
   de `senhas_empresa.senha_cifrada` e `integracoes.credenciais_cifradas`. A ferramenta
   registra essas contagens automaticamente por tabela no início e compara ao
   longo da execução. Ela não substitui a confirmação prévia de recuperação.
3. Executar os testes com Docker disponível: `./mvnw -B test`. No Windows, usar
   `mvnw.cmd`. Os testes PostgreSQL usam containers isolados, nunca produção.
4. Ensaiar o comando abaixo na cópia restaurada, incluindo interrupção e retomada.
   Verificar as duas tabelas concluídas, contagens e acesso às senhas/integrações
   pela aplicação. A ferramenta decifra todos os registros em memória na
   verificação final; não exporta cifras nem texto claro.
5. Obter aceite operacional de backup, restauração, janela e rollback. Parar
   **todas** as instâncias escritoras, jobs, scripts e consumidores durante a
   recifragem. O seeder não pode executar nessa janela.

Não fazer rolling deploy com versões antigas escritoras: elas não leem o novo
envelope e podem sobrescrever cifras já migradas. Os bloqueios por lote não
substituem a janela sem escritas, inclusive entre interrupção e retomada.

## Comando operacional

Preparar o JAR com `./mvnw -B -DskipTests package` somente após a suíte passar.
O comando usa um entry point separado: não inicia API, contexto Spring Boot,
Flyway, jobs ou seed; também não lê `.env` automaticamente.

Injetar no processo as quatro variáveis de criptografia acima, `POSTGRES_USER`,
`POSTGRES_PASSWORD` e `MIGRATION_JDBC_URL`. Esta última deve ser a URL JDBC
PostgreSQL aprovada para a cópia/restauração ou, após aceite, para o ambiente alvo,
com as opções TLS corretas. Não incluir usuário/senha na URL. Usar conexão direta
ou pooler em **modo session**; transaction pooling não preserva a trava de sessão.
A V5 precisa já estar aplicada ao banco selecionado.

Ajuda, sem conexão ao banco:

```sh
java -Dloader.main=br.com.ativeplay.operacao.MigracaoCifrasCommand -cp target/ativeplay-api-0.1.0-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --help
```

Após cumprir as condições acima, executar no ambiente aprovado:

```sh
java -Dloader.main=br.com.ativeplay.operacao.MigracaoCifrasCommand -cp target/ativeplay-api-0.1.0-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --confirmar-backup --confirmar-manutencao --lote=200
```

No PowerShell, colocar `'-Dloader.main=br.com.ativeplay.operacao.MigracaoCifrasCommand'`
entre aspas simples. O tamanho do lote aceita valores de 1 a 1000. As confirmações
são declarações do operador; o programa não consegue provar que um backup foi
restaurado nem que todos os escritores foram parados.

Uma trava PostgreSQL de sessão impede dois processos desta ferramenta no mesmo
banco. Cada lote usa transação própria e bloqueia escritas na tabela enquanto
processa. Cifras e checkpoint por UUID são confirmados na mesma transação; erro
de decifragem ou de atualização desfaz o lote. Lotes anteriores permanecem
confirmados. Os logs operacionais contêm apenas tabela, UUID, contagens e estado.

Para retomar após falha/interrupção, corrigir a causa e repetir o mesmo comando,
com as mesmas chaves e ID ativo, mantendo a janela sem escritas. Não apagar ou
editar checkpoints manualmente. Cifras já ativas são autenticadas e preservadas,
sem gerar novo IV. Ao final, uma varredura desde o início autentica todas as
cifras e confirma a chave ativa, inclusive registros antes do checkpoint.

Saídas: `0` indica verificação das duas tabelas concluída; `1` indica falha de
configuração/execução; `2` indica argumentos ou confirmações inválidos. Em falha,
consultar metadados de `rotacoes_cifras` e investigar dados/configuração em ambiente
restrito. Não imprimir linhas cifradas nem habilitar logs de parâmetros SQL.

## Aceite e rollback

Antes de reabrir escritas, verificar `concluida=true` nas duas entradas para o ID
ativo, comparar contagens e realizar os fluxos de revelar senha e testar uma
integração na versão nova. A janela deve permanecer sem escritas durante a
verificação final. `concluida` registra o resultado daquela execução, não monitora
mudanças posteriores. Cada invocação revalida integralmente o conteúdo.

Em falha: interromper a ferramenta, preservar os checkpoints, o backup e todas
as chaves. Retomar com a mesma versão/configuração após corrigir a causa, ou
restaurar o backup consistente junto com suas chaves e versão de aplicação.
**Reverter apenas o binário antigo não é seguro após a primeira escrita versionada.**
Reativar a chave legada permite ler cifras legadas nesta versão, mas não torna
o binário antigo capaz de ler envelopes novos. Não há conversão automática de
volta ao formato antigo. As instruções de restauração do provedor devem ser
validadas pelo responsável antes de agendar produção.

Manter chave legada e chaves anteriores até o aceite e o fim da janela de rollback.
Depois, retirar da configuração ativa somente as que não são mais necessárias;
preservar acesso protegido às chaves exigidas por backups ainda retidos. Destruir
cópias apenas conforme a política de retenção aprovada.
