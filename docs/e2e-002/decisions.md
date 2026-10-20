# NEKKORA-E2E-002 — Decisões

## Da usuária (registradas pelo AskUserQuestion, 2026-10-20)

| ID | Decisão | Onde vale |
|---|---|---|
| D-E2E2-01 | O texto do cabeçalho de linha (`th`/`dt`) pode sair da página como **âncora** do dado, só se for 100% vocabulário de interface; o vocabulário ganha `morte`, `nacionalidade`, `ocupacao` (com histórico). | extensão (`capture-policy.js`, vocabulário) e cópia do Studio (`CapturePrivacy`) |
| D-E2E2-02 | **Fonte alternativa declarada no PDD**: Ocupação ausente → linha "Campo(s)"; Nome completo ausente → título do artigo. A fonte usada vai para a proveniência. | especificação funcional (`FALLBACK:`), apêndice "Dados do processo" do PDD, código gerado |
| D-E2E2-12 | Marcadores `(s)`, `(es)`, `(a)`, `(as)`, `(o)`, `(os)` colados a uma palavra não são termo (`Campo(s)` = `Campo`); nenhum termo novo. | extensão + Studio |

## Técnicas (tomadas pelo agente com base em evidência)

| ID | Decisão | Por quê |
|---|---|---|
| T-01 | A execução supervisionada fica no **Studio** (botão "Executar teste autorizado"), sobre o mesmo isolamento de subprocesso do `PythonChecks`. | O Nekkora Agent não tem executor (bloqueia `python.exe`); "Validar automação" continua sem efeito. |
| T-02 | **Selecionar dado** é um modo próprio do Recorder (`SELECT_DATA` → ação `EXTRACT` → etapa Observar): nada é executado na página e nenhum texto sai, só o elemento e a âncora. | O Recorder descartava clique em nó não interativo (prova ANTES). |
| T-03 | Item de lista/parágrafo **dentro de uma célula cuja linha tem `th`** propõe a CÉLULA inteira (os itens são lidos e unidos por "; "). | Homologação r1: clicar num item de "Ocupação" gerava alvo posicional. |
| T-04 | `<input role="combobox">` (combobox editável, ARIA 1.2) pertence à família **text-entry**; `<select>`/listbox continuam "choice". | A busca da página troca searchbox → combobox ao receber foco: era o mesmo campo, julgado "outro tipo de controle". |
| T-05 | Região sem id/nome tem chave pelo marco (`landmark:<tag>`); no debug, empate entre candidatos de identidade CONFIRMADA é decidido pelo localizador **contextual gravado** quando ele seleciona hoje exatamente um deles (`DISAMBIGUATED_BY_RECORDED_LOCATOR`). Sem seleção única, continua ambíguo. | A busca do cabeçalho e a cópia oculta do cabeçalho fixo são indistinguíveis pelas características do elemento; o localizador gravado (verificado único) é o mesmo que a execução usa. |
| T-06 | Fluxo **lista → planilha** (`DATA_FLOW = LIST_XLSX`) é uma pergunta OPCIONAL da especificação; continua visível depois da geração; o plano o rotula como modelo `LIST`. | Sem a pergunta visível, a pessoa não tinha como escolher depois da 1ª geração (r6). |
| T-07 | Colunas: derivadas do rótulo da linha (revisáveis), "URL" para a leitura do endereço; o título não tem rótulo seguro → pergunta. | Não presumir colunas; o rótulo é dado de interface, não do artigo. |
| T-08 | Etapa sem alvo e sem tela (criada no editor) herda a tela da etapa seguinte da sessão. | r9: "Ler o endereço da página" rodava antes da espera da página e podia ler o endereço anterior. |
| T-09 | Propostas da IA só saem quando aplicadas, obsoletas (etapa removida/decisão registrada) ou ao trocar de automação; teto de saída 4 096 tokens. | r3/r8: propostas sumiam numa reanálise interna; 2 048 cortava respostas válidas (`PARTIAL_RESPONSE`). |
| T-10 | Campo de busca sem rótulo seguro recebe a variável `termo_de_busca` (não `entrada_etapa_N_M`). | Nome técnico aparecia na lista de entrada pedida à pessoa. |
| T-11 | Framework: nó desanexado DURANTE a verificação de identidade = `TARGET_DETACHED` (transitório). | r9: o Enter da busca falhava sem repetição quando o componente era recriado. |
| T-12 | Framework: depois do `COMMITTED` o estado da planilha guarda só hash, contagem e transações. | A cópia dos valores ficava na pasta de execução que o Studio descreve como "eventos sem valores". |
| T-13 | As opções de uma pendência ficam em linhas de até 3 botões. | r7: com uma opção por coluna, os botões finais saíam da janela e o clique real do Robot caiu no Chrome. |
