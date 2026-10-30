# NEKKORA-STUDIO-INTEGRATION-CONSOLIDATION-001: relatório final

Data: 29 e 30/10/2026. Repositório: `Nekkora-Operation/Nekkora-Studio`. Branch do PR: `integracao/studio-consolidacao-001` → `main`.

| Fase | Documento |
|---|---|
| S0 Preservação e inventário | [s0-inventario.md](s0-inventario.md) |
| S1 Revisão técnica independente (e S3, correções) | [s1-revisao.md](s1-revisao.md) |
| S2 Compatibilidade dos pacotes | [s2-compatibilidade.md](s2-compatibilidade.md) |
| S4 Regressão completa | [s4-regressao.md](s4-regressao.md) |
| S5 Homologação funcional real | [s5-homologacao.md](s5-homologacao.md) |
| S6 Consolidação no GitHub | [s6-integracao.md](s6-integracao.md) |

## Parecer: MERGE_READY, com as ressalvas R1–R5

Todos os critérios de aceite estão comprovados, com as evidências de cada fase:
- inventário completo e dados locais preservados;
- nenhum defeito bloqueante;
- correções com prova vermelho/verde;
- Windows aprovado e Linux classificado;
- interface real homologada em 100%, 150% e 200%;
- pacote genuíno aceito pelo Agent e pela API;
- resultado, identidade e rastreabilidade preservados;
- PR para a `main`;
- hashes e commits documentados.

O merge é decisão exclusiva da titular.

**Ressalvas para decisão da titular:**
- **R1 (R-15):** em 200% numa tela de 1080p, a aba Pendências fica apertada: a lista some e a primeira linha da pergunta fica sob as abas. O rodapé foi corrigido em todas as escalas; na `main`, "Fechar" saía da janela já em 100%. Melhorar a aba exige redesenho.
- **R2:** ressalvas técnicas registradas para missões próprias:
  - R-06: recuperação do registro de rastreio;
  - R-07: proveniência com as duas fontes;
  - R-08: motivo longo, não alcançável pela interface;
  - R-09: `OutputWriter` com junction e aposentadoria repetida;
  - R-10: leitura na EDT em "Revisar código";
  - R-11: harnesses antigos de homologação desatualizados.
- **R3:** a `main` pública **já contém** caminhos pessoais em evidências antigas, de missões anteriores a esta (S1 §1). Esta branch não introduz nenhum e não altera arquivos da `main`.
- **R4:** a proveniência do gerador é `DECLARED`, sem assinatura. O Studio entrega uma pasta, e o ZIP determinístico é montado pelo laboratório.
- **R5:** o executável do Agent usado na jornada foi gerado do commit `7986e84`, com `src` idêntico ao da `main` `ce30397`.

## A. Inventário

- Os 22 commits locais formam uma **cadeia linear** sobre a `main` `1cbf669`: CQ (15) → SOC (+5) → LEAN (+2), até `da7bb94`, que é o HEAD.
- O working tree estava limpo, sem stash nem submódulo.
- Fora da integração ficam:
  - `archive/pdd01-v3-print-real-local`, um arquivo local "não publicar";
  - `feat/e2e-002-wikipedia-excel`, já superada pelo PR #21.
- **Backup** fora do repositório: `git bundle --all` com 52 referências (SHA-256 `d8719228…0867`), mais `refs.txt` e os 9 logs ignorados. Foi verificado com `bundle verify`, `sha256sum -c` e um clone a partir do bundle. O procedimento de recuperação está em S0 §5.
- O `.env` local não foi copiado nem tocado.

## B. Revisão

Três revisões independentes: gerador, validação e especificação, interface e integração.
- **Nenhum achado BLOQUEANTE.**
- 4 achados de correção necessária, mais 1 ressalva que também foi corrigida.
- 10 ressalvas e 3 melhorias futuras, todas registradas.
- Nenhum módulo do Bridge, Recorder, Debug guiado, PDD visual, Validation ou Semantics foi tocado pelos 22 commits.
- Higiene: os 11 logs e as 20 capturas com dado pessoal foram saneados na reprodução, por decisão da titular.

## C. Correções

| # | Defeito | Commit (integração) | Vermelho → verde |
|---|---|---|---|
| S3-1 | "Fechar" cortado no rodapé da janela Desenvolver | `3738b6c` | `DevelopmentPanelUiTest.rodapeNaoCortaFechar` |
| S3-2 | aviso de legibilidade VQ virava "código com problemas" | `af2962b` | `CodeValidatedTest.avisoNaoBarra` |
| S3-3 | texto do usuário com `\` ou `"""` quebrava o Python gerado | `bbcda0e` | `DocstringEscapeTest` (`ast.parse` real) |
| S3-4 | localizadores posicionais `(//x)[n]` e `:first-of-type` passavam | `f879af5` | `CodegenQualityRulesTest.localizadores` |
| S3-5 | V5 em Python mais frouxa que o gerador | `4009ba7` | `OptionalColumnsTest#contratoV5RecusaManifestoForaDoVocabulario` |

## D. Regressão

| | Testes | Falhas | Erros | Ignorados | Commit |
|---|---|---|---|---|---|
| Windows, linha de base | 781 | 0 | 0 | 0 | `da7bb94` |
| **Windows, candidato** | **788** | **0** | **0** | **0** | `f879af5` |
| **Linux** (`golden-linux.sh verify`) | **788** | **0** | **0** | 29 | `f879af5` |

Os 29 pulados no Linux têm motivo de ambiente: 28 por falta de Python na imagem e 1 por rodar como root.

- **Escalas, candidato:** 100% e 150% 14/14; 200% 10/14, com 2 falsos positivos ou itens iguais à `main` e a R1.
- **Escalas, `main`:** 1, 1 e 4 falhas.
- **Geração:** coberta pelos testes do gerador e pela homologação 105/105.

## E. Compatibilidade

| | Valor |
|---|---|
| Manifesto | `nekkora-studio/codegen-manifest` 1.5.0 |
| Gerador | `nekkora-studio-codegen/1.2.0` (declarado) |
| Framework | `nekkora-rpa[excel]==0.3.0` |
| Impressão `package-fingerprint/1` | `8d07bc394f0442a9e9dd141f13de465e655272bc48008e2b8742896eb455fe0a` (igual na API e no Agent) |
| SHA-256 do ZIP | `4188dd700000968211c5ab0796bca5286a2d3a2704e192418f096abaaa9e93e1` (igual no local e no servidor) |

O código gerado é idêntico byte a byte ao do pacote homologado `lc1`; só mudam os UUIDs de cada gravação. Nenhum contrato da API, do Agent ou do Framework foi alterado.

## F. Homologação real

- **Studio candidato pela interface:** `CodegenQuality001Homologation` com **105/105**.
- **Reabertura do projeto:** 5/5. O pacote gerado de novo a partir do projeto reaberto é idêntico ao da interface.
- **Jornada Studio → Agent → API** (laboratório `agentlab-005`, API `main` `e04a57c`, ferramentas do Agent da `main` `ce30397`):
  - publicação por operação autenticada, **sem fixture SQL de publicação**;
  - **9/9**: SUCCEEDED, com 293 eventos guardados, 293 assentados e 0 recusas, histórico COMPLETE e OUTCOME_UNKNOWN sem relançamento;
  - planilha com 5 linhas, e a pessoa viva com "Morte" vazia.
- O laboratório foi parado com os volumes preservados.

## G. GitHub

- **Branch:** `integracao/studio-consolidacao-001` → `main` `1cbf669`.
- **Commits:** 27 reaplicados saneados (código em `f879af5`), mais o commit desta documentação.
- **Árvores:** S6 §1.
- **Checks:** não há CI no repositório.
- **Merge:** **não executado**; decisão da titular.
- **Branches locais preservadas:** `feat/studio-lean-001` `da7bb94`, `fix/studio-integration-001` `b83f04d` e as demais.

## H. Parecer

**MERGE_READY**, com as ressalvas R1–R5 acima apresentadas para decisão explícita da titular.
