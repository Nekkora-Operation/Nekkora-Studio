# NEKKORA-STUDIO-013 — resultados (Studio)

Execuções de 19/10/2026, com dados 100% sintéticos. Nenhum ambiente ou dado de cliente foi usado.

## Suítes

| Suíte | Resultado |
|---|---|
| Windows (`./mvnw test`, MS OpenJDK 21.0.12) | **649/649**. Base `main`: 624. Novos: `PddVisualV3Test` 7, `PddSitesUiTest` 9 (1 com evento v4 real; 2 do print integral: confirmações, nível, "Mascarar esta captura", selo, exportar), `PddSitesClientTest` 4 (1 do nível), `PddVisualV4Test` 2, `PddVisualV5Test` 2, `BridgeServerTest` +1 (quadro grande só para o print v4/v5) |
| Linux (`tools/golden-linux.sh verify`, Temurin fixado por digest) | **648/648 + 1 ignorado** (649 no total; o ignorado é o mesmo das missões anteriores, só para Windows), Temurin 21.0.12.1 linux/amd64, sobre o commit 36156ac do Studio e o 8c1ba4b da extensão |
| Contrato do Bridge | Schema e fixtures `s013-*` idênticos aos da extensão (20 válidas, 23 inválidas) |

## Homologação na janela real (`Studio013Homologation`)

A homologação usou o Studio de verdade com dados, Bridge e Studio isolados, o Chrome for Testing 155 com a extensão 0.6.0
desta branch, o Native Host do script oficial, o mouse e o teclado do sistema (Robot) e o gesto Alt+Shift+K real. A
página foi o "sistema real" sintético `studio013/sistema.html`, servido como `site-a.nekkora.test`. Nessa página, todo
dado (nomes, CPFs, tabela, mensagens, campos) está na cor-sentinela #E6007E, e a prova principal da v4 é **nenhum pixel
dessa cor** no print que chega ao Studio, no rascunho e no PDF. Operador: Claude como
agente de teste (TEST_AGENT).

**Resultado: 81/81 verificações; em 150%, 5/5.** Log completo: `evidence/S013-homologacao-log.txt`. (Na revisão do
print integral a página ganhou três sentinelas de senha; as 6 verificações a mais conferem que nenhuma delas chega ao
rascunho nem ao PDF.)

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

**Resultado: 55/55 verificações; em 150%, 13/13.** Log: `evidence/v5/S013-homologacao-log.txt`.

| Etapa | O que foi provado |
|---|---|
| B1 | Gravação no site (preencher, preencher, clicar) sem valor digitado no modelo |
| B2 | A escolha da imagem aparece ao autorizar, com o print mascarado como padrão; "Print integral" escolhido pelo teclado; confirmação real com o site e o aviso (dados no PDF, só senhas cobertas); **Cancelar não autoriza nada**; "Liberar print integral" autoriza (sessão, agente de teste); "Mascarar esta captura" e "Voltar ao print mascarado" inteiros dentro da janela |
| B3 | Captura v5 com o gesto real: só máscaras FIELD (3) e BLOCK; **5.495 px de dados** (cor-sentinela) no print e **nenhum** px de senha; selo "Print integral"; metadados do rascunho sem nenhuma das 23 sentinelas |
| B4 | "Mascarar esta captura": candidata v4 sem cor-sentinela e sem o selo; Cancelar mantém a imagem integral |
| B5 | Exportar pede confirmação; **Cancelar = nenhum PDF**; "Exportar mesmo assim" gera o PDF com a procedência do print integral e a nota no bloco; texto do PDF sem valores digitados; as páginas renderizadas trazem os dados e nenhuma senha |
| B6 | "Voltar ao print mascarado neste site" sem confirmação; a próxima captura no site é v4 sem cor-sentinela |
| B7 | Rascunho, logs do host e observações do log sem valores digitados nem dados do sistema; print salvo e páginas do PDF sem a sentinela de senha; `.nekkora` nunca mudou |
| E2 (150%) | Escolha, confirmação e autorização; "Capturar", "Mascarar esta captura" e "Voltar ao print mascarado" inteiros na janela; captura v5 e selo visíveis |

Evidências em `evidence/v5/`: escolha e confirmação do print integral, janela com o selo, o print integral salvo
(página **sintética**, com dados fictícios à mostra e senhas cobertas), a confirmação da exportação, o PDF e o texto
extraído.

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
| S18 | Liberar o print integral sem confirmação | morta |
| S19 | Exportar print integral sem confirmação | morta |
| S20 | Nível desconhecido vindo da extensão vale como FULL | morta |

**20/20 mortas.** A S11 também foi repetida com a âncora nova (o `grant` passou a conferir o nível).

## Defeito achado na homologação

Na janela do PDD, os rótulos longos da escolha de aba e da lista de sites empurravam "Capturar" e "Revogar captura PDD"
para fora da coluna; o clique real não alcançava o botão.

- **Correção:** as escolhas têm largura máxima e uma dica com o texto inteiro, e os botões ficam em linha própria.
- **Verificação:** a homologação passou a conferir que esses controles aparecem inteiros dentro da janela.
