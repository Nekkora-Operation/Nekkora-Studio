# STUDIO-007 — Limitações conhecidas

O Validator consegue comprovar apenas que a automação tem (ou não) as informações estruturais que as regras estáticas
verificam. Ele **não** comprova que a automação executa, que os localizadores encontram o elemento hoje, que variáveis
ou credenciais serão resolvidas, nem nenhum resultado de negócio.

| # | Limitação | Impacto | Mitigação / futuro |
|---|---|---|---|
| L1 | Sem verificação ao vivo de localizadores | um localizador validado na captura pode ter deixado de funcionar | informado como "validação histórica"; STUDIO-009 (proposta) |
| L2 | Sem catálogo de variáveis nem cofre de credenciais | referências nunca são comprovadas (`NOT_FULLY_ASSESSABLE`) | STUDIO-008 (proposta) |
| L3 | `WAIT_FOR` sem contrato de condição | a espera nunca é avaliada | aviso explícito |
| L4 | Etapas manuais não podem receber um elemento pelo editor | `ELEMENT_MISSING` só se resolve gravando a etapa | orientação no apontamento |
| L5 | Atualidade por geração de persistência | voltar ao estado validado (ex.: Ctrl+Z e Ctrl+Y) também marca desatualizado | conservador: basta validar de novo |
| L6 | Validação síncrona na EDT | automações muito maiores que 20.000 etapas (limite do formato) poderiam pesar | 5.000 etapas: ~6 ms no motor; painel medido na homologação |
| L7 | Relatório não persistido | fechar e reabrir exige validar de novo | intencional (D-S007-07); o resultado é idêntico |
| L8 | Linhas do painel mostram nomes do estado atual | num relatório desatualizado o nome pode já ter mudado | o painel está marcado como desatualizado |
| L9 | `NAVIGATE`: só confere a forma http(s) de um literal | endereços de outros esquemas viram aviso | conservador |
| L10 | Tecla de `PRESS_KEY`: lista fechada | teclas válidas fora da lista viram aviso | lista ampliável em nova versão das regras |
| L11 | Sem golden screenshot do painel | os goldens são do design system (independentes de plataforma); o painel do Studio depende de fontes do sistema | testes de renderização nos 4 temas, a 150% e conferência visual; capturas da homologação |
| L12 | Sem correções automáticas | o usuário corrige cada apontamento | intencional |
