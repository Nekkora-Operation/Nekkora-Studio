# STUDIO-001 — Limitações conhecidas

## Funcionalidades que ainda não existem (por escopo)

- **Persistência**: tudo fica em memória. Fechar o Studio perde as automações (há aviso). Salvar, reabrir, recuperar e
  migrar formatos são da STUDIO-006. O botão "Salvar" está desabilitado.
- **Edição avançada** (STUDIO-004): excluir, mover e reordenar sessões/etapas; mover etapa entre sessões; editar
  comentários, descrição/observações de sessão pela tela, elemento, valor e opção. Esses dados existem no modelo, mas a
  interface só cria e edita nome/tipo/descrição de etapas e o nome de sessões.
- **Renomear automação** pela interface (a operação de domínio `updateDetails` existe).
- **Recorder / Chrome Extension** (STUDIO-002/003): sem captura, sem conexão com o navegador. O adaptador PageMap →
  `ElementReference` existe apenas como protótipo de teste (`PageMapContractTest`). "Gravar" está desabilitado.
- **Execução, IA, documentação (PDD/SDD/PDF), plataforma, login, licença**: não implementados (verificado por
  `StudioScopeTest`).
- **Cofre de credenciais**: `CredentialReference`/`VariableReference` apenas nomeiam valores.

## Limitações técnicas observadas

- **Toolbar fora da ordem de Tab**: padrão do FlatLaf (`ToolBar.focusableButtons`). Todas as ações da toolbar têm
  atalho e equivalente focável no conteúdo.
- **Folga de 6% em títulos Sora** (`NekkoraTextLabel`): heurística medida no Windows; em outros sistemas a diferença de
  métricas pode ser outra. Títulos em Inter não mudam.
- **Golden screenshots**: as imagens de referência do Windows usadas na comparação já existiam localmente e **não são
  versionadas** (`golden/windows/` estava fora do Git antes desta missão e assim permaneceu). As de Linux, versionadas,
  não puderam ser verificadas nesta máquina; o único rótulo Sora da composição golden é o título do topo, sem nada à
  direita, então não se espera deslocamento — a confirmar no próximo build Linux.
- **150%**: a tela disponível é 1920×1080 (área útil 1920×1032). A execução real a 150% usou 1920×1032 físicos
  (≈1280×688 lógicos); 1366×768 lógicos a 150% (2049×1152 físicos) não cabem nesta tela e foram verificados de forma
  headless por `Scale150Test`.
- **1920×1080**: a janela real usou 1920×1032 (a barra de tarefas ocupa o restante).
- **Leitores de tela**: nomes e descrições acessíveis foram verificados por teste; não houve sessão com NVDA/JAWS.
- **`DisplaySafety` é conservador**: códigos longos alfanuméricos legítimos (≥ 24 caracteres com letras e dígitos) podem
  aparecer como "—" nas propriedades técnicas.
- **Títulos longos** no cabeçalho do painel de propriedades são abreviados com "…"; o nome completo aparece na linha
  "Nome".
- **Evidências com Robot**: textos acentuados são inseridos no campo focado pela EDT; teclas de navegação e confirmação
  são reais.
- **Preferência de tema**: o `ThemeManager` grava o último tema escolhido (comportamento existente); os testes
  terminam em Sakura Dark.
