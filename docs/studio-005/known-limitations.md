# STUDIO-005 — Limitações conhecidas (Studio)

1. **Sem persistência definitiva.** As evidências vivem com a automação em memória. Salvar e carregar é da STUDIO-006.
2. **Ambíguas na automação.** As evidências sem etapa aparecem só no painel da automação. Não há tela para associá-las
   manualmente, por decisão: o editor não altera evidências.
3. **Painel com as 20 mais recentes.** A lista completa fica na aba Evidências dos detalhes.
4. **Aba Evidências aberta é uma fotografia.** O diálogo de detalhes mostra as evidências do momento em que foi aberto;
   evidências tardias aparecem no painel e ao reabrir.
5. **Cobertura no Floating Recorder.** A cobertura (`Página acompanhada por completo`/`Parcial`) e o título da aba
   mudam nas fotografias da gravação (início, pausa, modo, rota, renovação com estado). Uma navegação no meio da gravação
   só aparece na próxima delas. É um comportamento da STUDIO-002/003 que não foi mudado.
6. **Linha do tempo pela ordem técnica.** O horário real é mostrado, mas não ordena; relógios de documentos diferentes
   não comprovam ordem.
7. **Etapa recusada pelo domínio.** As evidências dela são recusadas e informadas à extensão (`rejected`); elas não ficam
   guardadas sem etapa.
8. **Limites rígidos** (100 por etapa, 500 sem etapa). Acima deles a evidência é recusada e contada, nunca guardada pela metade.
9. **Achado de privacidade fora do canal de evidências** (extensão): o texto visível que repete um valor digitado aparece
   na captura técnica existente (log local e registro de alvos do motor). Não chega ao Studio por evidência; recomenda-se
   uma missão própria.
