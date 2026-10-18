# NEKKORA-E2E-001 — Relatório final (RPA Challenge · Input Forms)

Data: 2026-10-18. Operador: Claude (Claude Code) como **`TEST_AGENT`**; nenhuma decisão do agente é apresentada como
aprovação humana. Merges, releases e publicações: **nenhum** (decisão da usuária).

## 1. Resultado em uma linha

A cadeia **Recorder → Excel → PDD → Guided Debug → gerador Python do Studio → execução → resultado** rodou de verdade:
o projeto gerado pelo Studio, instalado sobre `nekkora_rpa` 0.2.0 numa venv limpa, completou as 10 rodadas em
`https://rpachallenge.com` com o placar **lido da página**: *"Your success rate is 100% ( 70 out of 70 fields) in
2404 milliseconds"* na tentativa oficial (e 100% nas outras duas tentativas reais, ver §4). Matriz de critérios:
**92/100** (§3). 100 pontos ≠ 100% de precisão do site: são avaliações distintas.

## 2. Repositórios, branches e HEADs

| Repositório | Base (`main`) | Branch | HEAD de código | Ordem de integração |
|---|---|---|---|---|
| Nekkora-RPA-Framework | `cf518bf` | `feat/e2e-001-rpa-challenge` | `965480b` (docs `8b1be58`) | 1º |
| Nekkora-Extension-Chrome | `88df603` | `feat/e2e-001-rpa-challenge` | `6307268` (docs `4c9f9e2`) | 2º |
| Nekkora-Studio | `2a8211f` | `feat/e2e-001-rpa-challenge` | `f9c09c1` (+ commit desta documentação) | 3º |

Depois dos merges autorizados, um novo teste integrado é necessário (a homologação desta missão usou as três branches
juntas).

## 3. Critérios (seção 8 da missão)

| # | Critério | Peso | Status | Pontos | Evidência | Observação |
|---|---|---|---|---|---|---|
| C1 | Ler proposta real e definir mapeamento sem hardcode | 10 | PASS | 10 | `docs/e2e-001/architecture-and-gap.md` §2; `evidence/projeto/rpa-challenge.codegen.json` | instruções, rótulos, Start, download e resultado lidos do site real; colunas → variáveis declaradas no modelo e na configuração de geração, sem valores |
| C2 | Recorder real e projeto Studio persistido | 15 | PASS | 15 | `evidence/g2-g5/homologacao-log.txt` (G2); `evidence/projeto/rpa-challenge.nekkora` | 11 etapas com proveniência RECORDER, StepIds estáveis após salvar/reabrir, LABEL validado nos 7 campos |
| C3 | Download XLSX real + leitura correta; CSV genérico testado | 10 | PASS | 10 | `evidence/g1/g1-evidencia.json`; framework `tests/unit/test_tabular.py` | 34 124 bytes, sha256 `cf1a8c04…1c04d37`, `Sheet1`, 10 registros, 0 problemas, cabeçalho "Last Name " normalizado, telefone inteiro lido como texto; CSV com fixture sintética |
| C4 | PDD visual produzido no Studio com privacidade | 10 | PARTIAL | 8 | `evidence/g2-g5/pdd-visual-v001.pdf`, imagens sanitizadas, texto extraído | PDF do Studio sem valores; **só 1 de 2 e 1 de 8 marcadores posicionados** depois da recarga; Conclusão sem imagem |
| C5 | Guided Debug inicial no navegador real, identidade válida | 15 | PARTIAL | 12 | `evidence/g2-g5/G4-plano.json`, `G4-registro-debug.json` | 10 decisões CONFIRMED (TEST_AGENT), registro da mesma revisão do plano; mas **a identidade registrada não sobrevive à recriação dos campos**: 8/10 e 7/10 etapas exigiram correção guiada, e depois dela o cartão volta em "Requer revisão" |
| C6 | Código gerado pelo Studio usando framework instalável | 15 | PASS | 14 | `evidence/g2-g5/G5-resultado-da-geracao.txt`; `evidence/g5-codigo-gerado/` (GUI); `evidence/g5-codigo-final/` + `DIFF-gerador-anterior.txt` + `G5-validacao.json` | gerado pelo menu; determinístico; edição à mão protegida; venv limpa compila/importa; contratos, ruff (perfil do framework) e mypy estrito OK no código final. −1: o código FINAL foi regenerado pelo mesmo gerador sem janela (só imports e quebra de linha mudaram) |
| C7 | Python real executado e depurado, com reparo rastreável | 10 | PARTIAL | 8 | `evidence/g6/*`, `evidence/g6-laboratorio/*`, commits `ec42309`, `1059431` | eventos por StepId (11/11), contratos das saídas válidos; reparos rastreáveis no GERADOR e no CONTRATO (defeito → teste que falha → correção → reteste). **Desvio:** o laboratório rodou depois das tentativas reais; nenhum erro de alvo exigiu reparo do modelo |
| C8 | Resultado no site, 10 rodadas, prova de precisão e relatório | 15 | PASS | 15 | `evidence/g6/<tentativa>/observacoes.json`, `summary.json`, `events.jsonl` | 3 tentativas reais, todas 100% (70/70), 10 transações SUCCEEDED cada; placar lido da página; na réplica, oráculo do servidor: 1 início, 10 submissões em ordem, todas corretas |
| | **Total** | **100** | | **92** | | |

## 4. Tentativas no site real (todas relatadas)

| Rótulo | Código executado | Venv | Placar lido da página | Transações |
|---|---|---|---|---|
| `depuracao-1` | gerado pela GUI (gerador antes da correção de estilo) | nova, wheel 0.2.0 da branch | 100% ( 70 out of 70 fields) in 2488 ms | 10 SUCCEEDED |
| `oficial` | o mesmo, sem alteração | nova | **100% ( 70 out of 70 fields) in 2404 ms** | 10 SUCCEEDED |
| `verificacao-codigo-final` | regenerado após a correção do gerador (diff anexado) | nova (framework `965480b`) | 100% ( 70 out of 70 fields) in 2478 ms | 10 SUCCEEDED |

Cada tentativa é um navegador novo (Start uma única vez). Nenhuma submissão foi repetida. Entrada conferida em todas:
`entrada_sha256 = cf1a8c04…1c04d37` (a planilha baixada NA EXECUÇÃO tem o mesmo hash da baixada no G1).

## 5. As 8 etapas: origem, ator, evidência e status

| Etapa | ORIGEM (o que produziu) | ATOR | EVIDÊNCIA | STATUS |
|---|---|---|---|---|
| 1. Recorder | Recorder do Studio + extensão em Chrome for Testing (aba AUTORIZADA, origem exata) | TEST_AGENT (Robot/CDP nos controles reais) | `homologacao-log.txt` G2; `rpa-challenge.nekkora` | PASS |
| 2. Download e leitura do Excel | `nekkora_rpa` 0.2.0 (`Actions.download` + `tabular`) — no G1 e DENTRO do código gerado (`a.download`) | TEST_AGENT (G1) / processo gerado (G6) | `g1-evidencia.json`; `observacoes.json` (`entrada_sha256`) | PASS |
| 3. PDD | Studio "PDD visual…" com 2 capturas do perfil BENCHMARK, sanitizadas pelo produto | TEST_AGENT (revisão de textos registrada como tal) | `pdd-visual-v001.pdf` (+ manifesto), imagens, texto extraído | PARTIAL (marcadores) |
| 4. Guided Debug | debug guiado do Studio + sensor da extensão, 2 passagens com o formulário recriado | TEST_AGENT (correção guiada "Selecionar no navegador") | `G4-plano.json`, `G4-registro-debug.json` | PARTIAL (identidade não sobrevive à recriação) |
| 5. Python gerado | gerador do Studio (menu) → `g5-codigo-gerado`; mesmo gerador sem janela → `g5-codigo-final` | Studio (gerador); TEST_AGENT aciona | `G5-resultado-da-geracao.txt`, `manifest.json`, `G5-validacao*.json`, diff | PASS |
| 6. Execução real | `main.py` gerado, sem alteração, em venv limpa, Chrome 155 instalado | processo gerado (TEST_AGENT só inicia o comando) | `g6/*/events.jsonl`, `checkpoint.json`, `summary.json` | PASS |
| 7. 10 rodadas | engine do framework: 10 transações (uma por linha), prova de avanço por nó substituído | processo gerado | `summary.json` (10 SUCCEEDED); réplica: oráculo 10/10 em ordem | PASS |
| 8. Resultado real | texto da página lido por padrão ancorado declarado | processo gerado | `observacoes.json` (3 × "100% ( 70 out of 70 fields)") | PASS |

Automação de teste AUXILIAR (não é funcionalidade da Nekkora): a leitura do site no G0 (navegador isolado e leitura
estática do `main.js`), o clique em Submit antes do Start entre as passagens do G4 (via CDP, para recriar o
formulário), a digitação na página por `Input.insertText` e a comparação de posições dos campos.

## 6. Regressões (G7) — contagens reais

| Suíte | Windows | Linux |
|---|---|---|
| Studio (`./mvnw test` / `tools/golden-linux.sh verify`) | **596/596** (HEAD `f9c09c1`) | **596** (595 + 1 pulado por rodar como root), HEAD `f9c09c1` |
| Extensão — Java (`java-core`: núcleo + Native Host) | **293/293** | — |
| Extensão — Node (`node --test`) | **179/179** | — |
| Extensão — ITs Chrome for Testing (10 grupos, HEAD `6307268`) | **275/275** (+ 2 de contrato do PageMap por grupo) | — |
| Framework (`pytest`) | **244/244** | **244/244** (contêiner Playwright 1.63.0) |
| Framework — `ruff check`, `mypy` estrito | OK, OK | — |
| Framework — controle negativo | **20/20 mutações detectadas** | — |
| Homologação real (G2–G5) | **56/56** | — |

## 7. Defeitos encontrados e corrigidos nesta missão

| Fronteira | Defeito | Reprodução | Correção |
|---|---|---|---|
| Recorder / Bridge | descrição > 200 derrubava a etapa no host | IT ao vivo | ext `1a77cad` + teste n9 |
| Recorder | corrida evidência × sincronização | 1/8 → 0/12 | Studio `5a3492a` |
| Seletor / privacidade | nome do link retido → só POSICIONAL | pré-voo `LOCATOR_POSITIONAL` | vocabulário +excel +cloud (usuária) ext `6307268`, Studio `744fc8e` |
| Geração (GUI) | diálogo 0×0 | homologação (Robot) | Studio `be485c1` |
| Geração (código) | ruff F401/I001/E501/TRY004 | `importsSemSobraEOrdenados` falhava | Studio `ec42309` |
| Contrato | `selectors-1.1.0` sem `label` | teste de contrato com a fixture do desafio falhava | framework `1059431` |
| Segurança / seletor (testes) | mutações M15 e M18 sobreviviam | controle negativo | framework `965480b` (2 testes novos) |

## 8. Comandos

```
# G1 (framework, venv do repositório)
python docs/e2e-001/tools/g1_download.py --destino <pasta> --evidencia <json> --transiente <fora-dos-repos>
# G2–G5 (Studio real + Chrome + extensão + Native Host)
java -Dnekkora.studio.decisionActor=TEST_AGENT ... com.nekkora.studio.app.evidence.E2e001Homologation <saída> <repo-ext> <transiente>
# G5 validação / G6 laboratório / G6 real
python docs/e2e-001/tools/g5_validar.py --gerado <pasta> --python-limpo <venv> --execucao <g6/...> --saida <json>
java ... com.nekkora.studio.app.evidence.E2e001Generate <projeto.nekkora> <config.codegen.json> <saída>
python docs/e2e-001/tools/g6_replica.py --framework <repo> --gerado <variante-lab> --python <venv> --evidencias <pasta>
python docs/e2e-001/tools/g6_executar.py --framework <repo> --gerado <pasta> --evidencias <pasta> --rotulo <rótulo> --valores <xlsx>
```

## 9. Pendências e limitações

Ver `docs/e2e-001/known-limitations.md`; as principais: identidade do debug não sobrevive à recriação dos campos
(C5), marcadores do PDD (C4), código final regenerado sem janela (C6), ordem laboratório × site (C7). Arquivo
transitório com o 1º registro (`transiente.json`) apagado ao final da missão.
