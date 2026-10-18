# Evidências da NEKKORA-E2E-001 (sanitizadas)

Nenhum valor da planilha (varredura por palavra inteira dos 65 valores distintos, inclusive dentro do `.nekkora`),
nenhum PNG bruto, caminhos locais trocados por `<trabalho>`, `<temp>` e `<repos>`.

| Pasta | Conteúdo |
|---|---|
| `g1/` | download real e leitura tabular: hash, tamanho, abas, cabeçalhos, contagem, tipos, bloqueios de rede |
| `g2-g5/` | log da homologação real (56/56); PDD (`pdd-visual-v001.pdf` + manifesto, texto extraído, 2 imagens JÁ sanitizadas pelo Studio); plano e registro do debug guiado; relatório das 4 gerações pelo menu; validação do código gerado pela GUI (antes da correção do gerador) |
| `projeto/` | `rpa-challenge.nekkora` salvo pela homologação e a configuração de geração (`rpa-challenge.codegen.json`) |
| `g5-codigo-gerado/` | projeto Python gerado pelo menu do Studio (usado nas tentativas `depuracao-1` e `oficial`) |
| `g5-codigo-final/` | mesmo modelo, regenerado depois da correção do gerador; `DIFF-gerador-anterior.txt`; `G5-validacao.json` (venv limpa, contratos, ruff, mypy, saídas da execução) |
| `g6/<tentativa>/` | tentativas no site real: eventos JSONL, checkpoint, resumo, observações (placar lido da página), contagem de resultados, stdout/stderr |
| `g6-laboratorio/` | variante `lab` na réplica: resumo com o oráculo do servidor, eventos, resumo da execução, configuração de geração usada |
