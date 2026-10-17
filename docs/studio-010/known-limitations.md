# STUDIO-010 — Limitações conhecidas e riscos residuais

| # | Limitação | Efeito | Encaminhamento |
|---|---|---|---|
| L1 | Captura só em origens `LAB` (laboratório local) | o PDD visual não documenta sistemas reais nesta versão; blocos ficam "Imagem indisponível" | por decisão da missão; liberar outras origens exige missão própria com política de autorização |
| L2 | A imagem mostra só rótulos de interface do vocabulário e molduras de campo | títulos, tabelas, mensagens, imagens, iframes e shadow ficam opacos (ex.: o Resultado aparece só com "Nova consulta" e "Sair") | consciente (lista de permissão); ampliar só numa `nekkora-visual/2` revisada |
| L3 | Rótulo cujo texto tem termo fora do vocabulário fica opaco | botões com nomes de negócio não aparecem | idem L2 |
| L4 | Captura exige o gesto Alt+Shift+K no Chrome; o atalho pode estar vazio (conflito ou removido) | o Studio orienta `chrome://extensions/shortcuts`; Alt+Shift+P é recusado pelo Chrome 155 | D-S010-11 |
| L5 | Marcador só para alvo comprovado pelo resolvedor da STUDIO-009 | etapas declaradas, alvos em iframe/shadow, ambíguos ou fora do viewport ficam sem marcador (aviso, não bloqueio) | herdado da 009 (L1/L2 de lá) |
| L6 | Imagem bruta existe por instantes na memória do service worker | não é retida nem enviada; limite declarado (A8) | — |
| L7 | 150% homologado com zoom do Chrome (IT G5-01), não com a escala do Windows; 1366×768 simulado pelo tamanho da janela numa tela 1920×1080 | layout verificado; tela física não | homologar numa máquina/VM dedicada |
| L8 | Linux: módulo e PDF testados (regressão Linux), captura real no Chrome só no Windows | — | — |
| L9 | Bloco cujas etapas mudam perde a imagem e as revisões ao atualizar | é preciso revisar e capturar de novo esse bloco | conservador por desenho |
| L10 | Aspas tipográficas viram `"` no PDF; fonte Inter embutida (subconjunto) | o texto extraído do PDF difere do da janela nas aspas | D-S010-12 |
| L11 | Paginação homologada no Windows real só com uma página | várias páginas (bloco maior que uma página, 6 seções) cobertas pelo teste `PddPdfTest.variasPaginas`, sem inspeção visual | cenário longo numa homologação futura |
| L12 | O rascunho mora no diretório de dados do Studio, não no `.nekkora` | outro computador abre o projeto sem o rascunho | D-S010-02; levar ao formato só com migração aprovada |
| L13 | Ajustes em testes de missões anteriores (D-S010-13) | `StudioScopeTest` e a expectativa do menu Automação mudaram | **para revisão da usuária** |

Dados reais continuam **não autorizados** (CHROME-SECURITY-001 §25).
