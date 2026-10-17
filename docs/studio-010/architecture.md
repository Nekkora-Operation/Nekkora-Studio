# STUDIO-010 — Arquitetura

```
Automation ─► ProcessProjection (somente leitura, processRevision)
                 │
                 ▼
            DraftBuilder ─► DocumentDraft (nekkora-studio/pdd-visual/1.0.0)
                 ▲              │  seções = sessões; blocos = etapas consecutivas da mesma tela
   DraftEdits ───┘              │  títulos/instruções (DocText: DERIVED / NEEDS_REVIEW / REVIEWED)
 (dividir, juntar, revisar,     │  SafeCapture por sha-256 (só imagem JÁ sanitizada)
  associar/remover imagem,      ▼
  atualizar)               PddStore  <dataDir>/pdd/<automationId>/rascunho.json + capturas/<sha>.png
                                │
                                ▼
                 PdfRenderer (PDFBox 3.0.8 + Inter) ─► prévia (PDF rasterizado) / PddExporter (vNNN + manifesto)

Captura:  PddPresenter ─arm─► Bridge ─► host (PddVisualPrivacy, 3ª barreira) ─► extensão (studio-pdd.js)
          ◄─studio.pdd.captured── (PNG sanitizado + regiões + anotações) ◄── gesto Alt+Shift+K no Chrome
          VisualVerifier (4ª barreira) ─► PddStore ─► DraftEdits.attachCapture
```

## Peças

| Peça | Módulo | Responsabilidade |
|---|---|---|
| `ProcessProjection` | `nekkora-studio-pdd` | fatos seguros de cada etapa (ação, tipo e nome **seguro** do alvo, tela opaca, troca de tela observada, origem gravada/declarada) e `processRevision` (D-S010-03). Nunca muta a automação |
| `DraftBuilder` | pdd | agrupamento conservador (D-S010-04) e textos determinísticos (`Instructions`, D-S010-05) |
| `DraftEdits` | pdd | operações puras sobre o rascunho; recusas com código (`JOIN_CROSSES_SCREEN`, `BLOCKS_NOT_ADJACENT`, `CAPTURE_DOES_NOT_MATCH_BLOCK`…). `refresh` preserva revisões e imagens dos blocos que não mudaram |
| `PddText` | pdd | política de texto: `safeLabel` (nome de interface pela `nekkora-privacy/2`) e `problems` (padrões recusados em texto revisado) |
| `Pendings` | pdd | pendências bloqueantes (texto a revisar, texto recusado, automação mudou) × avisos (imagem ausente, marcador indisponível) |
| `DraftJson` | pdd | escrita canônica e leitura estrita (campo desconhecido ou versão diferente = recusa) |
| `VisualVerifier` | pdd | 4ª barreira: decodifica o PNG, confere tamanho, política, sha-256, regiões e **cada pixel** fora dos portadores |
| `PddStore` | pdd | armazenamento auxiliar; nomes só técnicos; escrita atômica |
| `PdfRenderer` / `PddExporter` | pdd | A4, texto à esquerda e imagem à direita, marcadores vetoriais; versões sem sobrescrever, temporário verificado + movimento atômico; manifesto sem textos |
| `PddCaptureClient` | `nekkora-studio-recorder` | `studio.pdd.capture.arm`/`cancel`, espera de `studio.pdd.captured`, `LINK_LOST` |
| `PddPresenter` / `PddPanel` / `SwingPddHost` | app / ui | janela não modal "PDD visual — preparar documentação" (menu Automação → Gerar documentação → PDD visual…); desfazer/refazer próprios; prévia = o PDF real rasterizado |
| `studio-pdd.js` + `visual-sanitizer.js` | extensão | máquina de estados da captura e sanitização na fronteira (ver [visual-policy.md](visual-policy.md)) |
| `PddVisualPrivacy` | host Java | 3ª barreira: mesma verificação de pixels antes de o evento seguir ao Studio |

## Fluxo de dados (o que atravessa cada fronteira)

| Fronteira | Atravessa | Nunca atravessa |
|---|---|---|
| Studio → extensão (`arm`) | referência opaca do bloco, etapas (`targetId` + descritor recuperável, ação), aba (`t-…`), validade | valores, variáveis, credenciais, nomes livres |
| página → service worker | retângulos de portadores aprovados e de campos, caixas das etapas (resolvedor da 009), ficha do layout | texto da página, valores, URL |
| extensão → host → Studio | PNG **sanitizado**, dimensões, DPR, época, política, sha-256, regiões (tipo + retângulo), anotações (normalizadas, ou omitidas com motivo), limitações | imagem bruta, texto, seletores |
| Studio → disco | `rascunho.json` (textos revisados/derivados, ids técnicos de etapa), `capturas/<sha>.png` | imagem bruta, valores |
| Studio → PDF | títulos/instruções exportáveis, imagens re-verificadas, marcadores | ids técnicos, nomes de variáveis, seletores, chaves de correlação, logs |

## Estado e invariantes

- A automação **não muda** com o PDD: revisão (`Automation.revision`) idêntica antes e depois (G9 P5).
- O rascunho guarda `processRevision`; se a automação mudar, a janela avisa e bloqueia a exportação até "Atualizar com a automação".
- O manifesto da última versão guarda `processRevision`; se for diferente da atual, "PDF desatualizado".
- Imagem só entra no rascunho depois da 4ª barreira; só vai ao PDF depois de relida do disco e re-verificada (sha-256 + pixels).
