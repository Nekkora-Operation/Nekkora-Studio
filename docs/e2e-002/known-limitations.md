# NEKKORA-E2E-002 — Limitações conhecidas

- **Datas**: o código gerado normaliza para AAAA-MM-DD só a PRIMEIRA data por extenso completa da célula (dia, mês e
  ano). Data parcial ("1901", "março de 1901") não vira data: a linha é rejeitada e a planilha não é gravada (nada é
  presumido). Datas em outros formatos (numéricas, outros idiomas) não são reconhecidas.
- **Listas**: itens de uma célula são unidos por "; " a partir dos itens VISÍVEIS (`li`); texto entre itens fora de
  `li` não entra. Notas de rodapé dentro do item entram como texto do item, se visíveis.
- **Fonte alternativa** é usada só quando o alvo principal NÃO EXISTE na página; alvo existente e vazio rejeita o item.
- **Dado ausente sem fonte alternativa** (ex.: linha "Morte" de uma pessoa viva) faz o item falhar (alvo não
  encontrado) e a planilha não é gravada. Os quatro artigos da missão têm todas as linhas usadas; para listas com
  pessoas vivas seria preciso declarar a coluna como opcional — ainda não há pergunta para isso na especificação.
- **Tudo ou nada**: com uma falha técnica a execução para (`max_consecutive_technical_failures = 1` no código gerado)
  e a planilha é retida. Para reprocessar, uma nova execução (o estado depois do commit não aceita transação nova).
- **Teste autorizado** não é execução de produção: só as origens autorizadas como site público de treinamento, Chrome
  visível, uma lista pequena (a confirmação mostra a contagem). Não há agendamento nem fila.
- **Debug guiado** não percorre etapas sem alvo (ex.: leitura do endereço); elas são conferidas na execução
  (conferência do endereço com a lista de entrada).
- **Desempate pelo localizador gravado** só vale para a forma `<região> <tag>[atributo="valor"]` ou `<região> <tag>#id`
  produzida pelo próprio Recorder; outras formas continuam ambíguas.
- **IA**: a análise é opcional; a resposta inteira é recusada se não seguir o esquema (`SCHEMA_INVALID`) ou vier
  cortada (`PARTIAL_RESPONSE`) — nada é aplicado e as perguntas continuam abertas (visto 1 vez em 13 rodadas cada).
- **PDD desatualizado** é detectado por mudança de ESTRUTURA do processo (etapas, sessões, ações), não por renomear
  uma etapa (o texto do PDD é revisado à parte).
- **Linux**: a homologação real (Robot + Chrome visível) é só no Windows; no Linux rodam as suítes automatizadas.
