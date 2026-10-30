# S2 — Compatibilidade dos pacotes (Studio → Agent → Platform API)

A referência é o contrato efetivamente homologado na PLATFORM-AUTOMATION-LIFECYCLE-001: pacote `lc1`, gerado pelo Studio `da7bb94`. Aqui ele é comparado com o pacote `int1`, gerado pelo candidato (`f879af5`) pela interface real (S5).

## 1. Contrato

| Contrato | Referência (`lc1`, `da7bb94`) | Candidato (`int1`, `f879af5`) | Conferido por |
|---|---|---|---|
| Manifesto | `nekkora-studio/codegen-manifest` 1.5.0 | 1.5.0 | `PackageInspector` da API (`manifestVersion`) |
| Gerador declarado | `nekkora-studio-codegen/1.2.0` | `nekkora-studio-codegen/1.2.0` | API (`declaredGenerator`); proveniência `DECLARED` |
| Framework | `nekkora-rpa[excel]==0.3.0` | `nekkora-rpa[excel]==0.3.0` | API e Agent (o Python escolhido tem a mesma versão) |
| Modo | `list` (lista de entrada → planilha) | `list` | API (`executionMode`) |
| Impressão | `nekkora-agent/package-fingerprint/1` = `f199bda4…a8d1` | `/1` = `8d07bc39…fe0a` | calculada pela API a partir do ZIP e pelo Agent a partir da pasta: iguais (`PLATFORM_RUN_LINKED`) |
| Integridade | SHA-256 do ZIP = `a7e735a2…017ea` | `4188dd70…93e1` | a API recalcula sobre os bytes recebidos; igual ao local |
| Arquivos declarados | 9 (+ `manifest.json`) | 9 (+ `manifest.json`) | API (`filesCount`); os hashes do manifesto conferem byte a byte |

Os valores acima são do pacote homologado. A missão não congela nenhum deles como requisito geral: a API e o Agent conferem pelas próprias regras, e nenhum contrato foi flexibilizado.

## 2. O candidato gera o mesmo código

Os dois pacotes saíram do mesmo roteiro pela interface (`CodegenQuality001Homologation`, 105/105 nos dois). Comparação arquivo a arquivo (`diff -r`):
- **idênticos byte a byte:** `main.py`, `automation/__init__.py`, `automation/pages.py`, `automation/process.py`, `automation/selectors.py`, `config/execution.toml`, `requirements.txt` e `README.md`;
- **`manifest.json` e `selectors.json`:** só mudam os identificadores que cada gravação cria de novo (`automationId`, `stepId`/`studioStepId`) e as revisões derivadas deles (`planRevision`, `configRevision`, `processRevision`, `documentRevision`, `specRevision` e o hash de `selectors.json`).

Por isso a impressão e o SHA-256 do ZIP diferem entre `lc1` e `int1`: o conteúdo de identificação muda a cada gravação, não por diferença no gerador. As cinco correções desta missão não mudaram nenhum byte do código gerado a partir de texto comum, e isso é confirmado também pelos testes do gerador, que continuam inalterados (S3).

## 3. Regras conferidas

- **Entrypoint:** `main.py`, com as opções de CLI `--input`/`--spreadsheet`/`--output` declaradas no manifesto. O Agent só passa essas opções.
- **Caminhos:** todos relativos. O `PackageInspector` recusa travessia, absolutos, symlink e nomes duplicados (inclusive só na caixa), e aceitou o pacote.
- **Hashes:** cada arquivo listado em `manifest.files` confere (`s5-pacote-int1-sha256.txt`).
- **ZIP:** o Studio produz uma **pasta**, não um ZIP. O ZIP enviado à API é montado pelo laboratório de forma determinística: data fixa, ordem lexicográfica e DEFLATE. A determinação é do insumo de teste, não promessa do Studio.
- **`artifact.sha256` ≠ `package-fingerprint/1`:**
  - o primeiro é dos bytes do ZIP e muda com a compactação;
  - o segundo é dos arquivos declarados e não muda;
  - a API devolve os dois separados, e a publicação exige a expectativa de ambos.
- **Campos opcionais:**
  - a coluna "Morte" é `OPTIONAL` com motivo `ABSENT_FOR_SOME_ITEMS`;
  - a planilha real tem a célula vazia para a pessoa viva (S5);
  - a conferência V5 agora exige o vocabulário fechado (S3-5).
- **Procedência:** `DECLARED`. A indicação do gerador não é assinatura, e nenhuma assinatura foi implementada.

## 4. Não alterado

Contrato da Platform API, protocolo do Agent, Framework (a versão 0.4.0 não foi adotada), Chrome Extension e AI Engine (só as correções S3-2 e S3-5, que são do próprio Studio).
