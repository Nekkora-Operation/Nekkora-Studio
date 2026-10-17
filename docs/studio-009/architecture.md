# STUDIO-009 — Arquitetura do debug guiado

Prova vertical: **mapear → interpretar → destacar e explicar → Continuar/Corrigir → reusar → sondar → só então gerar
Python/Playwright**, num laboratório local com dados fictícios. A auditoria de reutilização está em
[g0-audit.md](g0-audit.md); as decisões, em [decisions.md](decisions.md); os contratos, em [contracts.md](contracts.md).

## 1. Quem decide o quê

| Peça | Responsabilidade | Não faz |
|---|---|---|
| Domínio (`nekkora-studio-domain`) | `TargetDecision` (CONFIRMED/REJECTED, ator, base em códigos, expectativa), `replaceTarget` atômico (captura e procedência intactas), `restoreOriginal(TARGET)`, desfazer/refazer | não fala com o navegador |
| Plano (`nekkora-studio-guided`) | `GuidedPlan` (projeção determinística do modelo, `planRevision`), `ReusePolicy` (reuso só com identidade comprovada agora, mesma fonte, mesma expectativa, estado adequado), `GuidedDebugRecord` | não decide identidade |
| Persistência | `.nekkora` 1.2.0 (decisão e substituto por etapa), menor versão capaz, migração com cópia | não altera 1.0.0/1.1.0 |
| Bridge/Recorder (`GuidedDebugClient`) | comandos `studio.debug.*` tipados e correlacionados (`requestId`, `operationId`), concessão renovada, 4ª barreira de privacidade, canal perdido = sessão encerrada | sem relay genérico nem JavaScript arbitrário |
| UI/App (`GuidedDebugPanel/Window`, `GuidedDebugPresenter`) | janela flutuante que não rouba o foco nem cobre o alvo; Continuar/Corrigir/Selecionar/Comparar/Aplicar; ator sempre visível; sonda só em aba LAB | não afirma sucesso de negócio |
| Extensão (`studio-debug.js`, content, host `DebugCommands`/`DebugPrivacy`) | **única autoridade de identidade web**: resolução somente leitura por `targetId` ou descritor, verificações situadas, destaque rosa sem etiqueta, seleção pontual interceptada, época da página, sonda LAB por chave de catálogo | não guarda valores; não executa nada fora da sonda LAB |
| Laboratório (`test-lab/pages/studio009/consulta.html`, `Studio009` no servidor) | 17 variantes da página e o **oráculo** (`/api/studio009/consulta|estado|reset`) | — |
| Claude (operador TEST_AGENT) | opera a homologação, decide pela janela (nunca pelo gabarito), gera e depura o Python do caso | nunca rotula decisão como humana |

## 2. Fluxo

```
Recorder grava (FILL, CLICK; targetId por etapa)
  └─ GuidedPlan.of(modelo) ─ planRevision
        └─ Ctrl+Shift+D → escolher aba (LAB/AUTORIZADA) → studio.debug.start
              └─ por etapa: studio.debug.inspect (targetId | descritor) → status + motivos + verificações + caixa
                    ├─ RESOLVED e estado adequado → "Proposto" → Continuar → decideTarget(CONFIRMED, ator)
                    ├─ decisão anterior + mesma expectativa + RESOLVED agora → "Reconfirmado" (avança sozinho)
                    └─ senão → "Requer revisão"/"Não verificável" → Corrigir (REJECTED)
                          → Selecionar no navegador (studio.debug.pick, clique interceptado) → studio.debug.picked
                          → Comparar antes × depois → Aplicar (replaceTarget) → verificar o alvo novo
              └─ navegação/aba fechada/canal perdido → studio.debug.changed / LINK_LOST → nada vale da página anterior
        └─ GuidedDebugRecord (só códigos, ator) ── plano + registro da mesma planRevision = PLANO CONGELADO
              └─ gerar.py → consulta_registro.py (função por StepId) → execução via CDP → ORÁCULO do laboratório
                    └─ falha → debug guiado → reparo NO MODELO → nova validação → gerar de novo (diff) → executar de novo
```

## 3. Identidade

- O `targetId` gravado é a identidade lógica registrada pela extensão. A resolução do debug é **somente leitura**
  (D-S009-03): não atualiza o registro nem troca o dono.
- Sem `targetId` utilizável, o descritor persistido (`ElementReference`) gera uma identidade transitória
  (`identitySource: DESCRIPTOR`), sempre apresentada como mais fraca.
- Um localizador que casa **não** é identidade: o debug só propõe com `RESOLVED`; o script Python confere tag e nome do
  plano antes de agir (`IDENTITY_MISMATCH`).
- Elementos indistinguíveis (ex.: dois botões "Consultar" iguais) nunca são comprovados, nem depois da escolha
  explícita (política da CHROME-SECURITY-001): a decisão fica registrada, a verificação continua honesta.

## 4. Época da página

Cada sessão tem `pageEpoch`. Navegar, trocar o documento ou reiniciar a extensão avança a época; candidatos,
destaques e resultados anteriores deixam de valer (`CANDIDATE_EXPIRED`, `CONTEXT_UNAVAILABLE`). O Studio verifica
de novo a etapa atual a cada época nova.
