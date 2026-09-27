package com.nekkora.showcase.pages;

import com.nekkora.showcase.kit.Fixed;
import com.nekkora.showcase.kit.Page;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.feedback.NekkoraToast;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.search.NekkoraGlobalSearch;
import com.nekkora.ui.search.NekkoraSearchResult;
import com.nekkora.ui.search.SearchItem;
import com.nekkora.ui.workflow.NekkoraStepper;
import com.nekkora.ui.workflow.NekkoraTimeline;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

/** Fluxo (etapas, linha do tempo) e busca. */
public final class WorkflowSearchPages {
    private WorkflowSearchPages() {}

    public static JComponent workflow() {
        Page p = new Page("Fluxo e histórico", "Etapas de um processo e linha do tempo de eventos.");
        NekkoraStepper h = new NekkoraStepper("Origem", "Mapeamento", "Validação", "Publicação");
        h.setCurrent(2);
        NekkoraStepper err = new NekkoraStepper("Preparar", "Executar", "Conferir", "Concluir");
        err.setCurrent(1);
        err.setState(1, NekkoraStepper.StepState.ERROR);
        NekkoraStepper v = new NekkoraStepper("Dados de entrada", "Regras", "Revisão").withOrientation(NekkoraStepper.Orientation.VERTICAL);
        v.setDescription(0, "3 arquivos carregados");
        v.setDescription(1, "Em edição");
        v.setCurrent(1);
        p.section("Etapas", "Concluída, atual, futura e erro — com número/ícone, não só cor.",
                Page.row(new Fixed(Page.column(h, err), 520, 0), v));
        NekkoraTimeline t = new NekkoraTimeline()
                .addItem("14:32", "Execução concluída", "1.280 itens processados em 3 min 12 s.", Status.SUCCESS)
                .addItem("14:29", "Etapa com atenção", "Validação demorou mais que o esperado.", Status.WARNING)
                .addItem("14:28", "Execução iniciada", null, Status.RUNNING)
                .addItem("09:10", "Projeto publicado", "Versão 1.4 por Ana.", Status.INFO);
        p.section("Linha do tempo", null, new Fixed(t, 460, 0));
        return p;
    }

    private static final List<SearchItem> ITEMS = List.of(
            new SearchItem("Conciliação bancária", "Workspace / Financeiro", "Projeto", IconName.WORKFLOW, null),
            new SearchItem("Emissão de notas", "Workspace / Financeiro", "Projeto", IconName.WORKFLOW, null),
            new SearchItem("parametros.json", "C:\\Nekkora\\config\\parametros.json", "Arquivo", IconName.BRACES, null),
            new SearchItem("Configurações de conta", "Preferências", "Página", IconName.SETTINGS, null),
            new SearchItem("Leitura de e-mails", "Workspace / Operações", "Projeto", IconName.MAIL, null),
            new SearchItem("Relatório semanal", "Workspace / Operações", "Projeto", IconName.CHART, null));

    public static List<SearchItem> search(String q) {
        String f = fold(q);
        return ITEMS.stream().filter(i -> fold(i.title() + " " + i.subtitle()).contains(f)).toList();
    }

    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public static JComponent search() {
        Page p = new Page("Busca", "Busca global com resultados flutuantes: ↑/↓ navegam, Enter abre, Esc fecha. "
                + "A mesma base visual servirá à Command Palette (Ctrl+K) na V2.");
        NekkoraGlobalSearch gs = new NekkoraGlobalSearch("Buscar projetos, arquivos e páginas…  (tente \"con\")");
        gs.setProvider(WorkflowSearchPages::search);
        gs.onOpen(item -> NekkoraToast.show(gs, Severity.INFO, "Abrir: " + item.title(), item.subtitle()));
        p.section("Busca global", null, new Fixed(gs, 480, 0));
        p.section("Resultado", "Trecho encontrado em destaque (ignora acentos), contexto truncado no meio e tipo.",
                Page.column(new Fixed(new NekkoraSearchResult(ITEMS.get(0), "conc"), 480, 0),
                        new Fixed(new NekkoraSearchResult(ITEMS.get(2), "param"), 480, 0)));
        return p;
    }
}
