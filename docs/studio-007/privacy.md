# STUDIO-007 — Privacidade

A validação não enfraquece a `nekkora-privacy/2` e não cria nenhum caminho novo para dados capturados.

## O relatório contém

Identidades (UUID da automação, sessão e etapa), posições, códigos estáveis, severidades, campos, detalhes do
vocabulário fechado e textos de modelos controlados.

## O relatório nunca contém

- valores literais, nomes de variáveis, apelidos de credenciais;
- nomes de automações, sessões ou etapas, descrições e comentários;
- conteúdo capturado de páginas (mensagens, nomes acessíveis, títulos, endereços);
- `id`/`name` HTML, expressões ou descrições de localizadores (capturados ou manuais);
- textos livres do produtor (descrição de obstáculos, notas de estabilidade);
- identificadores de gravação, eventos ou evidências;
- objetos completos do domínio ou mensagens brutas do navegador.

`ValidationContractTest.s007027SemDadosPrivados` procura cada um desses textos (de uma automação montada com todos eles)
no JSON. O painel mostra os NOMES de sessões e etapas que o próprio usuário escreveu (como o restante do Studio), lidos
do estado atual — eles não fazem parte do relatório.

## Privacidade não é defeito

Nome omitido, identificadores suprimidos, localizadores ou opção descartados pela política geram, no máximo,
informações que dizem explicitamente "não é defeito da captura". Nenhuma orientação sugere desativar a proteção.

## Logs

A validação não escreve logs. A homologação confere que os logs do Studio não contêm o que foi digitado nem nomes de
variáveis usados nos cenários.
