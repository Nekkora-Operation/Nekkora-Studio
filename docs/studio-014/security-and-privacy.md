# STUDIO-014 — segurança e privacidade

## O que nunca acontece

- **Nada é executado no sistema-alvo** por "Desenvolver automação" ou "Validar automação". A verificação do código
  (V4/V5) roda num Python isolado (`-I -B`: sem site do usuário, sem `PYTHONPATH`, sem bytecode na pasta do projeto),
  sem rede, com prazo e cancelamento; só compila, importa os módulos (que só definem classes e funções), roda ruff, mypy
  e os testes de contrato. `main.py` nunca é chamado. Execução real (V6) fica sempre "não verificada" no Studio.
- **Nenhum valor** entra no modelo, no PDD, na especificação, na configuração derivada, no código gerado, nos relatórios
  ou nos logs: o Recorder não grava o que se digita; as variáveis derivadas são declarações sem valor; senhas e o
  usuário de acesso viram apelidos de credencial (`credencial.senha`, `credencial.usuario`), lidos em tempo de execução
  de `NK_SECRET_<NOME>`; a entrada da execução vem de um arquivo `--entrada` validado antes de qualquer ação; os
  eventos do framework não registram valores.
- **Nenhuma URL com consulta**: o endereço gravado é um modelo; o endereço inicial informado é conferido contra ele e
  recusado com `?` ou `#`.
- **Nenhum código da IA**: a IA só propõe decisões de um vocabulário fechado; o gerador oficial é o único que escreve
  Python.

## IA opcional

| Item | Como é tratado |
|---|---|
| Chave | Só por variável de ambiente (`NEKKORA_ANTHROPIC_API_KEY` ou `ANTHROPIC_API_KEY`). Nunca no `.nekkora`, no PDD, no repositório, em configuração, em log, em mensagem de erro ou no corpo da requisição (vai só no cabeçalho). Nenhuma chave da Nekkora no aplicativo. |
| Consentimento | Por envio: a janela mostra o que VAI, o que NÃO vai e a carga EXATA em JSON; "Não enviar" não chama o provedor (provado na homologação com o contador do mock). |
| Carga | StepId, sessão/número, ação, tipo e rótulo SEGURO do alvo (o mesmo filtro do PDD), se é sensível, se está em iframe, se foi confirmado, as perguntas abertas, a versão e os ids das capacidades do framework, a identificação do prompt. Não vão: valores, URLs, credenciais/apelidos, nomes de variáveis, localizadores, imagens, PDF, conteúdo da página. |
| Sistema | O Prompt-Mestre vigente (hash conferido) + o contrato da chamada, que manda tratar a carga como DADO (injeção em rótulo é ignorada). |
| Resposta | Dado não confiável: o `PlanVerifier` recusa JSON fora do schema, campo extra, tipo de proposta desconhecido, StepId inexistente, valor fora do vocabulário, nome inválido ou de credencial, duplicata; justificativa com número, endereço ou dado é descartada. Nada é aplicado sem "Revisar e aplicar". |
| Limites | Estimativa de entrada recusada antes do envio (`BUDGET_EXCEEDED`), `max_tokens` de saída, prazo total, cancelamento. 429, 529, rede, resposta sem a ferramenta e corte por limite viram códigos; nada é repetido sozinho. |

## Testes adversariais (todos com mock ou servidor local — nunca a Anthropic)

`AiProviderContractTest`: carga minimizada (sem `http`, sem localizadores, sem apelidos); sem consentimento → zero
chamadas; StepId inventado, valor fora do vocabulário, comando de shell no valor, tipo desconhecido, duplicata e
justificativa com URL/CPF; JSON inválido e campo extra; 429/529/500/HTML/sem ferramenta/corte; chave ausente; orçamento;
chave só no cabeçalho; prazo esgotado; desconexão; cancelamento; mensagem de erro sem a chave.

## Dados da homologação

Laboratório local com dados fictícios. Usuário, senha e código de consulta de teste são sentinelas verificadas como
ausentes do modelo, do código gerado, dos eventos da execução e do log da homologação. As capturas de tela mascaram todo
campo editável da página antes de gravar a imagem.
