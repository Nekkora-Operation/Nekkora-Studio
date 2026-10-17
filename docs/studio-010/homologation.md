# STUDIO-010 — Homologação real (G9)

**Ambiente:**
- Windows 11 Pro (1920×1032 úteis, escala 100%), Java 21.0.12.
- Chrome for Testing 155.0.8059.39 com a extensão da branch `feat/nekkora-studio-010-safe-capture` (cópia de homologação só com o site de teste `site-a.nekkora.test` acrescentado).
- Native Host instalado pelo script oficial.
- Laboratório `test-lab/pages/studio010` (dados fictícios; todo texto sensível na cor-sentinela #E6007E).
- Studio com diretório de dados isolado.
- Mouse e teclado do sistema (Robot), com o primeiro plano conferido antes de cada ação.

**Operador:** Claude como **agente de teste** (`TEST_AGENT`). Máquina liberada pela usuária, com "Não incomodar".

Roteiro: `nekkora-studio-app/src/test/java/com/nekkora/studio/app/evidence/Studio010Homologation.java`.

**Resultado: 55/55** ([log](evidence/g9/S010-homologacao-log.txt)).

| Passo | O que foi feito | Verificações |
|---|---|---|
| R1 | Recorder grava a Autenticação (Usuário, Senha, Entrar) e a Consulta (Código, Consultar); a etapa "Observar o resultado" é declarada manualmente | etapas certas; nenhum valor no modelo |
| P1 | PDD aberto pelo menu real (Automação → Gerar documentação → PDD visual…) | 3 seções, 1 bloco por tela; "Autenticação" fora do vocabulário vai para revisão e bloqueia a exportação |
| P2 | Revisão de títulos pelo agente de teste, desfazer, refazer, título com valor | liberação da exportação; desfazer/refazer; recusa pela política de texto |
| P3 | Três capturas reais pelo gesto Alt+Shift+K (login com campos preenchidos, consulta com código, resultado com a tabela de negócio) | três imagens diferentes aceitas pela 4ª barreira; 0 pixel-sentinela; login com 3/3 marcadores |
| P4 | Aba de site autorizado fora do laboratório; atalho pressionado em outra aba; cancelar captura armada | aba nunca oferecida; `TAB_MISMATCH` com a imagem anterior mantida; cancelamento sem captura |
| P5 | Exportação duas vezes pelo diálogo de pasta real | v001 e v002 lado a lado, v001 byte a byte intacto; automação inalterada |
| P6 | Inspeção do PDF (PDFBox) | ordem das seções, 9 textos esperados, 13 sentinelas ausentes, metadados controlados, 3 imagens, páginas renderizadas sem cor-sentinela, manifesto sem textos |
| P7 | Etapa acrescentada à automação | aviso + exportação bloqueada; depois de atualizar, "PDF desatualizado"; revisões e imagem dos blocos inalterados mantidas |
| P8 | 4 temas; janela no tamanho de uma tela 1366×768 | capturas da janela; Exportar, Desfazer e Capturar visíveis dentro da janela |
| P9 | Privacidade | arquivos do rascunho só com nomes técnicos; rascunho, manifesto, log da homologação e logs do host sem valores nem conteúdo de negócio; **todas** as PNG da rodada sem cor-sentinela |

## Evidências ([evidence/g9](evidence/g9))

- `S010-imagem-sanitizada-{1,2,3}.png`: as imagens exatamente como guardadas pelo Studio (já sanitizadas).
- `pdd-visual-v001.pdf`, `pdd-visual-v002.pdf` + manifestos; `S010-PDF-pagina-1.png` (renderização); `S010-pdf-texto-extraido.txt`.
- `S010-rascunho.json`.
- Capturas da janela: `S010-P1…P8`, `S010-R1-mapeamento.png` (só a janela do Studio, à frente).

Integração anterior (`Studio010SafeCaptureIT`, extensão, 6/6): imagens sanitizadas em
`reports/evidence/S010-G4-01-login-sanitizado.png`, `S010-G4-02-sentinelas-sanitizado.png` e
`S010-G5-01-login-150-sanitizado.png` (repositório da extensão).

## Achados durante a homologação

| Achado | Natureza | Tratamento |
|---|---|---|
| Painel do Recorder fica em "Gravação concluída" até "Voltar ao Studio" | roteiro | o roteiro clica em "Voltar ao Studio" (comportamento do produto já conhecido) |
| "Capturar tela…" clicado enquanto o Chrome estava à frente da janela do PDD | roteiro | clique real na lista antes de armar |
| Captura da janela do PDD com pixels do Chrome que estava à frente; borda invisível da janela do Windows 11 com uma faixa do que estava atrás | **evidência** (privacidade; só no diretório temporário, nunca em commit) | janela trazida à frente antes de cada captura; recorte da área visível; varredura de todas as PNG com o detector (P9) |
| Nomes de verificação com o texto das sentinelas reprovavam a varredura do log | roteiro | sentinelas numeradas no log |
| Bloco do Resultado sem marcador | esperado | etapa declarada, sem alvo: `NO_TARGET` → "marcador indisponível" |
| Depois de acrescentar uma etapa ao Resultado, o bloco perde a imagem | esperado (conservador) | o bloco mudou; a imagem antiga não é reaproveitada (D-S010-04); é preciso capturar de novo |
