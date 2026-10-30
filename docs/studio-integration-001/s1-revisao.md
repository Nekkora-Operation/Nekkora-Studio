# S1 — Revisão técnica independente

Objeto: `1cbf669..da7bb94`, os 22 commits locais, com 424 arquivos alterados.

Método:
- Três revisões independentes, feitas em paralelo e só por leitura, sem ponto de partida nos relatórios das missões:
  - S1-A: gerador de código;
  - S1-B: validação e especificação;
  - S1-C/D: interface, Studio Lean, integração com os módulos e higiene para publicação.
- Experimentos fora do repositório com as classes compiladas, os scripts Python isolados e o framework 0.3.0/0.2.0 extraído da `main`.
- Cada achado classificado foi conferido por mim. Os de correção têm reprodução automatizada (vermelho) antes do conserto (§3).

## 1. Achados

| ID | Classificação | Onde | Achado | Destino |
|---|---|---|---|---|
| **R-01** | CORREÇÃO NECESSÁRIA | `nekkora-studio-ui/.../DevelopmentPanel.java` (rodapé) | Com o botão "Revisar código", os nove botões do rodapé não cabem numa linha na largura da janela (`min(1180, tela−40)`) e o "Fechar" fica cortado. As capturas da homologação mostram "Fech", e no estado "em execução" o botão some. É defeito de acessibilidade. | corrigido (S3-1) |
| **R-02** | CORREÇÃO NECESSÁRIA | `PythonChecks.quality` → `DevelopmentPresenter.finish` | Um aviso só de legibilidade da camada VQ (`NOME_GENERICO`, `DOCSTRING_AUSENTE`, `VARIAVEL_OPACA`, que são WARNING na validação) põe `CODE_QUALITY` em FAIL. Com isso, a janela Desenvolver tira `CODE_VALIDATED` e mostra "código gerado com problemas", enquanto a validação diz ATENÇÃO. Basta uma seção do PDD sem título, que gera `Page1`. | corrigido (S3-2) |
| **R-03** | CORREÇÃO NECESSÁRIA | `SemanticEmitter.doc`, regras de `build_spreadsheet_row`, docstring de fluxo do `TabularEmitter` | Texto do usuário (cabeçalho de coluna, rótulo da página, título do PDD) entra em docstring sem escape. Um cabeçalho legítimo como `Nome\Nascido` gera `malformed \N character escape` (não compila), outras barras caem no W605 e um `"""` fecha a docstring antes da hora. O pacote anterior é sobrescrito antes de o gate reprovar. | corrigido (S3-3) |
| **R-04** | CORREÇÃO NECESSÁRIA (baixa) | `LocatorPolicy.positional` | Deixa passar `(//td)[2]`, `(//div[@class='x'])[3]` e `:first/last/only-of-type`, `:only-child` e pseudo-classes em maiúsculas. O localizador posicional único passa calado, embora o manifesto declare a exclusão `POSITIONAL_LOCATORS`. | corrigido (S3-4) |
| **R-05** | RESSALVA → corrigida | `contrato_projeto.py` (`output_requirement`) | A conferência V5 é mais frouxa que o gerador: aceita qualquer texto diferente de `REQUIRED` como opcional, `OPTIONAL` sem motivo e `keyField` do manifesto diferente da chave do código. A S1-B pede coerência entre validação e geração e a correção é pequena, por isso foi corrigida. | corrigido (S3-5) |
| R-06 | RESSALVA | `DevelopmentStore.traceRegistry`, `GenerationOrchestrator` | Se o `rastreio.json` está ilegível, ou numa versão futura em caso de downgrade, ele vira registro vazio sem aviso. A semente vinda do manifesto perde os ids aposentados e, na geração seguinte, os `previousTraceIds`. Só ocorre no caminho de recuperação ou downgrade, com perda de rastreabilidade e não de dados de negócio. | registrada; missão própria |
| R-07 | RESSALVA | `RunProvenance` | Com as leituras principal e alternativa na mesma transação, a proveniência afirma `PRINCIPAL` mesmo quando o valor veio da alternativa ou ficou vazio. A auditoria fica inexata, sem vazamento. | registrada |
| R-08 | RESSALVA | `SemanticEmitter` (`DADO_OBRIGATORIO_VAZIO:{field}`) | Com campo de 58 caracteres ou mais, o motivo passa de 80 caracteres (o limite do `TOKEN` do framework) e o item vira erro de configuração. Não é alcançável pela interface: o planejador limita o campo a cerca de 53 caracteres. Só é alcançável com uma configuração escrita à mão. | registrada |
| R-09 | RESSALVA | `OutputWriter` | Os caminhos são conferidos só pela forma do texto (sem `toRealPath`), então uma junction dentro da pasta de saída escreveria fora dela; a exploração exige quem já escreve na pasta. Duas aposentadorias com a mesma revisão misturam `previous-generation/<versão>`. | registrada |
| R-10 | RESSALVA | `DevelopmentPresenter` ("Revisar código") | Lê o manifesto e 2 a 4 `.py` na EDT, no mesmo padrão já existente de `loadDraft`. São arquivos pequenos e as exceções são tratadas. | registrada |
| R-11 | RESSALVA | `Studio014Homologation.java`, `E2e001Homologation.java` (testes) | Harnesses antigos ainda esperam `paginas.py`/`processo.py`, mas o gerador agora emite `automation/`. O produto não é afetado; só não dá para reexecutá-los sem ajuste. | registrada |
| R-15 | RESSALVA | `DevelopmentPanel` (aba Pendências) | Em 200% numa tela de 1080p (476 px lógicos de altura), a lista de pendências fica com altura zero e a primeira linha da pergunta fica sob as abas. A `main` já reduzia a lista a uma linha; agora as duas linhas do rodapé (S3-1) tomam essa altura. O rodapé passou a ser correto em todas as escalas: na `main`, "Fechar" saía da janela já em 100%. Corrigir exige redesenhar a aba (S4 §3). | registrada; decisão da titular |
| R-12 | MELHORIA FUTURA | `SingleGenerator` (pré-voo) | O pré-voo `OPTIONAL_READ_WITHOUT_REQUIRED_ANCHOR` roda só na geração e não no "Validar automação" (V3). Bloqueia no sentido seguro, mas o aviso chega tarde. | — |
| R-13 | MELHORIA FUTURA | `SpecResolver` | Decisão de obrigatoriedade vencida (fingerprint antigo) volta a obrigatória só com o texto "padrão, sem decisão". O sentido é o seguro. | — |
| R-14 | MELHORIA FUTURA | `CodePlan.identifiers` | `address_before`/`field_before` não são reservados como nomes locais. É improvável. | — |

**Higiene para publicação**, com a decisão da titular de 29/10 (S0 §6):
- 11 logs com caminho pessoal: saneados na reprodução.
- 20 capturas: tarjadas na reprodução.
  - 18 têm caminho pessoal: em várias rodadas e telas, em vez de 13, a contagem exata após OCR.
  - 1 mostra o conteúdo real da pasta Documentos (`SITES-G6-01-escolher-lista`).
  - 1 deixa vazar um trecho da área de trabalho no rodapé (`rodada-4/.../SITES-G6-04-validacao-v6`).
- Nenhum SHA256SUMS ou manifesto cita esses arquivos, então nenhuma lista de hashes precisou ser regenerada.
- E-mails, nomes de máquina, tokens e chaves: nenhum. As únicas credenciais são as públicas do the-internet (`tomsmith`), que já existiam.
- As 2 planilhas XLSX têm dados fictícios.

Fora desta missão: a `main` pública já contém caminhos pessoais em evidências antigas. Exemplos: `docs/e2e-002/evidence/homologacao-log.txt`, `docs/packaging-001/evidence/g5/*`, `docs/studio-014/evidence/S014-homologacao-log.txt`, `docs/studio-010/g0-experimentos/*` e a captura `docs/e2e-002/evidence/capturas/G6-01-escolher-lista.png`, que é o mesmo arquivo de `SITES-G6-01`. Isso fica como ressalva para decisão da titular; nenhum arquivo da `main` foi alterado.

## 2. Conferido e correto

- **Gerador:**
  - nada ficou órfão com a remoção de `Emitter`/`Modern`;
  - os três ramos importam contra o framework declarado: lista→planilha contra 0.3.0, execução única e tabular contra 0.2.0;
  - `selectors.json` coincide com `automation/selectors.py`;
  - as assinaturas batem com o 0.3.0;
  - `Py.str`/`pref` escapam corretamente;
  - valores de entrada e credenciais nunca entram no código (`secrets.get(alias)`);
  - duas gerações seguidas produzem os mesmos bytes, sem datas nem caminhos absolutos, sempre com `\n`;
  - colunas opcionais: `REQUIRED_FIELDS`, `_filled`, `_date_if_present`, a chave opcional recusada e `None` como célula vazia, sem nada inventado;
  - os 9 hashes do manifesto do pacote real conferem byte a byte.
- **Validação:**
  - `FunctionalSpec` 1.2.0 só com `OUTPUT_REQUIREMENT`;
  - as specs 1.0.0 e 1.1.0 serializam idênticas;
  - o vocabulário é fechado;
  - a decisão muda a revisão;
  - nenhum BLOCKER se perde até a interface;
  - a IA não pode propor obrigatoriedade;
  - `rastreio.json` e a proveniência guardam só estrutura;
  - `TraceRegistry` 1.0.0 é lido como convenção `pt`, sem reaproveitar ids;
  - os layouts antigos (`paginas.py`/`--entrada`) continuam aceitos.
- **Interface e Lean:**
  - a remoção da dependência AI-Engine→guided não quebra nada em runtime (sem reflexão nem ServiceLoader; o guided continua no classpath pelo codegen);
  - nenhum recurso versionado é excluído do jar;
  - `.gitignore` e `.gitattributes` não escondem nada;
  - carregar, salvar, recuperar e desfazer/refazer do `.nekkora` não foram tocados;
  - geração e escrita continuam no worker;
  - o botão novo usa o design system e tem nome e descrição acessíveis.
- **Integração:** nenhum dos 22 commits toca Bridge, Recorder, Debug guiado, PDD visual, Validation (módulo) ou Semantics, nem contrato com a Chrome Extension. O impacto indireto está todo coberto: camada VQ no gate, opções de CLI lidas do manifesto (com fallback) e aposentadoria em `previous-generation/`.

## 3. Correções (S3)

As cinco correções estão na branch `fix/studio-integration-001`, sobre `da7bb94`, e foram reproduzidas na branch de integração. Cada uma tem prova vermelho/verde.

| # | Commit (local → integração) | Teste vermelho antes | Verde depois |
|---|---|---|---|
| S3-1 (R-01) | `1ebf6f6` → `3738b6c` | `DevelopmentPanelUiTest.rodapeNaoCortaFechar`: Fechar em `x=1120 w=72` num painel de 1164 | UI 20/20 |
| S3-2 (R-02) | `7328634` → `af2962b` | `CodeValidatedTest.avisoNaoBarra`: `checksPass` extraído sem mudar o comportamento, falha | app (`CodeValidated`, `DevelopmentStudio`, `CodeReviewText`) 13/13 |
| S3-3 (R-03) | `2cf559f` → `bbcda0e` | `DocstringEscapeTest`: `ast.parse` real, `malformed \N character escape` em `process.py:50` | 1/1; codegen 39/39 (bytes de texto comum inalterados) |
| S3-4 (R-04) | `b83f04d` → `f879af5` | `CodegenQualityRulesTest.localizadores`: `(//td)[2]` não era posicional | codegen 40/40, com o teste de `Py.docText` |
| S3-5 (R-05) | `c0777b8` → `4009ba7` | `OptionalColumnsTest#contratoV5RecusaManifestoForaDoVocabulario`: o requisito desconhecido passava | `OptionalColumnsTest` 14/14 |

Nenhuma correção altera contrato de pacote, manifesto ou protocolo. Bytes gerados a partir de texto comum continuam iguais, e isso é conferido de novo na S2/S5 com o pacote real.
