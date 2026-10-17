# STUDIO-008 — Limitações conhecidas

- **Nada é comprovado em execução.** Entrada e saída de etapa ficam como limitação ("não comprovada"); o Studio não
  executa automações nesta missão. O estado mais favorável continua sendo `NO_STATIC_BLOCKERS`.
- **Produtora só declarada.** Não existe captura de saídas de etapas; a ligação produtora→variável é sempre uma
  declaração do desenvolvedor.
- **Uma referência por etapa.** O modelo de parâmetros tem um único valor por etapa; o fluxo de dados é linear
  (sem desvios nem repetições).
- **Sem inferência.** Nomes parecidos não são associados; significado não é deduzido do nome nem da página; nenhuma
  proposta de significado vem de IA nesta missão.
- **Valor fixo em texto claro.** O `.nekkora` não é criptografado; por isso o valor fixo só existe para variáveis
  públicas ou internas e o formulário avisa.
- **Excluir/mover produtora ou sessão de escopo** não corrige a declaração; a validação aponta (D-S008-13).
- **Formato 1.1.0 não é lido por versões anteriores** do Studio; a migração pede confirmação e guarda uma cópia 1.0.0.
- **Contrato semântico não é exportado** pela interface; existe para consumo programático e testes.
- **Menu Automação e barra de ferramentas** não têm a ação de variáveis (D-S008-10); ela está no painel de propriedades
  da automação, no atalho Ctrl+Shift+B e na aba do painel inferior.
- **Representação só para CPF** (revisão 1): porcentagem (`15` × `0.15`), identificador e texto livre não têm
  representação canônica no vocabulário atual e não recebem o aviso; ampliar exige novos campos de restrição (formato do
  projeto) numa missão própria. A máscara de CPF confere só a forma, não os dígitos verificadores.
- **Linha "Declaração" do painel de variáveis** é cortada com reticências a 1366 px quando há muitas restrições (o
  texto completo está no componente e no formulário de edição).
- **Credenciais** continuam só apelidos, sem cofre.
- **Homologação sem navegador**: os cenários da 008 não envolvem gravação; a integração com o Recorder é a mesma da 007.
- `Studio007Homologation` (roteiro histórico, fora do `verify`) espera o comportamento das regras 1.0.0 para variáveis
  (D-S008-16).
