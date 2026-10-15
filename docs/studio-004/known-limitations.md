# STUDIO-004 — Limitações conhecidas

- **Histórico só em memória e por sessão de uso.** Fechar o Studio perde o histórico e as automações (a persistência é
  da STUDIO-006). O limite é de 100 operações por automação.
- **Conflito descarta o histórico.** Se a automação recebeu etapas do Recorder (ou um cancelamento) entre duas edições,
  desfazer além desse ponto não é possível: o histórico daquela automação é descartado, com aviso.
- **Edição bloqueada durante a gravação.** Na automação em gravação (incluindo início, pausa, interrupção, encerramento
  e reconciliação com resultado desconhecido, e enquanto houver etapas recebidas e ainda não entregues), editar,
  reorganizar, mover, duplicar, excluir, comentar, mudar parâmetros e desfazer ficam indisponíveis. "Nova sessão"
  continua permitida, porque o Floating Recorder a usa. Navegar e consultar continuam livres.
- **Sem arrastar e soltar.** Reorganizar é feito por teclado (Alt+↑/↓), menus, botões do painel e o diálogo "Mover
  etapa". Não foi implementado o arraste.
- **Mudar a sessão no editor leva a etapa para o final.** A posição exata é escolhida em "Mover etapa".
- **Localizador manual não verificado.** Não há verificação ao vivo de expressões novas nem recálculo de localizadores.
- **Sem cofre de credenciais.** A credencial é só um apelido.
- **Sem movimentação entre automações.** Uma etapa só se move dentro da própria automação.
- **Sem autoria nos comentários.** O Studio não tem login.
- **Observações da sessão** usam o mesmo limite e a mesma mensagem de tamanho da descrição.
- **Memória do histórico.** Cerca de 0,4 MB (100 etapas) a 2,7 MB (1.000 etapas) para 100 operações, numa medição
  aproximada (`S004-performance.json`).
