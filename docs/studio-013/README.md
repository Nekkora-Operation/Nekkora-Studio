# NEKKORA-STUDIO-013 — captura visual do PDD em sites autorizados (Studio)

Contrato, decisões D-S013-01..10 e políticas `nekkora-visual/3`, `/4` e `/5`: `Nekkora-Extension-Chrome/docs/studio-013/decisions.md`
(mesma branch `feat/nekkora-studio-013`). Este documento cobre o lado do Studio. Os resultados das execuções ficam em
`RESULTADOS.md`.

## Experiência

1. Abra a página no Chrome e conceda o acesso do Chrome à extensão, se ainda não tiver: ícone da extensão → Acesso ao
   site → Neste site. O Studio não concede acesso do Chrome.
2. No Studio, abra Automação → Gerar documentação → PDD visual… e escolha o bloco e a aba. A escolha de aba mostra o
   estado de cada uma:

   | Rótulo na lista | Estado | O que o Studio faz |
   |---|---|---|
   | `· laboratório` | READY | Captura sem pedir nada |
   | `· site autorizado para o PDD` | READY | Captura |
   | `· captura PDD não autorizada` | CONSENT_REQUIRED | Mostra *"Esta página permite gravação, mas a captura de PDD ainda não está autorizada. Site: <origem>"*, a escolha **Imagem do PDD neste site** (*Print integral (padrão)* ou *Print mascarado*), o botão **Autorizar captura PDD neste site** e a caixa **Lembrar este site** (sem marcar: só até fechar o navegador) |
   | `· sem acesso do Chrome` | CHROME_ACCESS_REQUIRED | Explica como conceder no Chrome. Não há botão: o Studio não concede |
   | `· em gravação` | RECORDING | Pede para finalizar a gravação |
   | `· captura indisponível` / `· benchmark (extensão antiga)` | UPDATE_EXTENSION | Avisa que a captura em site exige a extensão 0.6.0 |

   Quando nenhuma aba pode capturar, uma dica explica por quê, só com contagens: sem autorização PDD, sem acesso do
   Chrome, em gravação, bloqueadas pelo navegador. Nunca mostra endereço ou título.
3. Use **Capturar tela…** e depois o atalho **Alt+Shift+K** no Chrome. A imagem é o **print real da tela**
   (`nekkora-visual/4`, decisão D-S013-08). Os campos, os textos de dados, as tabelas, os frames, o canvas, o vídeo e as
   imagens de conteúdo saem cobertos por faixas lisas. Ficam como na página: a aparência, o logotipo, os ícones e os
   rótulos de interface do vocabulário. A v3 inteiramente sintética continua no contrato.

   **Duas opções por site** (D-S013-09 e D-S013-10). O **print integral** (`nekkora-visual/5`) é o **padrão**: mostra a
   tela como está, com os dados; só os campos de senha saem cobertos, sempre. O print mascarado é a outra opção.
   - **Autorizar:** "Print integral (padrão)" já vem escolhido e não há segundo clique; o texto da autorização avisa que
     os dados vão para o PDF. Para o mascarado, escolha *Print mascarado* antes de autorizar.
   - **Trocar o nível:** **Voltar ao print mascarado neste site** e **Liberar print integral neste site**, sem
     confirmação. O laboratório local também captura no integral por padrão.
   - **Num site integral:** **Mascarar esta captura** faz só aquela captura sair mascarada. A imagem v5 mostra o selo
     *"Print integral: dados reais da tela"*.
   **Pelo Recorder** (D-S013-10). Depois de **Finalizar**, o resumo da gravação mostra **Capturar tela**:
   - **O que faz:** a imagem vai para o bloco da última etapa desta gravação, na aba em que se gravou, com o mesmo gesto
     Alt+Shift+K. O Recorder diz o que aconteceu.
   - **Site sem autorização do PDD:** a janela do PDD abre no bloco certo para autorizar.
   - **Janela do PDD encoberta:** o Recorder fica acima das outras janelas e pode cobrir a do PDD; arraste-o.
4. Revise como antes: candidata, Aplicar/Cancelar, **Selecionar área** e **Posicionar marcador**. Os números dos
   marcadores nunca ficam um em cima do outro, nem num recorte justo (`MarkerLayout`, a mesma regra no editor e no PDF).
   **Sem recorte escolhido**, o print real (integral ou mascarado) vai ao PDF com a tela inteira, como o editor mostra.
   O recorte automático em volta dos campos ficou só para as imagens sintéticas e as anteriores.
5. Exporte o PDF. Na v4, o texto de procedência declara que as imagens são capturas reais com os dados cobertos e pede
   revisão antes de compartilhar. Com alguma imagem em print integral, o Studio pede confirmação antes (*"Exportar com
   print integral?"*; Cancelar não gera PDF). O PDF declara o print integral na procedência e numa nota em cada bloco.
6. Para revogar, use **Sites autorizados para o PDD** → **Revogar captura PDD**. Uma captura armada ou em curso nesse site é
   cancelada pela extensão.

## Peças

| Peça | Papel |
|---|---|
| `PddConsentClient` (recorder) | Comandos `studio.pdd.consent.status/grant/revoke/list`. Só aceita origem `esquema://host[:porta]`, escopo `SESSION`/`REMEMBERED`, ator `DEVELOPER`/`TEST_AGENT` e nível `MASKED`/`FULL` (`nekkora-pdd-consent/2`). Nível ausente ou desconhecido vindo da extensão vale `MASKED` |
| `BrowserTab` | Campos novos `origin` e `pddConsent`. Origem fora da forma é descartada; perfil desconhecido vira `null` (fail-closed) |
| `PddCaptureClient` | `supportsV3` (0.6.0+). `arm(..., policy, origin)`. Resultado v3 sem imagem; uma v3 com `png` é recusada |
| `SyntheticRenderer` (pdd) | Valida a descrição (mesma regra do host, com `CapturePrivacy.interfaceText`) e desenha em escala 2 (1 acima de 2048 px CSS), com Inter e paleta fechada; texto em tons de cinza |
| `VisualVerifier.verifyV3` | 4ª barreira, na recepção e antes de cada exportação: tipos, ordem, tetos, geometria em múltiplos da escala, barras quantizadas, rótulos do vocabulário e disjuntos, repintura exata fora dos rótulos, mistura fundo↔tinta dentro deles, sha-256 |
| `DocumentDraft`/`DraftJson` | Política `nekkora-visual/3`, regiões `TEXT`/`LABEL` e rascunho **1.2.0** só quando há v3. Versão e política precisam ser coerentes |
| `VisualVerifier.verifyV4` | 4ª barreira da v4: máscaras BLOCK/FIELD/TEXT na ordem, nos tetos e dentro da imagem, e cada pixel DENTRO delas exato. Fora delas os pixels são da tela (limite declarado) |
| `VisualVerifier.verifyV5` | 4ª barreira do print integral: só máscaras `FIELD` (senha) e `BLOCK` (frame), com as mesmas conferências da v4; `TEXT` é recusado |
| `BridgeFrames`/`BridgeServer` | Exceção única ao teto de 256 KiB: só o evento do print v4 ou v5, até 12 MiB, depois do handshake autenticado. Qualquer outro quadro grande derruba a conexão |
| `MarkerLayout` (domínio) | Posição dos números dos marcadores, a mesma no editor (`PddImageEditor`) e no PDF: em volta do contorno ou dentro dele, inteira na área visível (o recorte), sem encostar em outro número; sem lugar, o ponto livre mais perto |
| `RecorderController.lastRecording`, `FloatingRecorderPanel`, `PddPresenter.captureAfterRecording` | "Capturar tela" no resumo do Recorder: bloco da última etapa da gravação (`RECORDING_ID`), rascunho atualizado antes, mensagens no Recorder |
| `PdfRenderer` | Textos de procedência `PROVENANCE_V3`, `PROVENANCE_V4` e `PROVENANCE_V5` (com a frase da mistura quando há imagens mascaradas); nota `FULL_PRINT_NOTE` em cada bloco integral; recorte automático considera `LABEL` |
| `PddPresenter`/`PddPanel` | Estados das abas, autorização com o nível (integral por padrão), troca do nível, "Mascarar esta captura", selo do print integral, confirmação de exportar pelo `Host.confirm` (na janela real, `NekkoraConfirmationDialog.confirmDanger`; sem janela que pergunte, nada é confirmado), revogação, lista de sites com o nível, dica de indisponibilidade, mensagens por motivo |

O ator do registro vem de `StudioEnvironment.decisionActor()`: `TEST_AGENT` só em modo de laboratório explícito. A lista
de sites mostra quem decidiu. Os consentimentos ficam na extensão e nunca no `.nekkora`. Revisar a imagem também não muda
a automação.

## Compatibilidade

- **Extensão 0.6.0:** o Studio pede a v5 (print integral) num site com o nível `FULL` e no laboratório; a v4 (print
  mascarado) num site com o nível `MASKED` ou com "Mascarar esta captura". Uma descrição v3 que chegue continua aceita.
- **Extensão 0.5.x:** o Studio pede a v2, só no laboratório. Sites aparecem como indisponíveis e nenhum comando novo é
  enviado.
- **Extensões anteriores:** v1.
- **Rascunhos:** 1.0.0 e 1.1.0 continuam legíveis e exportáveis. Um Studio anterior recusa um rascunho 1.2.0 em vez de
  lê-lo errado.
