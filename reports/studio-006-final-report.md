# NEKKORA-STUDIO-006 — Relatório final

**Persistência de projetos, salvamento seguro e recuperação de automações**

| Item | Valor |
|---|---|
| Repositório | `Nekkora-Operation/Nekkora-Studio` |
| Branch | `feat/nekkora-studio-006` |
| Baseline | `3dc6035cb6b9d80de04b48d640d4b868b213491d` (main, PRs #7 da CHROME-SECURITY-001 integrados) |
| Extensão usada | árvore idêntica à `main` `2c7c06b` (checkout local em `e4a30aa`, `git diff e4a30aa 2c7c06b` vazio); **nenhuma alteração na extensão** |
| Commit de código testado | `db0e757` depois da revisão 1 do PR #8 (§22); a entrega original foi testada em `d2e0eec` (§17 e §18) |
| Dados reais | **não utilizados** (D-S006-18); só páginas fictícias do laboratório local |
| Merge | **não feito** — PR para revisão independente |

Commits da missão:

| Commit | Conteúdo |
|---|---|
| `b3579e7` | domínio: reconstrução controlada (`AutomationRestoration`) |
| `9970ded` | módulo `nekkora-studio-persistence`: formato 1.0.0, validação, gravação atômica |
| `6d362eb` | app: salvar, abrir, fechar, sair, recuperação, recentes, menu Arquivo |
| `b17da4a` | testes de reconstrução, recuperação e desempenho |
| `cd9134d` | contrato versionado: schema, fixtures válidas congeladas e 18 inválidas |
| `3ddff58` | barra de status sem sobreposição (defeito da homologação), rótulos "em memória", homologação real, README |
| `d2e0eec` | teste de somente leitura declara a inaplicabilidade como superusuário (Linux em contêiner roda como root) |
| `946ec7f` | `docs/studio-006/`, este relatório e as evidências |
| `a864089` | **revisão 1**: recarregar transacional (P1-A), limpeza da recuperação antes de informar sucesso ou encerrar (P1-B), Salvar como confere o destino confirmado (P2) |
| `a27f81f` | revisão 1: homologação real do encerramento (Sair → Descartar, salvar e sair, reinício sem oferta) |
| `db0e757` | revisão 1: aviso "Encerrando o Studio" só quando o encerramento realmente espera (achado da homologação) |
| (docs) | revisão 1: este relatório (§22), `docs/studio-006/` e evidências atualizados |

---

## 1. Baseline

`./mvnw clean verify` sobre `3dc6035` no Windows: **344/344**, BUILD SUCCESS (24 tokens, 46 swing-ui, 5 showcase,
89 domínio, 22 bridge, 62 recorder, 14 studio-ui, 81 app + 1 escala 150%). Igual ao baseline documentado.

## 2. Arquitetura

Novo módulo `nekkora-studio-persistence` (Java puro; depende só do domínio e do `Json` do bridge). O domínio ganhou
apenas a reconstrução controlada; o app ganhou `ProjectService`, `ProjectState` e o diretório de dados. Detalhes e o
mapeamento com a arquitetura sugerida (§6) em [docs/studio-006/architecture.md](../docs/studio-006/architecture.md).
Decisões D-S006-01…18 e decisões de implementação em [decisions.md](../docs/studio-006/decisions.md).

## 3. Contrato de arquivo

`.nekkora` = ZIP com `manifest.json`, `metadata.json`, `automation.json`, `sessions.json`, `steps/<stepId>.json`,
`evidence/<stepId>.json`, `evidence/unattributed.json`. Nomes num padrão fechado; ordem explícita nos documentos (nunca
do ZIP); todo campo sempre presente; campo desconhecido recusado. Schema JSON 2020-12 e fixtures versionados em
`nekkora-studio-persistence/src/test/resources/contracts/nekkora-project/1.0.0/` (2 válidas congeladas — projeto e
recuperação —, 18 inválidas). [project-format.md](../docs/studio-006/project-format.md),
[manifest.md](../docs/studio-006/manifest.md).

SHA-256 por documento no manifesto **só para corrupção acidental**; não é autenticidade. O arquivo **não é
criptografado** (`metadata.encrypted: false`, recusado se `true`).

## 4. Modelo de persistência

Auditoria do que é persistido × temporário e o estado por automação (`ProjectState`: arquivo, geração, geração salva,
geração fotografada, erro, salvando, recuperado) em [persistence-model.md](../docs/studio-006/persistence-model.md). A
geração é **independente** de `Automation.revision()`: evidência recebida torna o projeto não salvo sem entrar no
desfazer.

## 5. Serialização

Codec próprio, campo a campo, sem serialização automática. Fotografia na EDT (árvores JSON novas), todo o resto no
worker. ZIP determinístico (data fixa, ordem fixa). Releitura completa do temporário antes de confirmar.
[serialization.md](../docs/studio-006/serialization.md).

## 6. Reconstrução

`AutomationRestoration.restore` cria o agregado com as identidades e os instantes do arquivo e reaplica todos os
invariantes; ganchos restritos ao pacote do domínio (nenhuma API pública para estado arbitrário). Não usa
`Automation.create` + operações comuns. [hydration.md](../docs/studio-006/hydration.md).

## 7. Integridade

Dez camadas de verificação (tamanho, contêiner, bytes lidos, manifesto com versão primeiro, JSON, campos exatos,
referências, domínio, privacidade v2, arquivo estável durante a leitura). Zip Slip, caminho absoluto, `../`, duplicata,
bomba de descompressão, profundidade e memória cobertos. Integral ou recusado: o catálogo só recebe o agregado
completo. Verificação estrutural pós-carga compara IDs, ordem, valores, procedência, elementos, parâmetros,
comentários, evidências, revisões e instantes (`ModelDump`).

## 8. Atomicidade

Temporário na mesma pasta → `force` → releitura → conferência de conflito (duas vezes) → `ATOMIC_MOVE` → sync da pasta.
Sem `ATOMIC_MOVE`: cópia de segurança com restauração (limitação documentada). Sucesso só depois da troca.
[atomic-save.md](../docs/studio-006/atomic-save.md).

## 9. Tratamento de falhas

| Falha (§33) | Prova |
|---|---|
| disco cheio, falha durante escrita, antes e durante a substituição | `AtomicSaveTest` (falhas injetadas em `FileOps`), `ProjectStudioTest` S006-042/043 |
| diretório inexistente, somente leitura | `AtomicSaveTest` §33; **homologação real** (somente leitura no NTFS) |
| diretório sem permissão | mapeado para `ACCESS_DENIED` (`ProjectRepository.map`); **sem teste dedicado** (mesmo caminho de erro de E/S dos casos testados) |
| arquivo em uso | `AtomicSaveTest` S006-044; **homologação real** (outro processo com `FileShare.None`) |
| corrompido, manifesto inválido, checksum, versão, duplicados, referência inexistente, evidência duplicada, ZIP malicioso | `ProjectValidationTest` S006-046…054, fixtures inválidas; homologação (3 arquivos recusados pelo menu) |
| projeto duplicado / mesma identidade | `ProjectStudioTest` S006-066/067 |
| interrupção da aplicação | `AtomicSaveTest` (temporário abandonado limpo); **homologação real** (processo morto à força) |

Em nenhum caso o arquivo anterior foi apagado ou alterado; nenhum temporário ficou para trás.

## 10. Compatibilidade

Três versões independentes (formato 1.0.0, modelo `nekkora-studio/automation` 1.0.0, `nekkora-privacy/2`). Versão mais
nova → `NEWER_VERSION` antes de ler o resto; mais antiga/inválida → `UNSUPPORTED_VERSION`; sem migrações.
A fixture 1.0.0 congelada é reescrita identicamente (qualquer mudança de classe que quebre arquivos salvos faz o teste
falhar). A extensão não mudou e o protocolo do Bridge também não (D-S006-16). [versioning.md](../docs/studio-006/versioning.md).

## 11. Privacidade

Dados capturados como ponto fixo da `nekkora-privacy/2` (antes de confirmar e depois de ler); arquivo fora da política é
recusado, nunca "sanitizado". Credencial só pelo apelido; nenhum campo para valor digitado; texto da página nunca
gravado. Textos do usuário seguem o domínio, e a interface avisa que o arquivo é legível e não criptografado. Logs só
técnicos. [privacy.md](../docs/studio-006/privacy.md).

## 12. Evidências

Todos os campos preservados, mesma identidade, ordem técnica, não atribuídas, `NOT_VALIDATED`; nenhuma regenerada nem
duplicada; texto omitido não volta. [evidence-persistence.md](../docs/studio-006/evidence-persistence.md).

## 13. Histórico

Desfazer/refazer não é persistido (`Automation.Snapshot` não é o formato); ao abrir, histórico vazio; volta a funcionar
depois de uma edição; evidências fora do histórico mas dentro do controle de persistência.
[undo-redo.md](../docs/studio-006/undo-redo.md).

## 14. Recuperação

Só com consentimento informado (diálogo com local, retenção de 3 cópias/7 dias e aviso de não criptografado);
diretório próprio, substituível por `NEKKORA_STUDIO_DATA_DIR`; temporizador e perda de foco; mesmo formato
(`kind=RECOVERY`); oferta explícita ao abrir (Recuperar / Ignorar / Cancelar); recuperado continua não salvo; salvar
confere a base do arquivo de origem; fotografias apagadas só por salvamento confirmado, descarte autorizado, limpeza
pedida ou retenção. [recovery.md](../docs/studio-006/recovery.md), [conflict-handling.md](../docs/studio-006/conflict-handling.md).

## 15. Interface

Menu Arquivo completo (atalhos Ctrl+O, Ctrl+S, Ctrl+Shift+S sem conflito), `*` nas abas, situação do projeto na barra
de status, diálogos do Design System, recentes com indisponíveis. [ui-ux.md](../docs/studio-006/ui-ux.md).

**Defeito encontrado pela homologação e corrigido**: com a janela de 870 px (e em 1366×768 a 150%), a barra de status
sobrepunha textos da direita aos da esquerda (`BorderLayout` WEST/EAST). Correção em `NekkoraStatusBar` (linha única,
prioridade à esquerda, itens saem inteiros); teste `LayoutRegressionTest.barraDeStatusSemSobreposicao` **falha no
código anterior e passa no novo**. Também removidos os rótulos "Rascunho em memória — não salvo", que deixaram de ser
verdade. Golden screenshots **não regenerados** (passam nos dois sistemas).

## 16. Desempenho

Projetos realistas de 10/100/1.000/5.000 etapas (com evidências, comentários e edições). Windows, commit `3ddff58`:
5.000 etapas = 7,4 MiB; **20,5 ms de EDT** (só a fotografia); salvar com releitura 878 ms; abrir 588 ms; validação +
reconstrução 359 ms; 17,4 MiB de heap; recuperação 884 ms (gravar) e 583 ms (varrer). Linux (`d2e0eec`): EDT 19,9 ms, salvar 876 ms, abrir 567 ms. Limites
operacionais definidos a partir das medições (20.000 etapas, 8 MiB por entrada, 512 MiB descompactado, 128 MiB de
arquivo). [performance.md](../docs/studio-006/performance.md).

## 17. Testes

> Resultados da **entrega original** (`d2e0eec`). Os resultados finais, depois da revisão 1, estão na §22.

### Regressão

| Execução | Commit | Resultado |
|---|---|---|
| Windows 11, `./mvnw clean verify` (Bridge em `NEKKORA_BRIDGE_DIR` isolado, `NEKKORA_STUDIO_DATA_DIR` isolado) | `d2e0eec` | **417/417**, 0 pulados, BUILD SUCCESS |
| Linux de referência, `timeout 2400 sh tools/golden-linux.sh verify` (Temurin 21.0.12.1 fixado por digest; extensão ao lado, Native Host compilado) | `d2e0eec` | **417 executados: 416 aprovados, 1 pulado**, BUILD SUCCESS |
| Windows 11, `./mvnw clean verify` | `3ddff58` | 417/417 |
| Linux de referência | `3ddff58` | **falhou** em `AtomicSaveTest.pastaEArquivo` (ver abaixo); módulos seguintes não rodaram |

Por módulo (`d2e0eec`, iguais nos dois sistemas): tokens 24, swing-ui 47 (+1), showcase 5, domínio 92 (+3), bridge 22,
recorder 62, **persistência 42 (novo)**, studio-ui 14, app 108 (+27), escala 150% 1. Total 417 = 344 do baseline + 73
novos. Nenhum teste anterior foi removido.

- **Golden screenshots**: `GoldenScreenshotTest` 4/4 no Windows e no Linux, **sem regenerar** nenhuma imagem.
- **Contrato com a extensão**: `BridgeContractFixturesTest` 3/3 (schema e fixtures idênticos aos do repositório da
  extensão ao lado).
- **Extensão** (Java 271 / Node 148 / Chrome 251): **não repetida** — nenhuma alteração na extensão nem no protocolo
  compartilhado (§38). A integração foi verificada na homologação real (§18).

**Falha no Linux em `3ddff58` e correção**: o contêiner roda os testes como **root**, que ignora a permissão de somente
leitura (`Files.isWritable` verdadeiro e o sistema permite gravar). O Studio segue o sistema operacional; o teste é que
não podia exercitar "somente leitura" como superusuário. Em `d2e0eec` essa parte do teste usa uma *assumption*
explícita ("parte não aplicável para superusuário") — por isso o 1 pulado no Linux; no Windows ela roda e passa, e a
homologação real comprovou o somente leitura no NTFS.

### Testes ajustados (pré-existentes) e por quê

| Teste | Mudança | Justificativa |
|---|---|---|
| `StudioScopeTest` S001-020 | `FUTURE = [RUN]` (antes `[RUN, SAVE]`); Salvar desabilitado sem automação ativa | Salvar passou a existir de verdade (§28) |
| `StudioScopeTest` S001-021 | o aviso de saída agora é o diálogo Salvar todos / Descartar / Cancelar com a lista de projetos | §29 substitui o aviso "alterações em memória serão perdidas" |
| `StudioScopeTest` (barra de status) | "Rascunho em memória" → "Nenhum projeto aberto" / "Alterações não salvas — sem arquivo"; "1 automação em memória" → "1 automação no Studio" | estado por projeto (§20, §28) |
| `StudioScopeTest` (filtro de sucesso simulado) | exceção para `SAVED("Salvo")` em `ProjectState.java` | "Salvo" agora é real: só aparece após salvamento confirmado (provado em `ProjectStudioTest`) |
| `EditorStudioTest` | título da aba renomeada termina com ` *` | asterisco de alterações não salvas (§28) |
| `RecorderStudioTest` `s002001SemNavegador` | espera a propagação do estado (até 5 s) em vez de ler no mesmo instante | **condição de corrida do próprio teste** — ver abaixo |
| `ScriptedPrompts` | novas respostas roteirizadas (`thenSaveAs`, `thenOpen`, …); `thenExit(true/false)` = Descartar/Cancelar | novos diálogos |
| `AtomicSaveTest.pastaEArquivo` (novo nesta missão) | a parte "somente leitura" é declarada não aplicável quando o sistema considera o arquivo gravável (superusuário) | o contêiner Linux roda como root; a recusa continua testada no Windows e na homologação real |

Nenhuma asserção foi afrouxada para fazer um teste passar: cada mudança acompanha uma mudança de comportamento pedida
pela missão, ou corrige o próprio teste.

### Correção de diagnóstico da CHROME-SECURITY-001

Na CHROME-SECURITY-001 eu atribuí a falha intermitente de `RecorderStudioTest.s002001SemNavegador` ao Chrome da usuária
(processo do navegador conectado durante a suíte). **Esse diagnóstico estava errado.** A causa é uma condição de corrida
do próprio teste: o `FakeBrowser` começa "conectado 0.4.0" e o teste lia o texto da barra de status antes de a nova
fotografia do Recorder chegar à EDT. Corrigido nesta missão (espera de até 5 s pela propagação); a suíte passou nas
execuções completas listadas acima, com o Bridge isolado por `NEKKORA_BRIDGE_DIR`.

### Matriz S006

Os 80 casos, cada um com o teste automatizado e/ou o passo da homologação que o cobre, estão em
[test-plan.md](../docs/studio-006/test-plan.md). Todos passaram nas execuções registradas aqui.

## 18. Homologação real

> Execução da **entrega original** (`d2e0eec`, 60 verificações). A execução final, com 74 verificações em `db0e757`,
> está na §22; as evidências em `reports/studio-006/evidence/` são as dessa execução final.

Execução final: **60 verificações, 0 falhas**, sobre o build de `d2e0eec` (Windows 11, tela 1920×1032, JDK 21.0.12,
Chrome for Testing 155.0.8059.39, extensão com árvore da `main` `2c7c06b`, Native Host instalado pelo script oficial
`install-host.ps1`, laboratório local fictício `/lab/studio005/login-erro.html`). Diretório de dados e Bridge isolados
(`NEKKORA_STUDIO_DATA_DIR`, `NEKKORA_BRIDGE_DIR`). Mouse e teclado do sistema (Robot): menu Arquivo, Ctrl+S, Ctrl+O,
Ctrl+Z/Ctrl+Y, diálogo de arquivos do sistema (caminho colado no campo, Enter), botões dos diálogos.
Log completo: [`reports/studio-006/evidence/S006-homologacao-log.txt`](studio-006/evidence/S006-homologacao-log.txt);
logs do Studio da sessão: [`S006-logs-do-studio.txt`](studio-006/evidence/S006-logs-do-studio.txt); capturas
`S006-01` a `S006-16` (só janelas do Studio e páginas de teste).

| Cenário | O que foi feito de verdade | Resultado |
|---|---|---|
| 1 — Gravar e salvar | gravação real (Capturar e interagir): usuário e senha **digitados** e clique em Entrar → 3 etapas gravadas e evidências; **Ctrl+S durante a gravação** → mensagem exata da missão, nada gravado, gravação intacta; Finalizar; Ctrl+S → diálogo "Salvar projeto como" → `login-006.nekkora`; o Studio foi depois **encerrado** e um **novo processo do Studio** abriu o arquivo | OK: só entradas do formato; usuário e senha ausentes de todos os bytes; manifesto 1.0.0 / `nekkora-privacy/2`; `encrypted: false`; leitura independente com mesmas identidades, etapas `RECORDED` e evidências; novo processo com as 3 etapas, mesmas identidades e procedência; EDT 5 ms |
| 2 — Editar e salvar | renomear etapa, comentário, **parâmetro** (variável `cliente.usuario`) e **localizador manual**; Ctrl+Z/Ctrl+Y reais; Ctrl+S; Arquivo → Fechar projeto; Arquivo → Projetos recentes → reabrir | OK: `*` e estado corretos; reaberto sem IDs novos; original "Clicar no botão Entrar" intacto ao lado do nome editado; variável preservada; localizador manual ainda **Não verificado**; histórico vazio; arquivo não alterado e nada executado ao abrir |
| 3 — Evidências | mensagem de erro **real** do laboratório recebida na gravação; salvar; reabrir | OK: mesmas evidências (identidade e ordem); `NOT_VALIDATED`; texto da página ausente (`SUPPRESSED`); painel mostra "Não validado"; duplicar não copia evidências |
| 4 — Falha de salvamento | arquivo **somente leitura** (NTFS); arquivo **aberto por outro processo** com `FileShare.None`; arquivo **regravado por outro programa** → conflito → "Salvar com outro nome"; **Sair** com alterações: Cancelar; Salvar todos com o destino somente leitura; Salvar todos com sucesso | OK: nos três casos o arquivo anterior ficou com o **mesmo SHA-256**, nenhum temporário, alterações abertas; o conflito preservou as duas versões; Cancelar manteve o Studio; **falha no Salvar todos não encerrou**; sucesso salvou e encerrou |
| 5 — Recuperação | processo filho do Studio: abre o projeto, **consentimento pelo diálogo** (1 min), altera sem salvar, fotografia gravada **pelo temporizador** (59 s); processo **morto à força**; novo processo | OK: arquivo do projeto com o mesmo SHA-256 depois da queda; 1 fotografia na pasta própria, sem valores digitados; oferta "Encontramos alterações não salvas" com projeto, horário e arquivo, nada restaurado antes da escolha; recuperado = "Recuperado — alterações não salvas" com `*`; alterações presentes; arquivo intacto; Ctrl+S gravou no arquivo de origem (base conferida); fotografias apagadas |
| 6 — Compatibilidade `nekkora-privacy/2` | projeto gravado pelos componentes integrados após a CHROME-SECURITY-001; abrir pelo menu três arquivos adulterados (com manifesto recalculado): política `nekkora-privacy/1`, nome acessível com nome de pessoa, entrada `scripts/executar.js` | OK: elementos reabertos declaram `nekkora-privacy/2`; os três arquivos recusados ("política de privacidade", "nekkora-privacy/2", "entrada não permitida") **sem carregar nada**; nenhum texto omitido reativado |
| Logs | handler sobre o `java.util.logging` do processo | OK: só `projeto operacao=… resultado=… duracaoMs=… bytes=… codigo=…`; sem valores digitados, nome do projeto, comentários ou caminhos |

**Defeito encontrado pela homologação**: barra de status com textos sobrepostos em janela estreita — corrigido em
`3ddff58` (ver §15) antes da execução final. Duas execuções anteriores pararam por problemas do **roteiro** (a lista de
abas mostra "127.0.0.1 — Laboratório local" desde a CHROME-SECURITY-001; a última edição desfeita passou a ser o
localizador manual), não do produto.

## 19. Limitações

Ver [known-limitations.md](../docs/studio-006/known-limitations.md). Principais: modo de cópia de segurança sem troca
atômica (intervalo curto sem o nome do arquivo); Windows sem sync de diretório; nenhuma fotografia de recuperação
durante gravação sem quiescência; recuperado só é salvo no arquivo de origem se a base conferir; `revision` persistida;
sem criptografia nem assinatura; textos do usuário podem conter dados pessoais; estado "salvo" por geração.

## 20. Riscos residuais

| Risco | Avaliação |
|---|---|
| Arquivo adulterado com manifesto recalculado | o leitor valida estrutura, domínio e privacidade mesmo assim; não há execução de conteúdo; autenticidade exige assinatura (fora do escopo) |
| Texto pessoal digitado pelo usuário em descrições/comentários | não detectável em geral; avisado na interface; o domínio recusa padrões óbvios em comentários |
| Projeto aberto em pasta sincronizada (OneDrive etc.) | a detecção de conflito evita sobrescrever versão alterada por outro computador; não há merge |
| Recuperação em pasta local do usuário | arquivos não criptografados com o mesmo conteúdo do projeto; consentimento explícito, retenção curta e limpeza |
| Formato 1.0.0 virar compromisso de longo prazo | fixture congelada + schema + versões independentes; qualquer quebra acusa nos testes |
| Recorder sem autorização para dados reais | inalterado (CHROME-SECURITY-001): a STUDIO-006 não muda a captura |

## 21. Preparação para STUDIO-007

- Formato estável e versionado: novas informações (ex.: variáveis declaradas, dados de execução) entram como MINOR
  1.1.0 com migração explícita testada contra a fixture 1.0.0.
- `ProjectService` já separa EDT e worker; uma futura execução poderá abrir o projeto sem tocar o arquivo.
- Pendências que **não** foram iniciadas aqui (fora do escopo): importação como cópia com novas identidades, assinatura,
  criptografia, histórico de versões, sincronização, execução.
- Itens da SECURITY-RELEASE-001 continuam abertos (dados reais não liberados).

Nenhuma funcionalidade da STUDIO-007 foi iniciada.

## 22. Revisão 1 do PR #8

A revisão técnica aprovou arquitetura, formato, reconstrução, evidências e privacidade e apontou dois P1 e um P2. Regra
adotada: **uma operação de salvamento, descarte ou recarregamento não informa sucesso nem destrói o estado anterior
antes de concluir as suas garantias de integridade.** O formato `.nekkora` não mudou (schema e fixtures 1.0.0 intactos).

### Reprodução antes da correção

Os testes novos foram escritos primeiro e executados sobre o código de `d2e0eec` (HEAD `946ec7f`, só documentação
depois). Todos falharam pelo motivo apontado:

| Ponto | Teste | Falha observada em `d2e0eec` |
|---|---|---|
| P1-A | `ProjectStudioTest.p1aRecarregarRecusadoPreservaWorkspace` | "A continua no workspace": esperado `[Consulta de Clientes, Cadastro de Produtos]`, obtido `[Cadastro de Produtos]` |
| P1-A | `ProjectStudioTest.p1aRecarregarESalvarNaoSeSobrepoem` | recarregar durante um salvamento abriu a pergunta "Recarregar do arquivo?" |
| P1-B A | `ProjectStudioTest.p1bDescartarESair` | "não encerra enquanto a limpeza não terminou": esperado 0 encerramentos, obtido 1 |
| P1-B B | `ProjectStudioTest.p1bSalvarESairImediatamente` | sucesso informado com a fotografia superada (`…-g5.nekkora`) ainda no disco |
| P1-B | `ProjectStudioTest.p1bFalhaAoApagarRecuperacao` | o aviso de sucesso não mencionava a fotografia não apagada |
| P1-B | `ProjectRecoveryTest.superadaPorSalvamentoConfirmado` | fotografia anterior a um salvamento confirmado posterior foi oferecida |
| P2 | `ProjectStudioTest.p2SobrescritaAlteradaDepoisDaConfirmacao` | esperado `CONFLICT`, obtido `SAVED`: a versão gravada por outro programa depois da confirmação foi sobrescrita |

As corridas são reproduzidas com **barreiras controladas** nas `FileOps` de teste (gravação, leitura ou exclusão
retidas por latch, ou exclusão com falha injetada), nunca só com `Thread.sleep`. Nos cenários de encerramento, o
diretório de dados é copiado **no instante** em que o Studio encerraria a JVM, e outro Studio é aberto sobre essa
cópia: é exatamente o que um novo processo encontraria.

### Correções

| Ponto | Correção |
|---|---|
| P1-A | `AutomationCatalog.replace` valida identidade e nome único **antes** de trocar e troca na mesma posição; `ProjectService.reload` só descarta o estado anterior (aba, histórico, estado, fotografias) depois da admissão. Qualquer recusa preserva automação, alterações não salvas, seleção, histórico, arquivo e recuperação. Recarregar é recusado durante salvamento ou outro recarregamento do mesmo projeto, sem perguntar nada; salvar, fechar e recarregar esperam um recarregamento em andamento (`ProjectState.reloading`) |
| P1-B | As fotografias superadas por um salvamento são apagadas no **mesmo trabalho do worker**, antes de "Projeto salvo"; uma falha vira aviso honesto e nova tentativa ao sair. Todo trabalho de disco fica registrado; Sair espera por ele **sem bloquear a EDT**, apaga as fotografias devidas e só então encerra; durante a espera aparece "Encerrando o Studio" e um segundo Sair não abre outra pergunta. Se a exclusão falhar, o Studio pergunta "Sair mesmo assim?" e explica que as fotografias poderão ser oferecidas. As alterações descartadas não são fotografadas de novo. `ProjectRecovery.discard/discardAll/clear` informam o resultado e apagam pelas `FileOps`; a varredura não oferece (e apaga) fotografia anterior a um salvamento confirmado posterior do arquivo de origem (comparação estrita; o conteúdo-base nunca conta como superação; na dúvida, oferece) |
| P2 | Salvar como sobre arquivo existente lê a impressão digital do destino fora da EDT **antes** da confirmação e a passa como `expected`: o repositório a confere antes e de novo imediatamente antes da troca. Alteração, substituição ou remoção depois da confirmação = conflito com a mensagem "foi alterado por outro programa depois da sua confirmação"; nada é sobrescrito. `overwrite` sem `expected` fica só para arquivos do próprio Studio (fotografias de nome único) |

Achado da homologação desta revisão, corrigido em `db0e757`: o aviso "Encerrando o Studio" aparecia também durante um
"Salvar todos" que acabava falhando, ao lado de "O Studio continua aberto". Agora ele só aparece quando a espera pelo
trabalho de disco realmente acontece; a homologação confere a ausência dele na falha.

Detalhes: [conflict-handling.md](../docs/studio-006/conflict-handling.md), [recovery.md](../docs/studio-006/recovery.md),
[decisions.md](../docs/studio-006/decisions.md) (R1-01 a R1-07), [test-plan.md](../docs/studio-006/test-plan.md) e
[known-limitations.md](../docs/studio-006/known-limitations.md) (L16 a L18).

### Regressão final

| Execução | Commit | Resultado |
|---|---|---|
| Testes novos do app (6) repetidos 10 vezes seguidas | `a864089` (antes do commit, mesmo código) | 10/10 rodadas sem falha |
| Windows 11, `./mvnw clean verify` (Bridge e dados isolados) | `a864089` | 426/426, BUILD SUCCESS |
| Linux de referência, `tools/golden-linux.sh verify` | `a864089` | 426 executados: 425 aprovados + 1 pulado (superusuário), BUILD SUCCESS |
| Windows 11, `./mvnw clean verify` | **`db0e757`** | **426/426**, 0 pulados, BUILD SUCCESS |
| Linux de referência, `tools/golden-linux.sh verify` | **`db0e757`** | **426 executados: 425 aprovados + 1 pulado** (a mesma *assumption* de superusuário da §17), BUILD SUCCESS |
| Homologação real (Windows, Chrome for Testing 155.0.8059.39, extensão da `main`, Native Host instalado) | `a864089` + roteiro de `a27f81f` | 73/73 |
| Homologação real | **`db0e757`** | **74/74**, 0 falhas |

Por módulo (`db0e757`, iguais nos dois sistemas): tokens 24, swing-ui 47, showcase 5, domínio 93 (+1), bridge 22,
recorder 62, persistência 44 (+2), studio-ui 14, app 114 (+6), escala 150% 1. Total 426 = 417 da entrega original + 9
novos. Nenhum teste anterior foi removido ou afrouxado; o único ajuste em código de teste existente foi na
infraestrutura (`Ops` com barreiras por filtro e falha de exclusão injetável; gancho no instante do encerramento).

- **Golden screenshots**: 4/4 no Windows e no Linux, **sem regenerar** nenhuma imagem.
- **Matriz S006**: os 80 casos continuam cobertos e passaram nas execuções acima.
- **Extensão**: nenhuma alteração (árvore idêntica à `main` `2c7c06b`, sem alterações locais); suítes da extensão não
  repetidas pelo mesmo motivo da §17; a integração real foi exercitada de novo na homologação.

### Homologação real: o que entrou

Os seis cenários da §18 foram repetidos, mais três processos REAIS do Studio que terminam por `System.exit` (como a
aplicação), no mesmo diretório de dados do cenário 5:

| Fase | O que foi feito | Resultado |
|---|---|---|
| Sair → Descartar | abre o projeto, altera, grava uma fotografia; Arquivo → Sair → **Descartar alterações** no diálogo real (captura `S006-17`) | no instante do encerramento, 0 fotografias no disco; processo terminou com código 0; arquivo do projeto com o mesmo SHA-256 |
| Salvar e sair | abre, altera, grava uma fotografia; **Ctrl+S** e, assim que o salvamento é informado, Arquivo → Sair | no instante em que o sucesso é informado, a fotografia superada já não existe; 0 no encerramento; o arquivo tem a alteração |
| Novo processo | abre o Studio no mesmo diretório de dados e espera 10 s (captura `S006-18`) | nenhuma oferta de recuperação; nada aberto |
| Falha no Salvar todos | (cenário 4) | "O Studio continua aberto" e nenhum "Encerrando o Studio" |

Capturas: as de tela inteira foram recortadas para excluir a faixa inferior da área de trabalho, e as dos processos
filhos para a área da janela do Studio. Na execução final, essa faixa mostrava conteúdo pessoal de outra janela atrás do
Studio. Nenhuma outra edição foi feita nas imagens.

### Restrições mantidas

Sem merge. STUDIO-007 não iniciada. Recorder continua sem liberação para dados reais (homologação só com o laboratório
fictício). Formato `.nekkora` inalterado.

## 23. Higienização da evidência S006-02 (depois da integração)

Pedido da revisão final do PR #8. A captura `S006-02-salvar-como.png` mostrava a pasta Documentos da máquina de
homologação, com nomes de pastas do ambiente pessoal, num repositório público. Ela foi substituída por uma captura
NOVA e autêntica do mesmo diálogo real "Salvar projeto como": o Studio real com diretório de dados isolado, uma
automação nova "Login homologacao 006" e o **Ctrl+S real**, com uma pasta de projetos isolada que contém só nomes
fictícios como última pasta usada. A captura cobre só o diálogo. Nenhum outro arquivo de evidência mudou e nenhuma
funcionalidade foi alterada.

A versão anterior da imagem continua no histórico do Git: o histórico **não** foi reescrito (não autorizado; a imagem
continha nomes de pastas, sem credenciais nem segredos).

