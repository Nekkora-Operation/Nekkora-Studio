# STUDIO-010 — Política visual `nekkora-visual/1`

Lista de **permissão**. A imagem sanitizada começa inteiramente opaca e só recebe pixels reais onde a política prova
haver um rótulo de interface do vocabulário controlado. Falha de qualquer heurística produz **menos** imagem, nunca mais.

## Regiões

| Região | O que vai para a imagem | Condições (todas) |
|---|---|---|
| Portador (`CARRIER`) | pixels reais **só** do retângulo do elemento | `isCarrier` da `nekkora-privacy/2` (button, label, legend, summary, `a[href]`, papéis de controle); documento principal; visível e dentro do viewport; texto **inteiro** passa por `interfaceText`; sem `img`/`svg`/`canvas`/`video`/`iframe`/`object` dentro; sem pseudo-elemento com conteúdo; sem imagem de fundo; é o elemento do topo em 5 pontos de amostra; não sobrepõe nenhum campo; altura ≤ 64 px CSS; largura ≤ 60% do viewport |
| Campo (`FIELD`) | moldura **sintética** (#FFFFFF, borda #8A9099) | campo editável visível do documento principal. Nenhum pixel do campo é copiado (valor, autofill e placeholder nunca aparecem) |
| Todo o resto | opaco #D5D8DD | títulos, parágrafos, tabelas, mensagens, imagens, SVG, canvas, iframes, shadow, sobreposições |

Limites globais: ≤ 64 regiões; área somada dos portadores ≤ 20% do viewport; PNG ≤ 150 000 bytes (o quadro do Bridge é 256 KiB).

Violação de limite → captura **recusada** com código (`CARRIER_TOO_LARGE`, `CARRIER_OVERLAPS_FIELD`, `TOO_MANY_REGIONS`,
`REGION_OUT_OF_VIEWPORT`, `REGION_KIND_UNKNOWN`, `SANITIZED_IMAGE_TOO_LARGE`), nunca "corrigida" em silêncio.

## Verificação de pixels (quatro vezes)

Regra única: fora dos retângulos `CARRIER`, todo pixel é exatamente uma das três cores sintéticas (opaco, preenchimento
ou borda do campo) e a geometria das molduras confere.

| # | Onde | Quando | Falha |
|---|---|---|---|
| 1 | service worker (`visual-sanitizer.violations`) | logo depois de compor | `SANITIZATION_CHECK_FAILED`, nada sai |
| 2 | host (`PddVisualPrivacy`) | antes de repassar o evento ao Studio | evento descartado |
| 3 | Studio (`VisualVerifier`) | ao receber | imagem recusada, rascunho inalterado |
| 4 | exportação (`PddExporter`) | ao reler do disco (sha-256 + pixels) | exportação recusada |

## Ciclo de vida da imagem bruta

`captureVisibleTab` → bitmap em memória do service worker → composição no `OffscreenCanvas` → `close()` e referências
anuladas no `finally` (também em erro ou cancelamento). A imagem bruta nunca vai a `storage`, log, Bridge, disco,
área de transferência, relatório ou evidência. Limite declarado: ela existe por instantes na memória do Chrome, como a
própria página.

## Captura autorizada

- Só origem `LAB` (laboratório local). Fora dela: `VISUAL_CAPTURE_NOT_ALLOWED`; a aba nem é oferecida no Studio.
- Armação única do Studio (validade 5–120 s, época da página, aba) **e** gesto real no Chrome (`Alt+Shift+K`, comando
  `nk-pdd-capturar`, permissão `activeTab`). Gesto sem armação, em outra aba, depois de vencer ou com a página mudada:
  nada é capturado (`NOT_ARMED`, `TAB_MISMATCH`, `ARM_EXPIRED`, `PAGE_CHANGED_DURING_CAPTURE`).
- Ficha do layout antes e depois da captura; diferença → descarte. Dimensões ≠ viewport × DPR → `GEOMETRY_MISMATCH`.

## Marcadores

Caixas das etapas vêm do resolvedor somente leitura da STUDIO-009 (identidade registrada, nunca localizador),
normalizadas em [0,1]. Alvo não comprovado (ambíguo, não encontrado, em quadro, fora do viewport, etapa sem alvo) →
anotação `OMITTED` com motivo e aviso "marcador indisponível". O Studio desenha os marcadores **fora** dos controles
(esquerda, acima, direita, abaixo), evitando regiões e outros marcadores; nada é desenhado na página nem no PNG.

## Evolução

Mudança de regra = nova versão (`nekkora-visual/2`), nunca ajuste silencioso. O PDF identifica a política no manifesto.
