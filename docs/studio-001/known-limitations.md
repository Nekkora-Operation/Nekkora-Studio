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
- **Folga de 6% em títulos Sora** (`NekkoraTextLabel`): heurística medida na janela real do Windows (~4%). No Linux,
  a medição foi headless (até 2,9%, nenhum título cortado), porque não houve janela real Linux. Títulos em Inter não
  mudam.
- **Golden screenshots** (situação em 2026-10-14, PR #2): os dois baselines estão versionados.
  - Windows: as 4 imagens originais de 2026-09-26 (Microsoft OpenJDK 21.0.12), as mesmas usadas como referência
    durante a STUDIO-001, versionadas sem regeneração.
  - Linux: regravadas no ambiente de referência fixado por digest (Temurin 21.0.12.1+1, `tools/golden-linux.sh`). As
    anteriores correspondiam ao JDK 21.0.10, e a partir do 21.0.11 a métrica dos títulos em Sora mudou.
  - A STUDIO-001 não altera a composição golden em nenhum dos dois sistemas.
  - A folga de títulos foi verificada também no Linux.
  - Os golden são sensíveis à versão do JDK: fora do ambiente de referência, uma diferença pode vir do JDK. Detalhes
    em `reports/studio-001-golden-baselines-report.md`.
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
