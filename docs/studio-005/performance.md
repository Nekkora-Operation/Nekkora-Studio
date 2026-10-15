# STUDIO-005 — Desempenho (Studio)

## Medições

| Medida | Resultado | Fonte |
|---|---|---|
| clique → etapa no Studio (Chrome real), p50 sem/com evidências | 811 / 810 ms | `S005-performance.json` (extensão) |
| emissão da evidência no service worker | 1–2 ms | idem |
| 60 evidências em rajada (thread do Bridge) com desfazer/refazer simultâneos em outra automação | registradas em ordem, sem erro, EDT livre | `s005ConcorrenciaComHistorico…` |
| homologação real: 49 evidências registradas numa gravação de 24 etapas | contadores atualizados ao vivo | captura `S005-08` |

## Por desenho

- **EDT**: só a operação de domínio e a notificação acontecem na EDT. Leitura, ordenação, espera da etapa e ACK
  acontecem na thread do Recorder. Um `RepaintManager` de verificação mostra que nada do Swing é tocado fora da EDT (teste de aplicação).
- **Atualização localizada**: `StudioChange.EVIDENCE` atualiza só a sessão afetada e o painel, não a automação inteira.
- **Painel limitado**: as 20 evidências mais recentes no painel; a lista completa só na aba Evidências.
- **Limites**: 100 evidências por etapa, 500 sem etapa por automação, 500 fora de ordem e 500 à espera da etapa no Recorder.
- **Resumo derivado sob demanda**: `observation()` percorre as evidências de **uma** etapa (no máximo 100).

## Regressão

337/337 testes do Studio no Windows e no Linux (Docker, Temurin 21), inclusive `EditorPerformanceTest` (STUDIO-004) e
os golden screenshots, sem ajustes.
