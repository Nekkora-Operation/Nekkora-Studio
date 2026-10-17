# STUDIO-009 — Limitações conhecidas e riscos residuais

| # | Limitação | Efeito | Encaminhamento |
|---|---|---|---|
| L1 | Elementos indistinguíveis (sósias sem diferença) nunca são comprovados, nem depois da escolha explícita no navegador | a etapa fica "Requer revisão" e sem reuso automático; a decisão do operador é registrada | política aceita na CHROME-SECURITY-001 (indistinguível ⇒ AMBIGUOUS); recuperar só em missão própria, sem enfraquecer a política |
| L2 | Identidade gravada no documento principal não é reencontrada se o processo passa a estar dentro de iframe ou shadow root | NOT_FOUND honesto; o reparo pela seleção no navegador funciona dentro de iframe da mesma origem e de shadow aberto | shadow **fechado** não é selecionável (o clique chega ao hospedeiro); não testada seleção em iframe de **outra** origem |
| L3 | O gerador Python é do **caso de laboratório** (FILL e CLICK, documento principal) | outras ações, quadros e shadow são bloqueados na geração | não é gerador geral do produto; generalização é outra missão |
| L4 | A conferência mínima de identidade do script (tag e nome acessível do plano) não substitui o resolvedor da extensão | protege contra "localizador casa, elemento errado" com nome diferente; sósias com o mesmo nome continuam dependendo do localizador estrito (2+ → AMBIGUOUS) | o caminho de identidade forte é o debug guiado antes da execução |
| L5 | 150% homologado com a escala da JVM (`sun.java2d.uiScale=1.5`), não com a escala do Windows | layout e temas da janela verificados; a geometria entre aplicativos (janela × alvo no Chrome) foi verificada com conversão do roteiro, não com a escala do sistema | homologar com a escala do Windows numa máquina dedicada |
| L6 | 1366×768 não exercitado | a janela tem largura fixa (420 px lógicos) e conteúdo rolável; a barra do Studio já transbordava a 1366 px (STUDIO-008) | verificar numa tela/VM 1366×768 |
| L7 | Reinício da extensão e queda do processo do Native Host não foram exercitados no Chrome real | cobertos só por teste de unidade (sessão volta DEGRADED, concessão vence) | cenário de resiliência numa missão de release |
| L8 | Troca de sessão/automação no Studio durante o debug não foi exercitada | o presenter usa o `automationId` da abertura | cobrir num teste de interface |
| L9 | `BrandedChromeIT` (CHROME-002) é intermitente: `session.synchronize` logo após uma mutação pode responder `PENDING` | falhou uma vez na regressão desta missão e passou ao repetir; não envolve o debug | endurecer o teste (esperar o delta) fora desta missão |
| L10 | A sonda LAB usa eventos sintéticos declarados como tal | prova o caminho, não o comportamento de um usuário real | é ferramenta de laboratório; nunca disponível fora de origem LAB |
| L11 | Uma primeira versão das capturas do G8 não tinha máscara (o valor fictício só ficava escondido pela janela do Studio, por geometria) | os commits locais foram reorganizados antes do primeiro push: a branch publicada só tem as capturas mascaradas | — |

Dados reais continuam **não autorizados** para o Recorder e para o debug (CHROME-SECURITY-001 §25).
