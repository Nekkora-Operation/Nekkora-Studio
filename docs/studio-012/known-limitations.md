# NEKKORA-STUDIO-012 — Limitações conhecidas

1. **Lista de nível sem identificador (caso real).** A lista não tem `id`, `name` nem `<label>`; o rótulo ao lado é
   célula de tabela, que é conteúdo e não metadado elegível. Ela convive com 14 listas iguais.
   - O localizador continua estrutural, e a etapa fica "Requer revisão".
   - O debug guiado responde AMBÍGUO e NÃO confere a opção (falha fechada).
   - A confirmação da opção foi comprovada na variante do laboratório que tem identificador (`name="ddlTipo"`).
   - Para o sistema real, o caminho é um identificador estável na página, que é do lado do sistema, ou uma revisão
     futura da elegibilidade de rótulos de célula, que precisa de aprovação.
2. **Item de menu sem nome elegível.** "Auditoria", "estudo" e "nível" não estão no vocabulário. O link do menu sai sem
   nome, e o localizador fica posicional dentro do iframe, em revisão. O vocabulário NÃO foi ampliado automaticamente;
   uma revisão versionada exige aprovação.
3. **Hover.** Só um menu aberto por regra CSS `:hover` legível da própria página é detectado (`REVEALED_BY_HOVER`).
   - Menus por script (`mouseover`) não são distinguíveis de abertos por clique e ficam sem a nota.
   - Folhas de estilo de outra origem não são legíveis.
   - Não existe ação HOVER: o gesto vira obstáculo de revisão, e a execução não o reproduz.
4. **Runtime Python e iframes.** `nekkora_rpa` 0.2.0 não tem escopo de frame, `select_option` nem hover. O gerador
   bloqueia alvo em iframe (`TARGET_IN_FRAME_NOT_SUPPORTED`). Suporte de frame no framework fica como lacuna futura, e
   não está implementado.
5. **Trilha real autenticada.** Não foi executada por mim. A gravação autenticada no ambiente de homologação é da
   usuária, que digita a credencial; eu audito o resultado só por metadados. Ver `homologation.md`.
6. **Finalidade antiga.** Projetos já existentes com Finalidade em forma de valor continuam abrindo. A recusa só
   acontece ao salvar pelo formulário. A cópia da usuária tem as duas variáveis nessa situação, e a correção é local.
7. **Seleção por teclado com a lista aberta.** No Windows, a escolha só é confirmada quando a lista nativa fecha (Enter,
   Tab ou clique fora). Até lá não há `change`, e nada é gravado.
