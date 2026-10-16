# STUDIO-006 — Desfazer/refazer e persistência

## Decisão (§15, D-S006-09)

O arquivo guarda o **estado atual confirmado**; o histórico de operações não é persistido.

- `Automation.Snapshot` (STUDIO-004) guarda referências aos próprios objetos e pertence ao desfazer/refazer: **não** é o
  formato. Teste: `ProjectValidationTest` — "Automation.Snapshot (desfazer/refazer) não é o formato: o documento é
  próprio e não contém histórico".
- Ao abrir: desfazer e refazer vazios (`EditHistory.clear`). Depois de uma nova edição, voltam a funcionar
  (S006-035/036). Homologação: reaberto com histórico vazio; Ctrl+Z / Ctrl+Y reais desfizeram e refizeram a edição
  seguinte.
- Ao recuperar uma fotografia: histórico também vazio.

## Procedência e revisões não dependem do histórico

As revisões manuais ficam no próprio estado da etapa (`effective.fieldEdits`, `lastManualChange`) e o original em
`original.baseline`. Por isso "Restaurar original" e a aba "Origem e alterações" funcionam depois de abrir, sem
histórico (S006-034).

## Revisão × geração

| Contador | Quem muda | Para quê |
|---|---|---|
| `Automation.revision()` (domínio, persistida) | operações de edição | desfazer/refazer e detecção de mudança no domínio |
| `ProjectState.generation` (aplicação, não persistida) | edição, etapa gravada recebida, **evidência recebida**, desfazer, refazer | saber se o arquivo está atualizado |

Evidências continuam fora do desfazer (STUDIO-005), mas tornam o projeto não salvo. Desfazer até o estado do arquivo
não marca o projeto como salvo: o Studio não compara conteúdo, só gerações (comportamento conservador, documentado em
[known-limitations.md](known-limitations.md)).
