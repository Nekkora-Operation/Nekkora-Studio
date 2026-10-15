# STUDIO-003 — Limitações conhecidas (Studio)

- **Sem persistência definitiva.** As automações continuam em memória (STUDIO-001). Se o Studio for encerrado de
  forma abrupta, as etapas ainda não recebidas ficam retidas na extensão até a concessão expirar. **Não há promessa
  de recuperá-las.**
- **Resultado de negócio não validado.** "Interação observada" significa que a ação foi encaminhada à página, não
  que a operação deu certo. A análise de contexto e de resultado é da STUDIO-005.
- **Edição avançada** das etapas gravadas (renomear em lote, reordenar, comentários completos) é da STUDIO-004. A
  edição básica (nome, descrição, tipo) continua disponível.
- **Valor do preenchimento.** FILL gravado não tem valor. Associar variável, credencial ou valor explícito fica para
  missões seguintes; não há cofre de credenciais.
- **Ordem entre iframes de outra origem** é a de recepção na extensão (nota `CROSS_FRAME_ORDER_BY_RECEPTION`).
- **Elemento não correlacionado** (`UNRESOLVED`) entra como etapa com o obstáculo "elemento não correlacionado",
  sem localizador definitivo, para revisão.
- **Extensão anterior (STUDIO-002).** O Studio refaz o início sem a capacidade `steps/1`, e a gravação fica só
  técnica, com o aviso "Captura de ações indisponível nesta gravação".
- **Homologação com janela e Chrome reais** foi feita no Windows. No Linux rodam os testes automatizados, incluindo
  os cruzados com o Native Host real.
