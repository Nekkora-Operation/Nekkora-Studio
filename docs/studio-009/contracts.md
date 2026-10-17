# STUDIO-009 — Contratos

## 1. `nekkora-studio/guided-debug-plan/1.0.0` (G1)

O plano intermediário do debug guiado é uma projeção determinística do modelo (D-S009-01). Ele é produzido por
`nekkora-studio-guided` (`GuidedPlan.of(automação, relatório)`). Schema e fixture ficam em
`nekkora-studio-guided/src/test/resources/contracts/guided-debug-plan/1.0.0/`.

| Campo | Conteúdo |
|---|---|
| `automationId`, `modelVersion`, `automationRevision` | de onde o plano foi projetado |
| `planRevision` | `sha256:` do JSON canônico sem a própria revisão e sem `automationRevision`. Muda quando o **conteúdo** do plano muda; não muda com o nome de uma etapa (que o plano não contém). É a revisão que o caso Python congela |
| `staticValidation` | regras, estado e `"meaning": "STATIC_ONLY"`. `NO_STATIC_BLOCKERS` nunca é sucesso de execução |
| `variables[]` | como o contrato semântico 1.0.0 publica: nome, tipos, situação, sensibilidade, origem e produtora. Sem valor nem finalidade |
| `steps[].intent` | ação, requisito de alvo, **origem** do valor (`NONE`, `LITERAL`, `VARIABLE`, `CREDENTIAL`), variável declarada, tecla só do vocabulário fechado (`Enter`, `Tab`, `Escape`) e se há opção declarada. O literal e o apelido de credencial ficam retidos |
| `steps[].target` | a fonte da identidade é `TARGET_ID` (identidade lógica registrada pela extensão) ou `DESCRIPTOR` (só a referência compacta persistida). Inclui o estado do alvo efetivo, o tipo, a tag, o nome acessível apresentável, sensível, até 5 localizadores **como capturados** (validados ou não), o preferencial, os manuais (`NOT_VERIFIED`), a trajetória e as âncoras |
| `steps[].context` | aplicação, título e endereço **já sanitizados** pelo produtor: modelo de URL, nunca a URL completa |
| `steps[].applicability` | `INSPECT_TARGET`, `NO_TARGET_REQUIRED` ou `TARGET_MISSING`. No último caso, a etapa só admite Corrigir; localizador manual não promove identidade |
| `steps[].criteria` | o que o debug verifica no navegador: contexto, identidade, unicidade, visibilidade, habilitado, editável, tipo × ação, alcance de frame e de shadow |
| `steps[].uncertainties` | o que se sabe que **não** está comprovado antes do debug |
| `steps[].staticFindings`, `semanticUncertainties` | códigos da validação 1.1.0 e do contrato semântico para a etapa |
| `exclusions` | o que o plano nunca contém |

Garantias testadas por `GuidedPlanTest` e `GuidedPlanApiTest`:
- schema igual ao código;
- fixture congelada;
- mesmos bytes para o mesmo estado;
- ausência de literais, finalidades, nomes, apelidos e chaves de correlação, no JSON **e** em qualquer caminho da API Java;
- casos negativos: sem alvo, manual só ao lado de referência, descritor, frame/shadow, tipo incomum, sensível, tecla fora do vocabulário, relatório de outra automação.
