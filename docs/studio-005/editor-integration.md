# STUDIO-005 — Integração com o editor

## Princípio

O editor **mostra** evidências e **não as edita**. Nenhuma operação de edição da STUDIO-004 (nome, descrição, tipo,
parâmetros, localizadores, comentários, sessão, ordem, restaurar original) toca nas evidências, e nenhuma tela oferece
alterá-las.

## Telas

| Tela | O que mudou |
|---|---|
| Detalhes da etapa (F2) | nova aba **Evidências**, **a última**. As abas Geral (0), Parâmetros (1), Elemento (2), Comentários (3) e Origem e alterações (4) continuam nas mesmas posições. O espaçamento horizontal das abas foi reduzido para as seis caberem sem rolagem; um teste na escala da tela prova isso e falha sem a correção |
| Painel de propriedades | grupo **Evidências** depois de "Gravação", com o resumo e a linha do tempo recente; na automação, **Evidências sem etapa definida** |
| Lista de etapas | o selo da observação é acrescentado à linha secundária **só** quando há evidência (as saídas anteriores não mudam) |
| Floating Recorder | linhas **Evidências** ("N registradas · M sem etapa definida · K observações incompletas · P aguardando o Studio…") e **Última observação** (HH:mm:ss); o resumo final cita as evidências e "Resultado de negócio: não validado" |

## Operações de edição × evidências

| Operação | Evidências |
|---|---|
| editar campos | intactas |
| mover (na sessão ou para outra) | preservadas: a evidência pertence à etapa (objeto), não à posição |
| duplicar | a cópia nasce sem evidências (`NOT_APPLICABLE`); a original fica igual |
| excluir | saem com a etapa; desfazer devolve a mesma etapa com as mesmas evidências |
| restaurar original | intactas |

## Gravação em andamento

O bloqueio da STUDIO-004 continua: editar, mover, duplicar, excluir e desfazer são recusados no caso de uso para a
automação em gravação. A **recepção de evidências continua**, porque não é edição. Isso está provado em
`RecorderEvidenceStudioTest.s005BloqueioDeEdicaoComRecepcao` e na homologação (cenário 1).

## Acessibilidade e temas

As novas linhas do Floating Recorder têm nome acessível ("Evidências observadas", "Última observação"). Os rótulos do
painel são parágrafos com nome acessível igual ao texto. A aba Evidências e o Floating Recorder renderizam com texto nos
quatro temas (`s005AcessibilidadeETemas`). A informação nunca depende só de cor: o selo e o resumo são texto.
