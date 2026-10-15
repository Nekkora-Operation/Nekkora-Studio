# STUDIO-002 — Segurança e privacidade (lado do Studio)

A visão completa das ameaças, incluindo o lado do host e da extensão, está em
`Nekkora-Extension-Chrome/docs/studio-002/security.md`. Aqui ficam as garantias do Studio.

## Canal local

- **Somente local**: Unix Domain Socket no diretório do usuário. A alternativa TCP fica **só** em loopback
  (`InetAddress.getLoopbackAddress()`), com porta dinâmica, e nunca em `0.0.0.0` (`BridgeServerTest.tcpSomenteNoLoopback…`).
- **Permissões efetivas conferidas**: o diretório recebe uma ACL definida (uma entrada do dono, herdável por arquivos e
  subpastas). Socket e descoberta nascem restritos, e a ACL efetiva de diretório, arquivo e socket é relida. Se houver
  outro principal, a publicação falha de forma segura: Studio sem gravação, barra de status explicando. Estar em
  `%LOCALAPPDATA%` **não** é tomado como suficiente: a sonda real mostrou que, sem a entrada herdável, o socket nascia
  com Administradores e SISTEMA.
- **Credencial temporária**: 32 bytes aleatórios por inicialização. Não está no código, não aparece na interface e não
  vai para logs (verificado). É invalidada ao fechar: arquivo apagado e memória zerada. Uma nova instância gera outra.
- **Identidade e repetição**: desafio-resposta HMAC nos dois sentidos. O nonce do servidor muda a cada conexão, então
  a prova capturada de uma conexão é recusada em outra (`BridgeServerTest.recusa…Repeticao`).
- **Instância única e um controlador**: trava de arquivo (uma instância publica o Bridge) e uma conexão autenticada por
  vez (`BRIDGE_BUSY`). Uma gravação pertence à instância que a iniciou (`NOT_CONTROLLER` para outras).

**Limite declarado**: a autenticação por segredo em arquivo **não é proteção absoluta contra processos maliciosos
executados pelo mesmo usuário**, que podem ler o arquivo de descoberta como leem qualquer arquivo do usuário. Ela
protege contra a rede, outros usuários e processos que não leem o arquivo.

## Comandos

O Studio envia somente o vocabulário do contrato (`BridgeProtocol.COMMANDS`): controlar a gravação e listar abas. Não
existe comando para executar script, ler cookies, histórico ou credenciais, manipular arquivos ou contornar permissões.
O host e a extensão aplicam listas explícitas próprias e validam cada payload. O Studio **não concede** acesso a sites:
orienta o usuário a autorizar na extensão.

## Privacidade na interface (S002-026)

| Dado | Situação |
|---|---|
| `recordingId`, `operationId`, `instanceId`, segredo | técnicos; nunca exibidos (`RecorderStudioTest.s002026Privacidade`) |
| Ids de automação, sessão e etapa | usados internamente para resolver nomes; nunca exibidos |
| Aba do Chrome | `tabRef` opaco (só transporte); a interface mostra título mascarado pela extensão e host |
| URL da página | nunca chega ao Studio: o contrato não tem o campo, e o host recusa respostas que o tragam (`RealNativeHostBridgeTest.contratoAplicadoPeloHostReal`) |
| Mensagens de erro vindas da extensão | passam por `DisplaySafety`; texto com aparência de identificador é trocado por uma mensagem genérica |
| PageMap ou conteúdo da página | nunca trafega pelo canal de controle |

## Logs

O Studio registra apenas eventos operacionais com códigos (publicação, credencial inválida recusada, canal sem sinal),
nunca o segredo, payloads ou títulos.
