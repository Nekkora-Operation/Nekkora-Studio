# STUDIO-008 — Auditoria (G0) e decisões D-S008

Base auditada: `main` em `beae03f` (HEAD conferida). Baseline: Windows `./mvnw clean verify` 464/464, golden 4/4.

## 1. Auditoria

| Área | Achado (caminho:linha) | Consequência |
|---|---|---|
| Agregado | `Automation.snapshot()`/`restore()` (domain `Automation.java:556-588`) guardam nome, descrição, sessões e estados de etapa; `WorkspaceService.edit(...)` (`WorkspaceService.java:479-501`) fotografa antes/depois, registra no `EditHistory`, marca a geração (dirty) e respeita o bloqueio de gravação | um catálogo de variáveis DENTRO do agregado, incluído na fotografia, participa de Undo/Redo, dirty e bloqueio sem mecanismo paralelo |
| Parâmetros | `ValueSource.VariableReference(String name)` e `CredentialReference(String alias)` com `REFERENCE_NAME = [A-Za-z][A-Za-z0-9_.-]{0,63}` (`ValueSource.java:16,36`) | resolução por nome exato; o ponto faz parte do nome; credencial continua só apelido |
| Edição de etapa | `Automation.editStep` valida tudo antes de alterar e marca o campo editado (`Automation.java:434-472`); baseline e procedência ficam intactos | renomear variável = uma operação de domínio que reaplica `editStep` em todas as etapas afetadas, com UM `touch()` (uma entrada de desfazer) |
| Versões | `ProjectFormat.FORMAT_VERSION = "1.0.0"` e `compatibility()` aceita só 1.0.x (`ProjectFormat.java`); `ModelVersion.CURRENT = "1.0.0"` exigido por `AutomationRestoration.java:114` e `ProjectCodec.java:353`; leitor recusa entrada não declarada no padrão (`ProjectFormat.ENTRY_NAME`) | acrescentar o catálogo exige versões novas do pacote e do modelo (não é correto mudar o conteúdo mantendo 1.0.0) |
| Contrato 1.0.0 congelado (STUDIO-006) | `ProjectFormatContractTest.schemaIgualAoCodigo` (`:120,123`) exige `FORMAT_VERSION` e `ModelVersion.CURRENT` = schema 1.0.0; fixtures válidas são relidas e reescritas idênticas; **`fixtures/invalid/formato-mais-novo.json` declara `formatVersion 1.1.0 → NEWER_VERSION`** e **`ProjectValidationTest.s006049Versao` (`:117-120`) exige `1.1.0` recusado como `NEWER_VERSION`** | **conflito**: adotar 1.1.0 para o novo formato contradiz uma fixture congelada e um teste existente |
| Validação (STUDIO-007) | `ValidationContractTest.schemaIgualAoCodigo` (`:116,120,121,123,124`) exige `FindingCode.values()`, `FindingScope`, `FindingField`, `RULES_VERSION` e `ValidationReport.VERSION` iguais ao schema 1.0.0; a fixture 1.0.0 do relatório espera `VARIABLE_RESOLUTION_UNAVAILABLE` | novos códigos no MESMO catálogo mudam `FindingCode.values()`: a comparação precisa passar a usar só os códigos das regras 1.0.0 |
| Studio (STUDIO-007) | `ValidationStudioTest.s007034a036` (`:166`) espera `VARIABLE_RESOLUTION_UNAVAILABLE` para uma variável usada SEM declaração | com o catálogo, o Studio passa a apontar `VARIABLE_NOT_DECLARED` (o texto do código antigo diz "o Studio ainda não tem catálogo", que deixa de ser verdade) |

## 2. Decisões propostas

| Id | Decisão | Alternativas rejeitadas | Impacto |
|---|---|---|---|
| D-S008-01 | Catálogo de variáveis dentro do agregado `Automation`, imutável por definição, incluído em `Snapshot` | catálogo no app (fora do Undo/Redo) | Undo/Redo, dirty, bloqueio e recuperação sem código paralelo |
| D-S008-02 | Nome único por automação (sem distinção de maiúsculas na unicidade), resolução por nome exato; `SESSION` só visível na própria sessão | nomes duplicáveis por escopo (shadowing) | referências legadas por nome continuam inequívocas |
| D-S008-03 | Renomear = operação atômica que atualiza todas as referências efetivas; baseline e procedência intactos | renomear só a declaração | consumidores nunca ficam órfãos por renomeação |
| D-S008-04 | Excluir variável usada: recusada, ou, com confirmação explícita, descarte dos vínculos (etapas ficam sem valor) | converter em literal; excluir etapas | não destrutivo |
| D-S008-05 | Uso e dependência DERIVADOS (nome resolvido com escopo); produtor só quando DECLARADO (`STEP_OUTPUT` + `producerStepId`) | inferir produtor por nomes parecidos | zero inferência |
| D-S008-06 | **Formato do pacote 1.1.0 e modelo 1.1.0** (como proposto), nova entrada `variables.json`; leitor 1.0.x estrito (catálogo vazio, `variables.json` proibido) e 1.1.x (obrigatória); **escritor grava a menor versão capaz de representar o conteúdo**: sem variáveis → 1.0.0, byte a byte como hoje; com variáveis → 1.1.0, com confirmação explícita e cópia do original quando o arquivo existente era 1.0.0 | gravar sempre 1.1.0 (quebraria a reescrita idêntica das fixtures 1.0.0 e migraria todo projeto só por salvar); arquivo lateral `.vars` (perde atomicidade); 1.2.0 ou 2.0.0 para fugir do conflito (incoerente: o teste também fixa 2.0.0 como "mais novo") | **exige autorização** para atualizar a expectativa "1.1.0 = mais novo" (ver §3) |
| D-S008-07 | Regras `nekkora-validation-rules/1.1.0` com códigos NOVOS acrescentados ao catálogo (os 40 de 1.0.0 intactos); relatório `validation-report` 1.1.0; o motor continua capaz de produzir o relatório 1.0.0 idêntico (fixture 1.0.0 preservada byte a byte) | catálogo paralelo de códigos | comparação de schema 1.0.0 passa a usar o subconjunto 1.0.0 (ver §3) |
| D-S008-08 | Contrato semântico `nekkora-studio/semantic-contract` 1.0.0 separado, local e determinístico; sem descrições livres, constantes, aliases, capturas, PageMap ou localizadores | estender o `ValidationReport` | relatório continua mínimo |

## 3. Pedido de autorização (G0)

A estratégia proposta pela missão (pacote e modelo 1.1.0) contradiz o contrato 1.0.0 congelado da STUDIO-006, que
fixa "1.1.0" como versão desconhecida. Para seguir são necessárias estas mudanças em artefatos existentes:

1. `nekkora-studio-persistence/src/test/resources/contracts/nekkora-project/1.0.0/fixtures/invalid/formato-mais-novo.json`:
   versão do exemplo "mais novo" de `1.1.0` para `1.2.0` (expectativa `NEWER_VERSION` mantida);
2. `ProjectValidationTest.s006049Versao`: o caso `1.1.0 + campoDoFuturo` passa a usar `1.2.0` (mesma expectativa);
3. `ValidationContractTest.schemaIgualAoCodigo`: compara o schema 1.0.0 com os códigos/escopos/campos das regras 1.0.0
   (mesma força; um teste novo compara o schema 1.1.0 com o catálogo completo);
4. `ValidationStudioTest.s007034a036`: a variável usada sem declaração passa a ser `VARIABLE_NOT_DECLARED`, não
   `VARIABLE_RESOLUTION_UNAVAILABLE` (mudança intencional de comportamento das regras 1.1.0).

Nada mais das fixtures/schemas 1.0.0 muda; os binários `.nekkora` congelados ficam intactos.

**Autorizado pela usuária em 2026-10-16** (opção "Autorizar os 4 ajustes"), antes de qualquer código de produção.

## 4. Decisões tomadas durante a implementação

| Id | Decisão | Motivo |
|---|---|---|
| D-S008-09 | `SemanticAuthority` passou a se chamar `SemanticStatus` (valores `USER_CONFIRMED`, `PROPOSED_UNCONFIRMED`, `UNSPECIFIED`); o campo JSON continua `authority` no `variables.json` e vira `semanticStatus` no contrato semântico | `StudioScopeTest.semFuncionalidadesFicticias` recusa classes com "auth" no nome (regra de escopo da STUDIO-001: nada de autenticação) |
| D-S008-10 | A ação "Variáveis e dependências…" fica no painel de propriedades da automação (grupo Ações), no atalho Ctrl+Shift+B e na aba "Variáveis" do painel inferior — **não** no menu Automação nem na barra de ferramentas | `ValidationStudioTest.s007048e049` fixa os itens do menu Automação (mudar esse teste não estava entre os 4 ajustes autorizados); na barra, a 1366 px, um botão a mais empurrava "Executar" para fora (visto nas capturas da homologação) |
| D-S008-17 | "Versão do modelo" no painel de propriedades: 1.0.0 e 1.1.0 são atuais; "(anterior)" só para versão não suportada | antes, qualquer versão ≠ 1.0.0 aparecia como "anterior" |
| D-S008-11 | Regras 1.1.0: 19 códigos novos (10 na etapa, 9 na variável), escopo e campo `VARIABLE`; apontamentos de variável vêm depois dos da automação e antes das sessões, na ordem do catálogo; `VARIABLE_RESOLUTION_UNAVAILABLE` continua no catálogo mas não é emitido pelas regras 1.1.0 | ver `validation-rules.md` |
| D-S008-12 | A identidade "sem variáveis = 1.0.0 byte a byte" é conferida entrada por entrada **descompactada** (nomes, ordem e bytes de cada documento) | os bytes compactados do ZIP dependem do zlib da plataforma (Windows ≠ Linux); os documentos não |
| D-S008-13 | Excluir ou mover uma etapa produtora, ou excluir a sessão de um escopo, **não** altera a declaração da variável: a validação aponta (`VARIABLE_PRODUCER_MISSING`, `VARIABLE_PRODUCER_OUT_OF_SCOPE`, `VARIABLE_SCOPE_SESSION_MISSING`, `DECLARED_PRODUCER_MISSING`) | nada é corrigido em silêncio |
| D-S008-14 | Migração 1.0→1.1 só ao gravar **sobre o próprio arquivo** 1.0: confirmação explícita; cópia conferida do original em `<nome> (formato 1.0.0).nekkora` (depois ` 2`, ` 3`…), nunca substituindo um arquivo existente; conflito (nada copiado nem gravado) se o original mudou. "Salvar como" em outro arquivo não toca no original | não reescrever destrutivamente o formato |
| D-S008-15 | Homologação da 008 sem navegador | nenhum cenário grava, executa ou consulta página |
| D-S008-16 | `Studio007Homologation` (roteiro histórico da 007, fora do `verify`) não foi alterado; com as regras 1.1.0 a verificação "variável → `VARIABLE_RESOLUTION_UNAVAILABLE`" daquele roteiro não vale mais | é o registro da 007; alterar não estava autorizado |

## 5. Revisão 1 do PR #11 (2026-10-17) — ver `review-1.md`

| Id | Decisão | Motivo |
|---|---|---|
| D-S008-18 | O contrato semântico guarda só cópias próprias (`Variable`, `Scope`, `Origin`, `Constraints`); do domínio, só identidades e enums. O JSON é escrito a partir dessas cópias | P1: a API Java alcançava valor fixo e finalidade pela `VariableDefinition`; a minimização tem de valer para a API, não só para o JSON |
| D-S008-19 | Representação ≠ significado: `TextFormat.BRAZILIAN_CPF_MASKED` (fim do enum), `SemanticType.representations()`, aviso `VARIABLE_REPRESENTATION_UNSPECIFIED` (limita a avaliação) e incerteza `REPRESENTATION_UNSPECIFIED`; nunca bloqueio, nunca máscara imposta, nunca obrigatoriedade derivada do significado | P2: a IA não pode escolher entre `12345678909` e `123.456.789-09` |
| D-S008-20 | Acréscimos aditivos nos contratos 1.1.0 (projeto, regras/relatório) e 1.0.0 (contrato semântico) sem nova versão | contratos introduzidos neste PR, ainda não integrados nem publicados; fixture do contrato semântico idêntica; fixture `validation-report/1.1.0` regenerada com autorização da usuária |
| D-S008-21 | `VariableDraft.toString()` sem nome, finalidade nem valor | achado durante a reprodução do P1 (`draft().toString()` mostrava a finalidade) |

## 6. Revisão 2 do PR #11 (2026-10-17) — ver `review-2.md`

| Id | Decisão | Motivo |
|---|---|---|
| D-S008-22 | O domínio recusa formato de texto que nenhuma forma válida do significado satisfaz (`SemanticType.admits`, `ValidationProblem.VARIABLE_FORMAT_INCOMPATIBLE`): e-mail só com dígitos, com letras e dígitos ou com máscara de CPF; CPF com forma de e-mail. Lista explícita para CPF e e-mail; demais significados abertos; confirmado ou proposto | o contrato semântico vai alimentar geração assistida: uma declaração contraditória não pode chegar até ela. Mesmo mecanismo da moeda fora de "Valor monetário" (recusa, nunca correção silenciosa) |
| D-S008-23 | Sem código novo na validação nem no contrato semântico; arquivos 1.1.0 com a combinação são recusados ao abrir (`INVALID_CONTENT`) | a combinação deixa de existir no modelo; o 1.1.0 só foi produzido por builds deste PR, ainda não integrado |
