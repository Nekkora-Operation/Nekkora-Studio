# NEKKORA-STUDIO-012 — Segurança e privacidade

## Projeto real da usuária

O original não foi alterado. Antes de qualquer trabalho:
- foi gravado o hash SHA-256 do original;
- foram criados um backup e uma cópia de trabalho com nome neutro, numa pasta privada fora dos repositórios (ACL só da
  usuária, do SYSTEM e dos Administradores);
- a auditoria de metadados e a homologação abriram só cópias, e conferiram o hash antes e depois (inalterado).

Do projeto real, nada foi impresso, versionado ou capturado em tela: nomes, descrições, Finalidades, URLs e
identificadores ficaram de fora. A Finalidade das duas variáveis (forma de valor) foi tratada como confidencial e não
foi lida.

Nem o `.nekkora` real nem cópias dele entram nos repositórios.

## Sistema de homologação

A página de login foi observada uma única vez, de forma passiva (carregar e ler o DOM), sem digitar, clicar ou enviar.
O resultado só tem contagens e presença, nada de valores:
- existe `id`/`name`;
- é único;
- quantos termos ficam fora do vocabulário.

A credencial de teste informada no chat não foi gravada em arquivo: o classificador de segurança recusou e a recusa foi
respeitada. Ela não aparece em código, testes, evidências, logs nem PRs. Recomendação: trocar essa senha ao fim da
missão, porque ela ficou no histórico da conversa.

Nenhum registro do sistema foi alterado.

## Política de privacidade (`nekkora-privacy/2`)

- A revisão `nekkora-identifier-terms/1` foi aprovada explicitamente pela usuária: lista FECHADA e versionada, válida
  SÓ para identificadores do desenvolvedor, sempre acompanhada de um termo do vocabulário.
- O vocabulário de interface não mudou: `ui-vocabulary.json` continua com 1064 termos.
- Texto de interface, opção escolhida, valores digitados, classes e URLs seguem as regras anteriores.
- Campo sensível continua sem `id` no descritor.
- `IDENTIFIER_WITHHELD` transporta só a PRESENÇA de `id`/`name`.
- A conferência de opção do debug guiado recebe o rótulo do Studio e devolve só contagem e sim/não. O rótulo vindo da
  extensão nunca aparece: o teste confere todas as mensagens recebidas.

## Iframes

- O sensor só se religa a documentos de frame que a captura já aceitou (mesma origem, ou origem autorizada com script
  próprio). O frame sem permissão continua sem script e declarado.
- O gerador recusa alvo em iframe, para nunca gerar código que aja no documento principal por engano.

## Evidências

- PNG não é versionado.
- O log da homologação foi saneado: o nome do projeto da usuária virou "projeto de reprodução", e a troca está declarada
  no README das evidências.
- Varreduras feitas:
  - os valores fictícios digitados (usuário, senha, texto de Finalidade) não aparecem no modelo, no `.nekkora` nem no
    log;
  - o histórico das branches não tem nome do cliente, host interno nem credencial.
