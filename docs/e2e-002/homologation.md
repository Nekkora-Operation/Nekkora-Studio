# NEKKORA-E2E-002 — Homologação real

Data: 2026-10-20. Máquina: Windows 11 Pro 10.0.26200. Operador: Claude como `TEST_AGENT`
(`-Dnekkora.studio.decisionActor=TEST_AGENT`), mouse e teclado do sistema (`java.awt.Robot`).

## 1. Montagem

- **Studio real** (`StudioApplication`) com dados isolados (`NEKKORA_BRIDGE_DIR`, `NEKKORA_DATA_DIR`,
  `NEKKORA_STUDIO_DATA_DIR`), janela 1180×1000.
- **Chrome for Testing 155** com a extensão desta branch (cópia de teste que só declara `https://pt.wikipedia.org/*`),
  janela 1320×1000 (abaixo de ~1120 px a página recolhe a busca na lupa).
- **Native Host** pelo script oficial (`install-host.ps1`); conexão autenticada Studio ⇄ host ⇄ extensão.
- **IA real**: Anthropic, chave lida do `.env` local e passada SÓ ao processo do Studio (o lançador mascara a saída e
  confere que o log não a contém).
- **Python**: o do framework (3.12.10, `nekkora_rpa` 0.3.0 editável, ruff, mypy) para V4/V5 e o teste autorizado.
- Harness: `nekkora-studio-app/src/test/java/com/nekkora/studio/app/evidence/E2e002Homologation.java`.

Comando (na raiz do Studio, com o classpath do app):

```
java -Dnekkora.studio.decisionActor=TEST_AGENT -Dnekkora.bridge.dir=<hb> -cp <classpath> \
  com.nekkora.studio.app.evidence.E2e002Homologation <saída> <repo-da-extensão>
```

## 2. Rodada final (r14): 69 verificações, 0 falhas · conferidor independente 24/24

Commits: framework `2f36a0a`, extensão `9e4687e`, Studio `0293b50`. Log: `evidence/homologacao-log.txt`.

| Grupo | Verificações | Observado |
|---|---|---|
| Conexão | 1 | Studio ⇄ host ⇄ extensão autenticada |
| G1 sessões e projeto | 2 | Inicialização, Pesquisa, Extração, Transferência de dados (Excel), Encerramento; projeto salvo pelo Studio (`.nekkora`) |
| C02 busca | 4 | gravada COM interação: Preencher + Pressionar Enter (sem clique inventado); valor fora do modelo; campo recriado pela página localizado pela região (`header input[name="search"]`, único, sem posição); Enter observado como envio |
| C03 leituras | 6 | "Selecionar dado": título + 6 células (Nome completo, Nascimento, Morte, Nacionalidade, Ocupação; Campo(s) como fonte alternativa); nada executado (o link da data não navegou); título por ID, células por `ROW_CONTEXT` com o rótulo da linha; nenhum texto de artigo no modelo |
| C04 endereço | 2 | etapa "Ler o endereço da página" (editor) CONFERIDA com a variável de entrada `endereco_do_artigo` |
| G2 debug | 29 | 4 artigos + página principal: identidade comprovada no elemento certo em cada alvo presente; alvo AUSENTE (Nome completo no Einstein, Ocupação na Curie, Campo(s) nos outros) nunca confirmado num parecido (AMBIGUOUS); busca confirmada na página principal |
| PDD | 1 | textos revisados pelo TEST_AGENT, sem pendência que bloqueie (PDD_READY) |
| G4 IA + código | 8 | botão "Solicitar ajuda (IA opcional)", prévia sem texto de artigo e sem chave, resposta verificada (RESPONSE_VERIFIED, modelo informado); propostas aplicadas só quando coincidem com as decisões; CODE_VALIDATED (compilação, importação, ruff, ruff format, mypy estrito, testes de contrato); `XlsxResultSink` com as 6 colunas na ordem; só a origem autorizada |
| G5 PDF | 4 | apêndice "Dados do processo": tudo ou nada, 6 colunas na ordem, fontes alternativas declaradas; sem texto de artigo |
| G3 PDD desatualizado | 2 | etapa acrescentada pelo editor → Desenvolver recusa (`PDD_OUTDATED`, nenhum código); Ctrl+Z → volta a valer |
| G6 / C11 execução | 10 | código de novo CODE_VALIDATED; lista pedida com as colunas das variáveis de entrada (`termo_de_busca`, `endereco_do_artigo`); confirmação com site, destino exato, lista e "não é produção"; progresso na janela; **Cancelar** → CANCELLED e planilha NÃO gravada; execução completa → COMPLETED, 4/4 SUCCEEDED, planilha gravada e reaberta (6 colunas, 4 linhas); V6 ✓; V3 "Lista → planilha" |

**Conferidor independente** (`evidence/oraculo-veredito-li.json`): um Chromium do Playwright, sem `nekkora_rpa`,
abre as 4 URLs, lê o título e as linhas da ficha (cada `li` visível como item) e compara com a planilha reaberta pelo
openpyxl, com parser de datas próprio: 24/24 células, cabeçalhos exatos, 4 linhas, 0 fórmulas. A mesma conferência
com os dados lidos pelo CDP no Chrome visível também deu 24/24.

**Repetibilidade**: três rodadas reais completas com 100% (r10 63/63, r13 69/69, r14 69/69), cada uma com o conferidor
independente 24/24.

## 2b. Regressão (C12)

Suítes automatizadas sem falhas: Studio 765 testes (Windows) e 703 + 9 pulados (Linux, contêiner com JDK fixado);
framework 354 (Windows) e 353 + 1 pulado (Linux), controle negativo 30/30; extensão: núcleo 223, Native Host 98,
Node 221 e **ITs da suíte padrão: 40/40 classes, 305 testes aprovados, 0 falhas**. Os ITs foram concluídos em grupos
sequenciais de até 3 classes, com memória amostrada (a rodada completa anterior foi interrompida pelo sistema por memória
baixa depois de 16 classes / 129 testes, todos aprovados). Matriz classe a classe:
`Nekkora-Extension-Chrome/docs/e2e-002/c12-matriz.md`.

Defeito pré-existente encontrado (falhava também na base `main` `fd10e26`): `Studio013SitesIT` S013-06/S013-09
dependiam de a captura levar mais de 1,2 s; o teste passou a ocupar a página ANTES do gesto (extensão `7beeb25`),
19/19 sozinho e 35/35 com o grupo de permissões.

## 3. Rodadas anteriores — defeitos REAIS encontrados e corrigidos

| Rodada | Falha | Causa | Correção |
|---|---|---|---|
| r1 | Ocupação gravada com alvo POSICIONAL | clique num `li` dentro da célula propunha o item | extensão: célula com cabeçalho de linha (`8c4aa10`) |
| r1 | debug desalinhado | o debug pula etapas sem alvo; o roteiro indexava todas | roteiro |
| r3 | busca IDENTITY_MISMATCH | a página troca searchbox → combobox ao receber foco | extensão: `input role=combobox` = text-entry (`8c4aa10`) |
| r3, r8 | propostas da IA perdidas | reanálise interna limpava as propostas | Studio: propostas só saem quando obsoletas (`1c233f9`) |
| r4–r5 | busca AMBIGUOUS | cópia oculta do cabeçalho fixo indistinguível na gravação | extensão: região `landmark:<tag>` + desempate pelo localizador contextual gravado (`8c4aa10`) |
| r6 | código gerado em execução única | a pergunta opcional "lista → planilha" sumia depois da geração | Studio: avisos opcionais seguem visíveis (`1c233f9`) |
| r7 | Robot clicou no Chrome | botões da pendência de coluna fora da janela | Studio: opções em linhas de 3 (`1c233f9`); roteiro recusa clique fora da janela |
| r7 | 0 propostas (`SCHEMA_INVALID`) | resposta da IA fora do esquema (recusada inteira, nada aplicado) | comportamento correto; reportado |
| r8 | `PARTIAL_RESPONSE` | teto de 2 048 tokens de saída | Studio: 4 096 (`1c233f9`) |
| r8 | variável `entrada_etapa_2_1` | campo de busca sem rótulo seguro | Studio: `termo_de_busca` (`1c233f9`) |
| r9 | 1 item com falha técnica | componente recriado durante a verificação de identidade → MISMATCH não repetível | framework: `TARGET_DETACHED` transitório (`e248c8f`) |
| r9 | endereço lido antes da página | etapa criada no editor sem tela própria | Studio: herda a tela seguinte (`7b9130a`) |
| r9 | V3 "Execução única" com lista | modelo do plano | Studio: modelo `LIST` (`b884793`) |
| r10 | conferidor 20/24 | listas exibidas em linha: `innerText` concatena itens | conferidor lê cada `li` (a planilha estava certa) |
| r11 | renomear etapa não desatualiza o PDD | o PDD acompanha a ESTRUTURA do processo | roteiro passa a acrescentar uma etapa |
| r12 | Cancelar não clicado | o Chrome da execução abriu à frente; a guarda do roteiro recusou clicar | roteiro traz o Studio à frente (ALT + toFront) antes de clicar |
| pós-r13 | valores no estado da planilha após o commit | o estado guardava as linhas para retomada | framework: estado 1.1.0 sem valores (`2f36a0a`); proveniência 1.1.0 aponta a linha (`0293b50`) |

## 4. Prova ANTES (G0)

`evidence/antes/homologacao-log-antes.txt` (base: Studio `03073ae`, extensão `fd10e26`): clicar no título e em células
gravava 0 etapas (NON_INTERACTIVE); "Somente capturar" só gerava "Clicar no link" posicional; a busca só tinha
localizador POSICIONAL único.
