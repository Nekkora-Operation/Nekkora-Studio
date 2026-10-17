# STUDIO-010 — Visual PDD Generator

PDD visual local: o Studio projeta a automação (sessões → seções, etapas consecutivas da mesma tela → blocos), deriva
títulos e instruções por modelos determinísticos, permite revisar, dividir e juntar, associa a cada bloco uma captura
**real e sanitizada** do Chrome (só em laboratório, com gesto do usuário) e exporta PDFs versionados.

| Documento | Conteúdo |
|---|---|
| [g0-audit.md](g0-audit.md) | auditoria de reutilização, experimentos de permissão no Chrome real, proposta de captura segura |
| [architecture.md](architecture.md) | peças, fluxo de dados por fronteira, invariantes |
| [decisions.md](decisions.md) | D-S010-01..14 (D-S010-13 pede revisão da usuária) |
| [contracts.md](contracts.md) | `pdd-capture/1` no Bridge, manifesto da extensão, rascunho `pdd-visual` 1.0.0, PDF e manifesto de exportação |
| [visual-policy.md](visual-policy.md) | política `nekkora-visual/1`: regiões, limites, as 4 verificações de pixels, ciclo da imagem bruta |
| [privacy.md](privacy.md) | o que nunca chega ao documento e onde isso foi provado |
| [test-plan.md](test-plan.md) | matriz da missão × onde foi provado |
| [homologation.md](homologation.md) | homologação real G9 (55/55) e achados |
| [known-limitations.md](known-limitations.md) | limitações e riscos residuais |
| [evidence/](evidence/g9) | imagens sanitizadas, PDFs v001/v002, manifestos, páginas renderizadas, rascunho, capturas da janela, log |

Relatório final: [reports/studio-010-final-report.md](../../reports/studio-010-final-report.md).
