# STUDIO-002 — Bridge: servidor (Studio) e cliente (Native Host)

Contrato: `nekkora-bridge/1`, mantido na extensão (`Nekkora-Extension-Chrome/contracts/studio-bridge.schema.json` e
`docs/studio-002/bridge-protocol.md`). Este repositório guarda uma cópia do schema e das fixtures em
`nekkora-studio-bridge/src/test/resources/contracts/`. Quando o repositório da extensão está ao lado, o
`BridgeContractFixturesTest` confere que a cópia não divergiu, no mesmo modelo do PageMap.

## Servidor (`nekkora-studio-bridge`)

| Classe | Papel |
|---|---|
| `BridgeServer` | publica e atende; implementa `BrowserLink` (a visão que o Recorder usa) |
| `BridgeDirectory` | diretório padrão (o mesmo algoritmo do host); ACL definida só para o dono, com herança; conferência da ACL efetiva |
| `BridgeAuth` | nonces de 32 bytes, provas HMAC-SHA256, comparação em tempo constante |
| `BridgeFrames` | quadros de 4 bytes big-endian + JSON UTF-8, até 256 KiB |
| `Json` | JSON estrito, sem dependências (chave duplicada, caractere de controle e profundidade acima de 64 recusados) |
| `BridgeProtocol` | vocabulário do Studio (comandos, eventos, estados, modos), conferido contra o contrato |
| `BrowserLink.offline()` | ligação inexistente (testes, segunda instância): estado UNAVAILABLE, todo comando recusado |

### Publicação (`BridgeServer.start`, fora da EDT, antes da janela)

1. `BridgeDirectory.prepare(%LOCALAPPDATA%\Nekkora\Studio\bridge)`: cria, restringe e confere. Falha →
   `UNAVAILABLE/INSECURE_DIRECTORY`.
2. Trava de instância `bridge.lock` (`FileChannel.tryLock`). Ocupada → `UNAVAILABLE/OTHER_INSTANCE`. O Studio continua
   funcionando sem gravação e a barra de status explica.
3. Transporte: `AUTO` tenta Unix Domain Socket (`studio.sock`), removendo antes um órfão (a trava garante que é órfão)
   e conferindo a ACL do socket. Se o caminho tiver mais de 100 caracteres ou o UDS falhar, usa TCP em
   `InetAddress.getLoopbackAddress()` com porta dinâmica. A propriedade `nekkora.bridge.transport=unix|tcp|auto` força
   uma escolha.
4. Segredo de 32 bytes e `instanceId` novos a cada inicialização. O arquivo `studio-bridge.json` (protocolo,
   transporte, endereço, instância, pid, segredo) é escrito em `.tmp`, restringido, conferido e movido atomicamente.
5. A thread de aceitação começa e o estado vai para `WAITING` ("aguardando conexão com a extensão").

### Conexão

Desafio → `hello` (prova do host) → `welcome` (prova do Studio) → `CONNECTED` com a versão da extensão. O handshake
tem prazo de 5 s; um handshake parado é fechado por outra thread. Uma segunda conexão autenticada recebe `BRIDGE_BUSY`:
um navegador controlador por vez. Batimentos a cada 5 s; sem sinal por 15 s, o canal fecha (`HEARTBEAT_LOST`).

### Comandos e eventos

`send(type, payload, timeout)` devolve um `CompletableFuture` e nunca bloqueia quem chama. A resposta completa o futuro.
O erro vira `BridgeCommandException(code, message, reason, retryable)`. O tempo esgotado vira `TIMEOUT`. Se o canal
cair, os pendentes falham com `LINK_LOST`, e nada é presumido. Eventos `studio.recording.*` vão para os ouvintes.
`bridge.connected` atualiza a versão da extensão. `bridge.disconnected` registra o motivo (ex.: `CHROME_DISCONNECTED`),
que aparece no estado `WAITING`.

### Encerramento

`close()`: fecha o canal, apaga o arquivo de descoberta e o socket, zera o segredo e solta a trava. É chamado ao sair do
Studio e por um gancho de desligamento da JVM.

## Cliente (no repositório da extensão)

`StudioLink` no Native Host. Só procura o Studio depois do opt-in no painel. Confere o arquivo (dono e ACL; TCP só em
loopback; pid vivo), autentica, aplica a lista explícita e valida pelo contrato tudo o que entra e sai. Detalhes em
`Nekkora-Extension-Chrome/docs/studio-002/native-host-integration.md`.

## Testes

| Teste | Cobre |
|---|---|
| `BridgeServerTest` (9) | permissões restritas; segredo fora dos logs; conexão e eventos; credencial inválida, protocolo incompatível e repetição; instância única e `BRIDGE_BUSY`; sem navegador, tempo esgotado e canal perdido (`send` não bloqueia); batimentos; limpeza ao fechar e nova identidade; TCP só em loopback; ACL com outro principal |
| `JsonTest` (2) | leitura e escrita sem perda; JSON estrito |
| `BridgeContractFixturesTest` (3) | fixtures compreendidas; vocabulário igual ao contrato; cópia idêntica à da extensão |
| `RealNativeHostBridgeTest` (2, módulo recorder) | **Studio real ⇄ processo real do Native Host** (JAR da extensão ao lado): handshake, comandos confirmados, renovação, evento do navegador, Chrome fechando a porta; host recusando resposta fora do contrato |
