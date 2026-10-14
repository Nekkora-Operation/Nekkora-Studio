# Contribuindo — Nekkora Swing UI

## Fluxo de trabalho

Trabalho local com branches; `main` é a principal. Antes de integrar:

```bash
./mvnw verify
```

Isso roda: contraste WCAG dos temas, criação/pintura de todos os componentes nos 4 temas, eventos e
teclado, campos (máscaras/validação), ícones, registro de ações, **golden screenshots** e o **lint de
design** das telas.

## Regra de contribuição

Antes de criar um componente, responda:

1. **É reutilizável fora desta tela?** Se não, ele pertence à tela (ou ao `nekkora-studio-ui`), não à lib.
2. **Algo existente já resolve 80%?** Então estenda com uma variante/propriedade semântica.
3. **Nomeie pela intenção, nunca pela aparência ou pelo uso.** Nunca `PinkButton`, `SaveButton`,
   `BigRedLabel` — sim `NekkoraButton(Variant.DANGER)`, `Status.FAILED`, `TypeStyle.H2`.
4. **Sem domínio:** nada de Automation, Process, Evidence, Worker, PDD, API, auth na lib.

## Regras para telas (verificadas pelo `DesignLintTest`)

Telas **não** usam `setBackground`, `setForeground`, `setFont` (exceto `NekkoraFonts.font(token)` em
pintura), `setBorder`, `BorderFactory`, `new Color`, `new Font`, cores hex, `Box.create*Strut` ou
`setBounds`. Use `NekkoraPanel.setSurface/setPadding/setOutline`, `NekkoraStack` com `Spacing`, os
componentes de tipografia e os tokens. Copie o `DesignLintTest` para o módulo do Studio.

## Dentro da lib

- Cores só via `Tokens.color(ColorToken.X)` resolvido na pintura; fontes via `NekkoraFonts`; medidas via `Scale.px`.
- Estilo reaplicado em `updateUI()` (troca de tema ao vivo). Cuidado: `updateUI()` roda no construtor do
  Swing antes dos campos — use a guarda `initialized`.
- Componentes pintados estendem `NekkoraComponent` (acessibilidade) e usam `InteractionTracker`
  (hover/pressed/foco) e `Paint` (anel de foco, sombras, texto).
- Novo componente ⇒ entrada no `ComponentCatalog` (teste), exemplo no Showcase com seus estados.
- Mudança visual intencional ⇒ `./mvnw test -Dnekkora.golden.update=true` e revise as imagens em
  `nekkora-swing-ui/src/test/resources/golden/<sistema>/`. (Na primeira execução em Windows/macOS as
  imagens de referência daquele sistema são gravadas.)
- Golden screenshots dependem da versão do JDK, não só do sistema: o golden Linux versionado corresponde ao
  Temurin 21.0.10 ou anterior. A partir do 21.0.11, a métrica dos títulos em Sora mudou e o teste falha no Linux
  sem nenhuma mudança de código. O golden Windows corresponde ao Microsoft OpenJDK 21.0.12. Diferença inesperada ⇒
  compare primeiro o JDK com o do baseline, sem regenerar (ver `reports/studio-001-golden-baselines-report.md`).
- Ícones: edite `tools/extra-icons.txt` (grade 24×24, `D` duo, `S` traço, `F` sólido) e rode
  `node tools/extract-icons.mjs <icons.tsx> tools/extra-icons.txt > .../nekkora-icons.txt` +
  `node tools/gen-icon-enum.mjs ... > .../IconName.java`.

## Definition of Done (V1)

- Abrir o Showcase, alternar entre os 4 temas e ver todos os componentes consistentes, sem cores,
  fontes ou bordas "hardcoded".
- Abrir a tela Studio fictícia (Sidebar + Navegador em árvore + Abas + Área de trabalho + Inspector +
  Toolbar + Status + Diálogo) montada **só** com componentes da lib.
- `./mvnw verify` verde.
