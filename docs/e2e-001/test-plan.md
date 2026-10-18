# NEKKORA-E2E-001 — Plano de testes

Operador: Claude (Claude Code) como `TEST_AGENT`. Toda decisão registrada pelo agente fica com ator `TEST_AGENT`;
nenhuma é apresentada como aprovação humana.

## 1. Camadas de prova

| Camada | O que prova | Onde roda | Nunca prova |
|---|---|---|---|
| Unitário / contrato | regras do gerador, do leitor tabular, dos contratos e das políticas | JUnit (Studio, extensão), pytest, `node --test` | que o site real funciona |
| Laboratório local | o código GERADO contra a réplica sintética do desafio, conferido pelo ORÁCULO do servidor | réplica `lab/static/desafio` do framework | o placar do site real |
| Chrome real (ITs) | extensão desta branch em Chrome for Testing: captura, política, perfil visual | `integration-tests` da extensão | o Studio |
| Homologação real | Studio real (Swing) + Chrome + extensão + Native Host, operados por Robot/CDP pelo TEST_AGENT | `E2e001Homologation` | o resultado do desafio |
| Execução real | o projeto gerado pelo Studio, sem alteração, no Chrome instalado, contra `https://rpachallenge.com` | `g6_executar.py` | — (é a prova de C8) |

## 2. Gates e roteiro

| Gate | Roteiro | Ferramenta | Critério de aceite |
|---|---|---|---|
| G0 | leitura do site real sem clicar em Start; inventário de lacunas | navegador isolado + leitura estática do `main.js` | diagnóstico com lacunas, nunca PASS por intenção |
| G1 | download REAL do XLSX pelo `nekkora_rpa` (ambiente `benchmark`), leitura tabular | `tools/g1_download.py` | hash, tamanho, abas, cabeçalhos, contagem, tipos; evidência sem valores |
| G2 | Recorder: Preparação (download, Start), Preenchimento por registro (7 campos + Submit), Conclusão (observar) | `E2e001Homologation` | proveniência RECORDER, LABEL validado nos 7 campos, nenhum valor no modelo, salvar/reabrir íntegro |
| G3 | PDD visual: revisão de textos (TEST_AGENT), 2 capturas no perfil BENCHMARK, exportação | idem | PDF sem valores, 2 imagens sanitizadas, metadados controlados |
| G4 | debug guiado em 2 passagens; entre elas o formulário é recriado (nova ordem e novos ids) | idem | nunca Continuar sobre alvo incerto; correção guiada compatível; todas as etapas CONFIRMED (TEST_AGENT); registro da mesma revisão do plano |
| G5 | geração pelo menu "Automação → Gerar código Python…"; 2ª geração; edição manual; desfazer | idem + `g5_validar.py` | determinismo, proteção (`.novo` + `.diff`), sem valores, sem posição; venv limpa compila/importa; contratos; ruff; mypy estrito |
| G6 | variante de laboratório (mesmo gerador, config `lab`) na réplica; depois tentativas reais no site | `E2e001Generate`, `g6_replica.py`, `g6_executar.py` | oráculo: 1 início, 10 submissões em ordem e corretas; site: placar e tempo LIDOS da página |
| G7 | suítes completas Windows/Linux, ITs Chrome, controle negativo, falhas injetadas por fronteira | ver §4 | contagens reais |

## 3. Casos negativos (seção 9 da missão) e onde estão

| Caso | Teste |
|---|---|
| Download ausente, tardio, cancelado, formato/tamanho inválido, outra origem | `test_framework002.py` (`dl.nada`, `dl.falha`, `dl.grande`, `dl.texto`, `dl.externo`, guarda de rede falhando) |
| XLSX com cabeçalho trocado/duplicado, coluna faltante, vazio, número, fórmula | `tests/unit/test_tabular.py` (23) |
| CSV malformado | `tests/unit/test_tabular.py` |
| Campo com rótulo alterado entre rodadas; posição muda; duplicata ambígua | `test_framework002.py` (`v=trocado`, `v=sosia`, rodadas com id novo), contêiner com dois campos |
| Start/Submit com desfecho incerto | `nekkora_rpa` 0.1.0 (M05, M06, M07, M13) + prova de avanço `replaced` (M20) |
| Origem fora da allowlist, perfil errado | `test_config_security.py` (benchmark × lab), M08, M17 |
| Captura de texto sensível ou imagem bruta | ITs `Studio010SafeCaptureIT`, `ChromeSecurity001IT`; vetores `privacy-v2-vectors.json` |
| `planRevision` distinta do registro de debug | homologação (comparação com a revisão recebida pela sessão) |
| `.nekkora` fechado/reaberto; alterações não salvas | homologação G2 (salvar/reabrir), migração de formato ao salvar |
| Código gerado editado à mão | homologação G5 + `PythonGeneratorTest` |
| Falta de wheel / versão do Playwright | `g6_executar.py` constrói o wheel e instala com `constraints/lab.txt` em venv nova |
| Resultado ausente ou ambíguo, padrão não ancorado | `test_padrao_de_texto_invalido_ausente_e_ambiguo`, M19 |

## 4. Regressões (G7)

- Studio: `./mvnw test` no Windows; `tools/golden-linux.sh verify` (contêiner, HEAD commitado).
- Extensão: `mvnw install` de `java-core` (núcleo + Native Host), `node --test` da extensão, ITs Chrome em 10 grupos.
- Framework: `ruff check`, `ruff format --check`, `mypy` estrito, `pytest` Windows e `tools/linux.sh`;
  `tools/controle_negativo.py` (20 mutações).

## 5. Falhas injetadas por fronteira (prova de que os testes detectam)

| Fronteira | Injeção | Detectada por |
|---|---|---|
| Recorder / seletor | M18: rótulo irmão aceito com dois campos no contêiner; M01: ambíguo vira "o primeiro" | `test_framework002 -k rotulo`; `test_resolver` |
| Excel / dados | M14: fórmula aceita | `test_tabular.py` |
| Geração | edição manual de `paginas.py`; localizador posicional como preferido; valor literal | homologação G5 (PROTECTED); pré-voo `LOCATOR_POSITIONAL`, `LITERAL_VALUE_NOT_GENERATED` |
| Segurança | M15: download de outra origem; M17: benchmark com http/loopback/curinga; M08: navegação fora da origem | `test_framework002 -k download`; `test_config_security`; `test_actions` |
| Runtime | M20: prova de avanço sempre verdadeira; M05/M06: desfecho desconhecido repetido | `test_framework002`; `test_retry`, `test_engine` |
| Resultado | M19: padrão não ancorado aceito | `test_framework002 -k padrao` |
