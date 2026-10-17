# STUDIO-009 — Guided Visual Debugger & AI-Validated Automation Proof

Debug guiado no navegador: o Studio verifica cada etapa gravada no Chrome (sem executar), destaca o elemento,
explica, e o operador continua ou corrige. A prova vertical termina num script Python + Playwright gerado do modelo
homologado, conferido por um oráculo independente.

| Documento | Conteúdo |
|---|---|
| [g0-audit.md](g0-audit.md) | auditoria de reutilização, recuperação de identidade, menor desenho vertical |
| [architecture.md](architecture.md) | peças, responsabilidades, fluxo, identidade e época |
| [decisions.md](decisions.md) | D-S009-01..23 (as marcadas "para revisão" pedem o aval da usuária) |
| [contracts.md](contracts.md) | plano 1.0.0, `guided-debug/1` no Bridge, `.nekkora` 1.2.0, registro 1.0.0, script Python |
| [privacy.md](privacy.md) | o que nunca sai do navegador, operador TEST_AGENT, capturas |
| [test-plan.md](test-plan.md) | matriz da missão × onde foi provado |
| [homologation.md](homologation.md) | homologação real G8 (106/106, 150% 11/11) e G9 (30/30) |
| [known-limitations.md](known-limitations.md) | limitações e riscos residuais |
| [python/](python/README.md) | caso Python do laboratório: gerador, scripts, dependências, instruções |
| [evidence/](evidence/) | capturas, logs, planos, registros, projeto e depuração do Python |

Relatório final: [reports/studio-009-final-report.md](../../reports/studio-009-final-report.md).
