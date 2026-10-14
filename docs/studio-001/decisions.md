# STUDIO-001 — Decisões

Formato: decisão · justificativa · alternativas consideradas.

**DS001-01 — Três módulos novos com os nomes propostos** (`nekkora-studio-domain`, `nekkora-studio-ui`,
`nekkora-studio-app`). Não havia módulos equivalentes; os nomes seguem o README ("(futuro) nekkora-studio-ui").
*Alternativa:* domínio e app num só módulo — rejeitada (P1/P10: domínio testável sem Swing e reutilizável pelo Recorder).

**DS001-02 — `Automation` como raiz do agregado; mutadores de sessão/etapa restritos ao pacote.** Toda regra (nomes,
unicidade, compatibilidade de parâmetros, privacidade de valores) fica em um lugar; a UI só chama operações.
*Alternativa:* entidades com setters públicos — permitiria contornar validações.

**DS001-03 — Ordem explícita = ordem das listas do agregado; numeração calculada.** `Automation.sequence()` produz
`StepPosition`; a tela mostra o número, e a identidade é o `StepId`. *Alternativa:* campo `order` inteiro em cada etapa
— exigiria renumeração e abre inconsistências sem ganho enquanto não há reordenação (STUDIO-004).

**DS001-04 — Identidades UUID em records com `toString()` mascarado.** Concatenação acidental nunca revela o valor
(S001-013). *Alternativa:* `String` — fácil de vazar em rótulos e logs.

**DS001-05 — `ElementReference` pequeno, alinhado ao PageMap 0.2.0, sem depender do código da extensão.** Enums
fechados e pequenos (`strategy`, `stability`, tipo de passo) viram enums Java com teste de compatibilidade; vocabulários
grandes e em evolução (`elementKind`, tipos de relação e de obstáculo) ficam como texto do contrato, validados pelo
fixture. *Alternativas:* depender de `java-core` da extensão (não publicado, acoplaria os repositórios); copiar o PageMap
(proibido pela missão).

**DS001-06 — Privacidade em duas camadas.** O produtor sanitiza (`nekkora-privacy/1`); o Studio aplica `DisplaySafety`
como barreira de **apresentação** e só exibe `id`/`name` HTML com política reconhecida. Campo sensível nunca recebe
literal. *Alternativa:* reimplementar a política completa da extensão — duplicaria o motor semântico.

**DS001-07 — Estado observável + serviço de casos de uso confinado à EDT.** `StudioState` (somente leitura para a UI)
e `WorkspaceService` (mutações + eventos tipados `StudioChange`). Sem singletons: `Studio.create` monta tudo. Cada
automação lembra a própria seleção. *Alternativa:* modelo de UI separado (cópias) — viola P2.

**DS001-08 — Diálogos descritos por `FormSpec` e exibidos por `StudioPrompts`.** A aplicação usa diálogos reais; os
testes usam o **mesmo** formulário e o mesmo `StudioDialogs.trySubmit`, sem janelas. A validação vem do domínio (o
`submit` executa a operação; se recusada, a mensagem aparece no campo). *Alternativa:* validar no formulário — duplicaria
regras.

**DS001-09 — Painel de propriedades somente leitura.** Edição pelos diálogos (que passam pelo domínio); o painel nunca
guarda cópia editável. *Alternativa:* edição inline — exige sincronização bidirecional (fica para a STUDIO-004).

**DS001-10 — Uma aba por automação aberta (`NekkoraWorkArea`).** Fechar a aba não exclui a automação (continua na
navegação); escolher na navegação reabre e restaura a seleção.

**DS001-11 — Ações futuras desabilitadas e explicadas.** Gravar/Executar/Salvar existem só como ações desabilitadas com
dica; nenhuma produz resultado. *Alternativa:* esconder — perde-se a sinalização de que nada é salvo.

**DS001-12 — Sidebar recolhida.** Com uma única área ("Automações"), a sidebar expandida (232px) deixava ~400px para a
automação em 1366×768. Recolhida (64px), a área central passa de ~780px.

**DS001-13 — Seleção de sessão indicada por selo "Selecionada".** A primeira versão usava elevação, que deslocava o
cartão; o selo é texto (não depende de cor) e não altera o layout.

**DS001-14 — Três correções genéricas no Design System, com teste de regressão cada** (necessidade demonstrada pelas
capturas reais e por thread dump):
1. `NekkoraParagraph`: a estimativa de tamanho agendava revalidação; num slot de largura zero (grupo recolhido do
   Inspector) a EDT entrava em ciclo infinito (aplicação congelada).
2. `NekkoraEmptyState`: descrição com largura fixa (340 "px" de CSS ≈ 442px reais) colapsava o ícone e cortava o texto
   em painéis estreitos; agora limita-se à largura disponível e o ícone tem tamanho mínimo.
3. `NekkoraTextLabel`: no Windows, títulos em Sora são desenhados até ~4% mais largos que a métrica de layout (medido:
   "Nova etapa" 92px × 96px); a última letra era cortada em `BoxLayout`. Folga de 6% só para a fonte de display.
Os golden screenshots do Windows (pré-existentes) continuam aprovados. *Alternativa:* contornar nas telas do Studio —
deixaria o defeito no Showcase e em futuras telas.

**DS001-15 — Escala 150% testada em JVM separada.** O FlatLaf fixa a escala ao instalar o Look and Feel; a execução
`scale-150` do Surefire roda `Scale150Test` com `-Dflatlaf.uiScale=1.5`.

**DS001-16 — Evidências com janela real e teclado real.** `StudioEvidence` (escopo de teste) usa `java.awt.Robot` para
Ctrl+N, Tab, Enter, Esc, setas e clique; textos acentuados são inseridos no campo focado pela EDT (o Robot não digita
acentos de forma portátil).

**DS001-17 — Leitor JSON mínimo nos testes** (`MiniJson`) em vez de adicionar Jackson apenas para dois fixtures.

**DS001-18 — Nomes únicos** de automação (no catálogo) e de sessão (na automação), sem diferenciar maiúsculas —
evita ambiguidade na navegação e na escolha de sessão.
