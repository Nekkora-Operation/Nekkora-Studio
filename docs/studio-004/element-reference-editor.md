# STUDIO-004 — Revisão das referências técnicas

O Studio continua **sem analisar a página**: não recalcula localizadores, não altera os IDs técnicos produzidos pelo
motor semântico e não inventa validações. A revisão trabalha sobre o `ElementReference` que a extensão entregou.

## O que aparece

No painel (grupo **Elemento**) e no editor (aba **Elemento**):

- nome acessível, categoria, tag, id/name HTML (só quando seguros), trajetória, contexto, estabilidade e obstáculos;
- os localizadores capturados, um por linha, com a estratégia, a descrição, a expressão técnica e a situação:
  - **✓ Preferencial**, ou "Validado na captura: encontrou exatamente o elemento";
  - **○ Candidato**, ou "Candidato alternativo, não validado: N correspondências na captura";
  - "Não encontrou o elemento na captura";
- os localizadores propostos manualmente, sempre com **Não verificado**.

## Localizador preferencial

- No editor, cada localizador capturado é uma opção. Só os **validados** (`LocatorCandidate.validated()`: encontrou
  exatamente o alvo na captura) podem ser escolhidos; os demais ficam indisponíveis. Também há "Nenhum preferencial".
- `Automation.choosePreferredLocator` / `editStep` recusam `LOCATOR_NOT_VALIDATED`, `LOCATOR_NOT_FOUND` e, numa etapa
  sem elemento, `STEP_HAS_NO_ELEMENT`.
- A escolha gera a referência **efetiva** (`ElementReference.withPreferredLocator`). O `capturedElement()` continua
  sendo o mesmo objeto, com o preferencial original, e as chaves internas do motor não mudam.

## Localizador manual

- Aba Elemento → **Localizadores propostos manualmente**: linguagem (CSS ou XPath), expressão e observação opcional.
- `ManualLocator` é sempre `NOT_VERIFIED`:
  - não tem `matchCount` nem `matchesTarget`, e nenhuma validação histórica é reaproveitada para uma expressão
    digitada;
  - nunca pode ser o preferencial;
  - não entra na lista capturada.
- Validação:
  - expressão obrigatória, até 500 caracteres, sem quebras de linha ou caracteres de controle;
  - observação de até 200 caracteres;
  - nada com aparência de dado sensível (ex.: um e-mail num seletor de atributo).
- A verificação ao vivo de novas expressões **não** pertence a esta missão.

## Evidência

- Domínio: `AutomationEditingTest.localizadores` e `localizadorManual`.
- Aplicação: `EditorStudioTest.localizadores`.
- Homologação real, cenário 5. O elemento "Entrar" gravado tinha 5 candidatos validados; o 2º foi escolhido; o
  capturado continuou intacto (`S004-05-localizadores.png`).
