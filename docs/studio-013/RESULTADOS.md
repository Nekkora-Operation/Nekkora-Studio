# NEKKORA-STUDIO-013 — resultados (Studio)

Execuções de 19/10/2026, com dados 100% sintéticos. Nenhum ambiente ou dado de cliente foi usado.

## Suítes

| Suíte | Resultado |
|---|---|
| Windows (`./mvnw test`, MS OpenJDK 21.0.12) | **655/655**. Base `main`: 624. Novos: `PddVisualV3Test` 7, `PddSitesUiTest` 10 (1 com evento v4 real; print integral como padrão, "Mascarar esta captura", selo, exportar; "Capturar tela" pelo Recorder), `PddSitesClientTest` 4 (1 do nível), `PddVisualV4Test` 2, `PddVisualV5Test` 2, `BridgeServerTest` +1 (quadro grande só para o print v4/v5), `MarkerLayoutTest` 4 e `PddImageEditorMarkersTest` 1 (números dos marcadores); `RecorderStepsStudioTest` passou a conferir o botão "Capturar tela" |
| Linux (`tools/golden-linux.sh verify`, Temurin fixado por digest) | **654/654 + 1 ignorado** (655 no total; o ignorado é o mesmo das missões anteriores, só para Windows), Temurin 21.0.12.1 linux/amd64, sobre o commit 2b7d985 do Studio e o f82d58f da extensão |
| Contrato do Bridge | Schema e fixtures `s013-*` idênticos aos da extensão (20 válidas, 23 inválidas) |

## Homologação na janela real (`Studio013Homologation`)

A homologação usou o Studio de verdade com dados, Bridge e Studio isolados, o Chrome for Testing 155 com a extensão 0.6.0
desta branch, o Native Host do script oficial, o mouse e o teclado do sistema (Robot) e o gesto Alt+Shift+K real. A
página foi o "sistema real" sintético `studio013/sistema.html`, servido como `site-a.nekkora.test`. Nessa página, todo
dado (nomes, CPFs, tabela, mensagens, campos) está na cor-sentinela #E6007E, e a prova principal da v4 é **nenhum pixel
dessa cor** no print que chega ao Studio, no rascunho e no PDF. Operador: Claude como
agente de teste (TEST_AGENT).

**Resultado: 83/83 verificações; em 150%, 5/5.** Log completo: `evidence/S013-homologacao-log.txt`.
- **Sentinelas de senha:** na revisão do print integral, a página ganhou três sentinelas de senha; 6 verificações
  conferem que nenhuma chega ao rascunho nem ao PDF.
- **Integral como padrão:** com o integral virando padrão (D-S013-10), este roteiro escolhe "Print mascarado" pelo
  teclado antes de autorizar (A3), e no laboratório usa "Mascarar esta captura" (A11).
- **Higiene (A12):** uma evidência só conta como suja a partir de 5 px da cor-sentinela. Dado vazado é texto, com dezenas
  ou centenas de px. As capturas da janela mostraram 1 px isolado, no mesmo ponto em todas: ampliado, é a borda
  antisserrilhada do número rosa do marcador (#C2466D) na prévia do PDF, que agora pode ficar acima do campo, sobre o
  rótulo "Senha".

| Etapa | O que foi provado |
|---|---|
| A1 | Gravação no site autorizado pelo Chrome (preencher, preencher, clicar), sem valor digitado no modelo |
| A2 | "Esta página permite gravação, mas a captura de PDD ainda não está autorizada", com a origem exata e sem caminho. "Capturar" fica indisponível |
| A3 | "Autorizar captura PDD neste site" com clique real: só nesta sessão, registrado como agente de teste. Botões e lista inteiros dentro da janela |
| A4 | Captura **v4** (print real) com gesto real. Rascunho 1.2.0. Só máscaras BLOCK/FIELD/TEXT. Campos (inclusive de shadow roots aberta e fechada), tabela, frame, canvas, vídeo, fundo, pseudo-elemento e linhas de texto de dados cobertos. **Nenhum pixel da cor-sentinela**. Rascunho sem nenhuma das 20 sentinelas |
| A5 | Recaptura vira candidata; recorte pelo mouse; Aplicar |
| A6 | Marcadores automáticos 1/2/3 comprovados (o manual foi dispensado) e a automação intocada |
| A7 | O PDF declara o print real com os dados cobertos e pede revisão antes de compartilhar. Nenhuma sentinela no texto nem cor-sentinela nas páginas renderizadas |
| A8 | Revogar pela lista deixa a captura indisponível. "Lembrar este site" registra o site como lembrado |
| A9 | Revogar com a captura armada cancela a captura |
| A10 | Acesso do Chrome revogado: a autorização PDD some. Devolver o acesso não a devolve |
| A11 | Laboratório local captura sem autorização (candidata v4, sem cor-sentinela) |
| A12 | 4 temas. Rascunho, logs do host e observações do log sem sentinelas. Nenhuma evidência PNG com a cor-sentinela. `.nekkora` nunca mudou |

Evidências em `evidence/`:

- **Janela do PDD:** A2, A3, A4, A6 e temas.
- **Print v4 mascarado (página sintética):** `S013-A4-print-mascarado-site.png` e a janela `S013-A4-captura-v4.png`.
- **PDF:** `S013-pdd-visual-v001.pdf` e a página renderizada.
- **Texto extraído do PDF.**
- **150%:** em `evidence/150/`.

As imagens são da janela do Studio ou o print v4 da página SINTÉTICA, com os dados cobertos. O rótulo do passo A0 no log
ainda diz "nekkora-visual/3" (texto fixo do roteiro, anterior à v4); a política verificada no A4 é a v4.

## Homologação do print integral na janela real (`Studio013V5Homologation`)

Mesma infraestrutura, com o "sistema real" sintético: todo dado na cor-sentinela #E6007E e toda senha na cor #00B000
(senha, "mostrar senha" com `type=text` e senha dentro da shadow root fechada). Operador: Claude como agente de teste;
as duas confirmações foram respondidas por clique real do mouse do sistema.

**Resultado: 58/58 verificações; em 150%, 12/12** (rodada depois de D-S013-10: integral como padrão, "Capturar tela"
pelo Recorder e números dos marcadores). Log: `evidence/v5/S013-homologacao-log.txt`.

| Etapa | O que foi provado |
|---|---|
| B1 | Gravação no site (preencher, preencher, clicar) sem valor digitado no modelo |
| B1b | **Recorder:** o resumo da gravação concluída oferece "Capturar tela"; com o site ainda sem autorização do PDD, o Recorder avisa e a janela do PDD abre no bloco das etapas desta gravação |
| B2 | A escolha da imagem aparece ao autorizar com o **print integral como padrão** e o aviso de uma linha; **autorizar não pede segundo clique** (nenhuma janela de confirmação); site no integral (sessão, agente de teste); "Mascarar esta captura" e "Voltar ao print mascarado" inteiros dentro da janela |
| B3 | **"Capturar tela" pelo Recorder** com o gesto real: o Recorder mostra "Captura armada" e depois "Imagem associada"; a imagem vai para o bloco das etapas desta gravação. Captura v5: só máscaras FIELD (3) e BLOCK; **5.495 px de dados** (cor-sentinela) no print e **nenhum** px de senha; selo "Print integral"; metadados do rascunho sem nenhuma das 23 sentinelas |
| B3b | **Recorte justo** nos campos de login (Usuário, Senha, Entrar) pelo mouse: os números dos marcadores ficam separados no editor (defeito relatado pela usuária) |
| B4 | "Mascarar esta captura": candidata v4 sem cor-sentinela e sem o selo; Cancelar mantém a imagem integral |
| B5 | Exportar pede confirmação; **Cancelar = nenhum PDF**; "Exportar mesmo assim" gera o PDF com a procedência do print integral e a nota no bloco; texto do PDF sem valores digitados; as páginas renderizadas trazem os dados e nenhuma senha |
| B6 | "Voltar ao print mascarado neste site" sem confirmação; a próxima captura no site é v4 sem cor-sentinela |
| B7 | Rascunho, logs do host e observações do log sem valores digitados nem dados do sistema; print salvo e páginas do PDF sem a sentinela de senha; `.nekkora` nunca mudou |
| E2 (150%) | Escolha (integral como padrão) e autorização; "Capturar", "Mascarar esta captura" e "Voltar ao print mascarado" inteiros na janela; captura v5 e selo visíveis |

Evidências em `evidence/v5/`: escolha e confirmação do print integral, janela com o selo, o print integral salvo
(página **sintética**, com dados fictícios à mostra e senhas cobertas), a confirmação da exportação, o PDF e o texto
extraído.

**Defeitos relatados pela usuária no teste e corrigidos (D-S013-10):** os números dos marcadores ficavam um em cima do
outro depois de recortar, no editor e no PDF. Agora há uma regra única de posição (`MarkerLayout`).

**Observação de uso.** O Recorder fica acima das outras janelas. Quando ele abre a janela do PDD para autorizar o site,
pode cobrir os controles dela. No roteiro, o Recorder é arrastado para o canto, como a pessoa faria.

**Defeitos achados nesta homologação e corrigidos:**
- "Lembrar este site" ficava cortado ao lado de "Autorizar" (235 de 396 px visíveis): agora fica em linha própria.
- O título da confirmação cortava a origem: a origem foi para o texto.
- A nota da imagem v5 não cabia: ficou curta.
- O banner dizia "representação sintética" e "0 rótulo(s)" para os prints reais: agora diz "print mascarado" ou
  "print integral".

## Teste de mutação

| id | Mutação | Resultado |
|---|---|---|
| S1 | 4ª barreira aceita rótulo fora do vocabulário | morta |
| S2 | Mistura fundo↔tinta sempre aceita | morta |
| S3 | Repintura exata fora dos rótulos desligada | morta |
| S4 | O desenho aceita descrição com rótulo fora do vocabulário | morta |
| S5 | Barras TEXT sem quantização | morta |
| S6 | v3 aceita portador de pixels | morta |
| S7 | Rascunho 1.1.0 aceita v3 | morta (sobreviveu na 1ª rodada; o caso que só essa regra pega foi acrescentado) |
| S8 | O Studio aceita origem com caminho | morta |
| S9 | Aba sem consentimento oferecida como pronta | morta |
| S10 | O Studio aceita v3 com imagem | morta |
| S11 | O Studio manda origem com caminho no consentimento | morta |
| S12 | v3 não reverificada antes de exportar | morta |
| S13 | O Studio deixa de conferir as máscaras da v4 | morta |
| S14 | O Studio aceita quadro grande de qualquer política | morta (âncora atualizada para v4/v5 e repetida) |
| S15 | v5 aceita máscara de texto | morta |
| S16 | Site no print mascarado arma a v5 | morta |
| S17 | "Mascarar esta captura" ignorado | morta |
| S19 | Exportar print integral sem confirmação | morta |
| S20 | Nível desconhecido vindo da extensão vale como FULL | morta |
| S21 | Números dos marcadores sem desvio (sempre na posição preferida) | morta |
| S22 | O editor volta à regra antiga (número sempre à esquerda) | morta |
| S23 | Autorizar sem escolha volta ao mascarado | morta (SOBREVIVEU na 1ª rodada: a janela sempre passa a escolha; o teste passou a chamar a autorização sem nível) |
| S24 | Recorder: etapas de qualquer gravação contam como desta | morta |
| S25 | Recorder: arma a captura em site sem autorização do PDD | morta |
| S26 | Recorder: "Capturar tela" também depois de cancelar | morta |
| S27 | A lista da janela volta a abrir no mascarado | morta |

**26/26 mortas.** A S18 (liberar o integral sem confirmação) saiu: a confirmação ao liberar foi retirada por decisão da
usuária (D-S013-10). As S11, S14, S16 e S17 foram repetidas com as âncoras novas.

## Defeito achado na homologação

Na janela do PDD, os rótulos longos da escolha de aba e da lista de sites empurravam "Capturar" e "Revogar captura PDD"
para fora da coluna; o clique real não alcançava o botão.

- **Correção:** as escolhas têm largura máxima e uma dica com o texto inteiro, e os botões ficam em linha própria.
- **Verificação:** a homologação passou a conferir que esses controles aparecem inteiros dentro da janela.
