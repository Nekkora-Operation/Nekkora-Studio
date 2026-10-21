# NEKKORA-CODEGEN-QUALITY-001 — Desenho (G1)

Estado: em implementação local (G-1: `REMOTE_PUBLICATION_BLOCKED_OWNER_DECISION`). Este documento fixa os contratos
ANTES do código; mudanças posteriores entram como decisão numerada no fim.

## 1. Onde nasce cada nome (fonte da verdade)

| Símbolo | Fonte, em ordem | Exemplo (E2E-002) |
|---|---|---|
| Page (classe) | título REVISADO da seção do PDD em que a tela começa → 1ª palavra de conteúdo; senão nome seguro da sessão; senão `Pagina<N>` + apontamento `NOME_SEM_EVIDENCIA` | "Pesquisa do artigo" → `PaginaPesquisa`; "Extração dos dados" → `PaginaExtracao` |
| prefixo da Page / área do traceId | a mesma palavra, em minúsculas | `pesquisa`, `extracao` |
| método | (A) todas as etapas num ÚNICO bloco do PDD cujo título começa por verbo no infinitivo → verbo + 1ª palavra de conteúdo; (B) senão, pela ação: `preencher_<entrada>`, `conferir_<saída>`, `ler_dados`, `clicar_<alvo>`… | "Pesquisar o artigo pelo nome" → `pesquisar_artigo`; bloco genérico "Etapas do processo" → `ler_dados` |
| alvo (chave do registro) | rótulo associado (label) → âncora da linha (th/dt) → nome acessível → `campo_<variável de entrada>` (preenchimento) → `titulo` (cabeçalho lido) → apontamento | `nome_completo`, `campo_termo_de_busca` |
| campo lido / coluna | nome da variável SAÍDA DE ETAPA (já revisável em Variáveis e dependências) | `nascimento` |
| traceId (`step_id`) | `<área>.<ação>_<objeto>` na 1ª geração, depois FIXO por StepId (registro de rastreio) | `extracao.ler_nome_completo` |

Nada é inventado: sem evidência, o nome é provisório e o Quality Gate mostra `NOME_SEM_EVIDENCIA` com o lugar a
corrigir (título no PDD ou nome da variável). O texto do PDD já passou pela política de texto (sem dígitos, URLs,
seletores, identificadores, dados) e foi revisado por um ator do Studio.

Palavras ignoradas (artigos, preposições, contrações) e títulos genéricos ("Etapa(s) do processo", "Bloco do processo",
"Sessão", "Tela") estão em `CodeNames` e têm teste. Nomes passam a ASCII (sem acento), `snake_case`/`PascalCase`,
nunca palavra reservada do Python nem membro do `BasePage`/`RpaProcess`; colisão → sufixo `_2`, `_3` na ordem do modelo.

## 2. Telas (Pages) por evidência

Uma tela nova começa quando: (a) a tela opaca registrada muda (modelo de endereço + título); ou (b) a etapa
anterior registrou TROCA DE DOCUMENTO OU DE ROTA (`DOCUMENT_CHANGED`/`ROUTE_CHANGED`, `screenChangeAfter`). Mudar de
sessão sozinho NÃO cria tela (sites de página única): cria um método novo na mesma Page. No E2E-002, a busca e a ficha
têm o mesmo modelo de endereço (`/{…}/{…}`), mas o Enter registrou `DOCUMENT_CHANGED newDocument=true`: são duas Pages.

## 3. Rastreabilidade: traceId × StepId (decisão D-CQ-03)

- O código usa `step_id="<traceId>"` legível. O framework (0.2.0 e 0.3.0) aceita o token (`[A-Za-z0-9][A-Za-z0-9_.:-]{0,79}`).
- `manifest.json` 1.3.0 ganha `trace` (`nekkora-studio/trace-map/1.0.0`): `traceId ↔ studioStepId ↔ ação ↔ chave do alvo
  ↔ símbolo Python`. É bijetivo (teste) e é a única ponte para eventos, proveniência e contrato V5.
- Registro de rastreio por automação (`<dados>/desenvolvimento/<automationId>/rastreio.json`,
  `nekkora-studio/trace-registry/1.0.0`): o traceId é atribuído na 1ª geração da etapa e REUTILIZADO depois — mudar o
  título do PDD, a ordem das etapas ou o nome da Page não muda o traceId. Etapa removida tem o token aposentado (nunca
  reaproveitado por outra etapa). Sem registro (dados do Studio apagados), o `trace` do manifesto anterior da pasta de
  saída da MESMA automação é usado como semente.
- Checkpoints do framework não guardam `step_id` (verificado em `core/checkpoints.py` e `core/engine.py`): retomada não
  depende do token. Eventos antigos continuam legíveis (UUID); a proveniência do Studio lê os dois.
- O UUID não aparece mais no corpo de `paginas.py`/`processo.py` (nem em comentário): fica no manifesto e no
  `selectors.json` (`provenance.studioStepId`, contrato do framework).

## 4. IR tipado (D-CQ-04)

`CodePlan` (módulo codegen) é montado ANTES da emissão a partir da configuração validada, das etapas prontas e dos
nomes (`CodeNames`): Pages (classe, prefixo, docstring, identidade, métodos), métodos (nome, parâmetros, retorno,
operações na ordem do modelo), operações (traceId, StepId, tipo, chave, leitura/opcional/conferência), modelos de dados
(dataclass só quando um método lê 2+ dados) e o processo (chamadas, linha da planilha pela regra declarada). Os
emissores só traduzem o `CodePlan` para texto; o manifesto descreve o mesmo `CodePlan` (`architecture`).

## 5. Dados e erros (Q02)

- Leitura opcional (só onde o PDD declarou fonte alternativa): `None` quando o alvo NÃO EXISTE na página; ambíguo ou
  identidade divergente continua ERRO do framework (`exists` nunca devolve "existe" por aproximação).
- Data por extenso incompleta, ambígua ou impossível: `BusinessRuleRejected("DATA_INCOMPLETA_OU_AMBIGUA", step_id=<traceId>)`
  (antes virava `""` e depois `DADO_INCOMPLETO`).
- Coluna obrigatória vazia depois das fontes alternativas: `BusinessRuleRejected("DADO_OBRIGATORIO_VAZIO")`.
- Conferência de endereço: `BusinessRuleRejected("ENDERECO_DIFERENTE_DO_ESPERADO", step_id=<traceId>)`.
- Excel continua 100% no `XlsxResultSink` (tudo ou nada, texto antifórmula, chave sem repetição, reabertura).

## 6. Localizadores (Q03)

Ordem de preferência mantida da gravação/debug (alvo CONFIRMADO): identificador forte (id, `data-testid`, papel+nome,
rótulo) → âncora semântica (linha `th→td`, termo `dt→dd`, região/landmark + atributo) → XPath relacional. Bloqueios na
geração (código `LOCATOR_POSITIONAL` / `LOCALIZADOR_POSICIONAL`): estratégia POSITIONAL, `:nth-child`, `:nth-of-type`,
`>> nth=`, XPath absoluto (`/html…`), índice numérico em XPath fora de `following-sibling::x[1]`/`preceding-sibling::x[1]`,
`position()`, `last()`. Unicidade: o framework recusa 0 ou 2+ correspondências (`TARGET_AMBIGUOUS`) — nunca escolhe o
primeiro. O tipo de cada localizador vai para o manifesto (`locators`) e para o README ("como cada elemento é localizado").

## 7. Quality Gate (camada nova, sem substituir V0–V6)

`VQ — Qualidade do código gerado`: (1) análise do `CodePlan`/manifesto em Java (sempre disponível) e (2) análise AST
do Python gerado (`qualidade_codigo.py`, mesmo intérprete de V4/V5). Diagnósticos: `UUID_EXPOSTO_NO_CODIGO`,
`COMENTARIO_POR_ETAPA`, `NOME_GENERICO`, `NOME_SEM_EVIDENCIA`, `VARIAVEL_OPACA`, `TRACE_SEM_MAPEAMENTO`,
`TRACE_ALIAS_CONFLITO`, `LOCALIZADOR_POSICIONAL`, `SELETOR_NO_CODIGO`, `DOCSTRING_AUSENTE`, `SAIDA_OPCIONAL_SEM_REGRA`,
`SILENCIA_FALHA` (`or ""`, `except: pass`). `FRAMEWORK_CATALOG_MISSING` (D13) continua soberano: com ele, VQ fica
`NOT_CHECKED` e nada é dado por validado. VQ verde NÃO é aceite de legibilidade: o aceite é só da titular.

## 8. Compatibilidade

- Configuração `codegen-config` 1.1.0/1.2.0 sem mudança de esquema (os nomes vêm no `Group` já existente e no
  `CodeNames`, separado e versionado no manifesto). Requisitos do framework inalterados: 1.1.0 → `nekkora-rpa==0.2.0`;
  1.2.0 → `nekkora-rpa[excel]==0.3.0`. Nenhuma migração de versão do framework.
- Tabular 1.0.0 (configuração DECLARADA, Automação → Avançado): fora do escopo, byte a byte igual.
- Árvore de arquivos igual (`paginas.py`, `processo.py`, `main.py`…): o `OutputWriter` (`.novo`/`.diff`) segue valendo
  sem plano de migração de caminhos.
