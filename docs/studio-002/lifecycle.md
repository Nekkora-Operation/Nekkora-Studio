# STUDIO-002 — Ciclo de vida, recuperação e desvios justificados

## Do zero à gravação

| Passo | Studio | Host | Extensão |
|---|---|---|---|
| Studio abre | publica o Bridge e mostra "aguardando conexão com a extensão" | — | — |
| Chrome abre | — | não existe ainda | se "Conectar ao Nekkora Studio" já estava ligado, abre a porta nativa ao iniciar |
| Usuário liga o interruptor no painel | — | iniciado pelo Chrome; recebe `studio.link.configure` | guarda o opt-in em `storage.local` e mantém a porta e o alarme |
| Host encontra o Studio | `CONNECTED` com a versão da extensão | lê a descoberta, autentica, envia `bridge.connected` | painel: "Conectada ao Nekkora Studio" |
| Gravar → aba → Iniciar | STARTING → RECORDING (confirmado) | repassa `studio.recording.start` | valida e adquire `studio-recorder:<id>` |

A solução não depende de um service worker sempre ativo por sorte. Com o opt-in, a porta nativa aberta mantém o worker
vivo. Se ele for encerrado, o alarme de 30 s e o evento seguinte o reacordam, a porta é reaberta, o host renasce e se
reconecta ao Studio. Isso está coberto por `Studio002BridgeIT.s002020…`.

## Recuperação

| Evento | Detecção | Comportamento |
|---|---|---|
| Studio fecha normalmente com gravação | diálogo de saída | Finalizar ou Cancelar enviados e confirmados (até 5 s), depois arquivo de descoberta apagado e segredo zerado. Homologado (18.1) |
| Studio trava ou cai | o host perde os batimentos ou a conexão; a extensão deixa de receber renovações | a concessão expira (30 s; homologado com 10 s: **expirou 10,15 s após a queda**) e **só** o consumidor do Recorder é liberado: a inspeção manual continuou ativa |
| Chrome fecha | o host recebe EOF, avisa `bridge.disconnected/CHROME_DISCONNECTED` e sai | Studio: DEGRADED (`LINK_LOST`), "Nada foi dado como finalizado"; opção "Encerrar no Studio"; ao reabrir o Chrome, a conexão volta sozinha (homologado) |
| Host reinicia (service worker reiniciado) | nova porta, novo `configure` | Studio: DEGRADED → RECOVERING → consulta `status` → RECORDING (estado mínimo restaurado na extensão) |
| Ligação perdida durante o início | `LINK_LOST` | DEGRADED. Ao voltar, a consulta decide entre RECORDING e "Gravação não iniciada" |
| Aba fechada / permissão revogada | eventos da CaptureSession e da política | DEGRADED com o motivo; nada continua observando uma origem revogada |
| Comandos duplicados | `operationId` | mesmo resultado (`duplicate`), sem repetir efeitos |

## Persistência técnica mínima (21.1)

| Onde | O quê | Validade | Limpeza |
|---|---|---|---|
| Studio: `studio-bridge.json` | protocolo, transporte, endereço, instância, pid e **segredo efêmero** | enquanto a instância vive | apagado ao fechar; um arquivo deixado por queda é ignorado (pid morto) e sobrescrito |
| Studio: `bridge.lock`, `studio.sock` | trava e endpoint | enquanto a instância vive | o socket é apagado ao fechar; a trava é liberada pelo sistema |
| Extensão: `storage.session` `nk.srec.*`, `nk.tabrefs` | ids, estados, contadores, modo, concessão e referências de abas (nada da página) | sessão do navegador | retenção de 20 gravações encerradas; apagado quando o navegador fecha |
| Extensão: `storage.local` `nekkora.studioLink` | opt-in do canal | até o usuário desligar | — |

Nada disso salva automações: o Studio continua sem persistência definitiva (STUDIO-006).

## Desvios em relação ao texto da missão (justificados)

| Item da missão | Implementado | Por quê |
|---|---|---|
| Estado `PAUSED` do Recorder com pausa "suspendendo a participação" | pausa = liberação real do consumidor; se o Recorder era o único, a sessão de captura termina e a retomada estabelece nova base | é a estratégia preferencial da seção 12.2, verificável; documentado que, com a inspeção ativa, a página continua observada (`otherConsumers`) |
| Comandos `studio.recording.*` da lista sugerida | + `studio.recording.renew`; eventos + `studio.recording.recovered` | concessão renovável (D-07) e retomada a partir de uma interrupção |
| "Procedimento claro para estabelecer a conexão" | opt-in "Conectar ao Nekkora Studio" no painel | o host é iniciado pelo Chrome; o Studio não pode iniciá-lo. O opt-in evita que a extensão mantenha o host vivo sem necessidade |
| "Autorizar pela extensão" na homologação | o site é concedido e revogado pelo controle "Acesso ao site" do Chrome (`chrome://extensions`) | a regra da missão proíbe automatizar o diálogo de permissão; esse controle é do próprio Chrome. O diálogo real foi homologado manualmente na REAL-001 |
| Aba identificada pelo id do Chrome | `tabRef` opaco | identificadores internos do navegador não saem da extensão (seção 21) |
| Operação longa da S002-022 | `host.hold` (comando de teste do motor, até 60 s) | "operação controlada" com tempo conhecido. Ela ocupa a mesma thread de uma exportação real, e o comportamento do motor é idêntico |
