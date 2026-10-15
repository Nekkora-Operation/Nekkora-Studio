# STUDIO-005 — Linha do tempo da observação

## Duas ordens, ditas com clareza

- **Ordem técnica**: a sequência das evidências da gravação (`evidenceSequence`). É a ordem em que aparecem na linha do
  tempo. A chegada fora de ordem é reordenada no domínio.
- **Horário real**: `observedAt` de cada evidência, com milissegundos (`HH:mm:ss,SSS`, fuso do computador). Ele é
  mostrado, mas **não** ordena, porque relógios de documentos diferentes não comprovam ordem.

## Onde aparece

| Lugar | Conteúdo |
|---|---|
| Aba **Evidências** (última aba dos detalhes da etapa) | "Resumo da observação" + "Linha do tempo (ordem técnica, horário real)" completa + aviso de que evidências não são validação de negócio e não podem ser editadas |
| Painel de propriedades → grupo **Evidências** | o mesmo resumo + as 20 evidências mais recentes (as anteriores ficam na aba) |
| Painel da automação → **Evidências sem etapa definida** | as ambíguas, com "N etapas candidatas — nenhuma escolhida" |
| Lista de etapas | selo: "Mudanças observadas", "Indicador de erro observado", "Observação incompleta", "Nenhum sinal observado" ou "Observando…" |

## Linha de cada evidência

`horário · título — detalhes`. O título é o rótulo do tipo, mais a classificação quando houver. Os detalhes trazem:
trecho entre aspas ou "Texto omitido por privacidade", campo, região, mudança de estado (`aria-expanded: não → sim`),
navegação (mesmo ou novo documento + URL sanitizada), sinais na janela, candidatas (ambígua), confiança, "em iframe",
"em Shadow DOM" e limitações.

Exemplo real da homologação (cenário 1):

```
14:39:57,770  Formulário enviado — observada diretamente
14:39:57,880  Mensagem de erro exibida · Indicador de erro — "Usuário ou senha inválidos." · mensagem de erro do formulário login · associada pelo tempo
14:40:00,785  Observação concluída — 1 sinal(is) na janela · observada diretamente
```
