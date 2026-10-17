# STUDIO-008 — Escopos, vinculação e fluxo de dados

## Escopos

- **Automação inteira**: visível em todas as sessões.
- **Sessão**: visível só nas etapas daquela sessão. Uma etapa de outra sessão que use o nome recebe
  `VARIABLE_OUT_OF_SCOPE` (bloqueio). Não existe sombreamento: o nome é único na automação inteira (D-S008-02).

## Vinculação (uso derivado)

Não há vínculo armazenado: a etapa guarda o **nome** na sua referência de valor (como desde a STUDIO-004) e o Studio
deriva o uso comparando com o catálogo por **igualdade exata**. Nomes parecidos (maiúsculas, um caractere a mais) não
são associados: a validação aponta `VARIABLE_NOT_DECLARED`, e o editor de etapa mostra "Não declarada".

### Renomear

Uma operação de domínio troca o nome da declaração **e** a referência efetiva de todas as etapas que a usam, com uma
única entrada de histórico (um Desfazer volta tudo). O original capturado e a procedência das etapas gravadas não
mudam. O formulário mostra a prévia ("1 etapa passa a usar o novo nome…") antes de confirmar.

### Excluir

Variável sem uso: excluída. Em uso: só com confirmação explícita, que diz quantas etapas ficam sem valor; os
parâmetros dessas etapas são esvaziados na mesma operação desfazível (D-S008-04). Nunca há conversão em literal.

## Fluxo de dados

- **Ordem linear**: sessões na ordem, etapas na ordem. O modelo não tem desvios nem repetições.
- **Produtora declarada** (origem `STEP_OUTPUT`): a etapa escolhida no formulário, que precisa existir e estar no
  escopo da variável quando é declarada.
- Para cada etapa que usa uma variável de saída de etapa:
  - produtora antes → `STEP_OUTPUT_NOT_PROVEN` (aviso que limita: declarada, não comprovada);
  - produtora depois → `DECLARED_PRODUCER_AFTER_CONSUMER` (bloqueio);
  - a própria etapa → `STEP_OUTPUT_SELF_CONSUMED` (bloqueio);
  - produtora inexistente ou fora do escopo → `DECLARED_PRODUCER_MISSING` (bloqueio);
  - ciclo entre etapas (A usa saída de B e B usa saída de A) → `DATA_DEPENDENCY_CYCLE` (bloqueio).
- Excluir/mover a produtora ou excluir a sessão do escopo **não** altera a declaração: a validação aponta na variável
  (`VARIABLE_PRODUCER_MISSING`, `VARIABLE_PRODUCER_OUT_OF_SCOPE`, `VARIABLE_SCOPE_SESSION_MISSING`) — D-S008-13.

## Credenciais

`CredentialReference` continua sendo só um apelido. Variáveis não substituem credenciais: um campo sensível que usa
variável recebe `SENSITIVE_FIELD_WITHOUT_CREDENTIAL`; uma credencial continua `CREDENTIAL_RESOLUTION_UNAVAILABLE`.
Nenhum cofre é consultado.
