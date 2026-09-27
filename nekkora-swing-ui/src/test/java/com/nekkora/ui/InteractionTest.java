package com.nekkora.ui;

import static com.nekkora.ui.TestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraCard;
import com.nekkora.ui.container.NekkoraCollapsiblePanel;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.data.NekkoraStatusBadge;
import com.nekkora.ui.data.NekkoraTable;
import com.nekkora.ui.data.NekkoraTag;
import com.nekkora.ui.data.NekkoraTree;
import com.nekkora.ui.data.NekkoraTreeNode;
import com.nekkora.ui.input.NekkoraCheckBox;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraRadioButton;
import com.nekkora.ui.input.NekkoraSearchField;
import com.nekkora.ui.input.NekkoraSwitch;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.navigation.NekkoraSidebar;
import com.nekkora.ui.navigation.NekkoraSidebarGroup;
import com.nekkora.ui.navigation.NekkoraSidebarItem;
import com.nekkora.ui.navigation.NekkoraTabs;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.studio.NekkoraWorkArea;
import com.nekkora.ui.workflow.NekkoraStepper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Eventos, teclado e binding dos componentes. */
class InteractionTest {

    @BeforeAll
    static void theme() {
        TestSupport.theme(NekkoraThemes.SAKURA_DARK);
    }

    @Test
    void botaoDisparaAcaoPorCliqueEspacoEEnter() {
        onEdt(() -> {
            AtomicInteger clicks = new AtomicInteger();
            NekkoraButton b = NekkoraButton.primary("Salvar").onClick(e -> clicks.incrementAndGet());
            b.doClick(0);
            assertTrue(Keys.press(b, "ENTER"));
            Keys.press(b, "pressed SPACE");
            Keys.press(b, "released SPACE");
            assertEquals(3, clicks.get());
        });
    }

    @Test
    void botaoDesabilitadoOuCarregandoNaoDispara() {
        onEdt(() -> {
            AtomicInteger clicks = new AtomicInteger();
            NekkoraButton b = NekkoraButton.danger("Excluir").onClick(e -> clicks.incrementAndGet());
            b.setEnabled(false);
            b.doClick(0);
            Keys.press(b, "ENTER");
            b.setEnabled(true);
            b.setLoading(true);
            b.doClick(0);
            assertEquals(0, clicks.get());
            b.setLoading(false);
            b.doClick(0);
            assertEquals(1, clicks.get());
        });
    }

    @Test
    void botaoAlternavelGuardaEstado() {
        onEdt(() -> {
            NekkoraButton b = NekkoraButton.ghost("Grade");
            b.setToggle(true);
            b.doClick(0);
            assertTrue(b.isSelected());
            b.doClick(0);
            assertFalse(b.isSelected());
        });
    }

    @Test
    void switchAlternaPorCliqueEEnter() {
        onEdt(() -> {
            NekkoraSwitch s = new NekkoraSwitch("Ativo", false);
            List<Boolean> seen = new ArrayList<>();
            s.addItemListener(e -> seen.add(s.isSelected()));
            s.doClick(0);
            assertTrue(Keys.press(s, "ENTER"));
            assertEquals(List.of(true, false), seen);
        });
    }

    @Test
    void checkboxIndeterminadoSaiDoEstadoAoClicar() {
        onEdt(() -> {
            NekkoraCheckBox c = new NekkoraCheckBox("Todos");
            c.setIndeterminate(true);
            c.doClick(0);
            assertFalse(c.isIndeterminate());
            assertTrue(c.isSelected());
        });
    }

    @Test
    void radiosDoGrupoSaoExclusivosESetasNavegam() {
        onEdt(() -> {
            NekkoraRadioButton a = new NekkoraRadioButton("A", true);
            NekkoraRadioButton b = new NekkoraRadioButton("B");
            NekkoraRadioButton.group(a, b);
            assertTrue(Keys.press(a, "DOWN"));
            assertTrue(b.isSelected());
            assertFalse(a.isSelected());
        });
    }

    @Test
    void buscaNotificaEEscLimpa() {
        onEdt(() -> {
            NekkoraSearchField f = new NekkoraSearchField();
            List<String> terms = new ArrayList<>();
            f.onSearch(terms::add);
            f.setText("  robô ");
            f.postActionEvent(); // Enter
            assertEquals("robô", terms.get(terms.size() - 1));
            assertTrue(Keys.press(f, "ESCAPE"));
            assertEquals("", f.getText());
            assertEquals("", terms.get(terms.size() - 1));
        });
    }

    @Test
    void formFieldMostraEOcultaErroEAssociaRotulo() {
        onEdt(() -> {
            NekkoraTextField field = new NekkoraTextField();
            NekkoraFormField ff = new NekkoraFormField("Nome", field).required().helper("Ajuda");
            ff.setError("Obrigatório");
            assertTrue(ff.hasError());
            assertTrue(field.isError());
            ff.setError(null);
            assertFalse(ff.hasError());
            assertFalse(field.isError());
            boolean labelled = false;
            for (var c : ((java.awt.Container) ff.getComponent(0)).getComponents()) {
                if (c instanceof JLabel l && l.getLabelFor() == field) labelled = true;
            }
            assertTrue(labelled, "rótulo associado ao campo");
        });
    }

    @Test
    void tabelaAtivaLinhaComEnter() {
        onEdt(() -> {
            NekkoraTable t = new NekkoraTable(ComponentCatalog.tableModel());
            List<Integer> activated = new ArrayList<>();
            t.onRowActivated(activated::add);
            t.setRowSelectionInterval(1, 1);
            assertTrue(Keys.press(t, "ENTER"));
            assertEquals(List.of(1), activated);
            assertEquals(1, t.getSelectedModelRow());
        });
    }

    @Test
    void tabelaOrdenaPelaColunaMantendoIndiceDoModelo() {
        onEdt(() -> {
            NekkoraTable t = new NekkoraTable(ComponentCatalog.tableModel());
            t.getRowSorter().toggleSortOrder(2); // Qtd: 3, 7, 1 -> 1, 3, 7
            assertEquals(2, t.convertRowIndexToModel(0));
        });
    }

    @Test
    void arvoreAtivaFolhaComEnter() {
        onEdt(() -> {
            NekkoraTree tree = new NekkoraTree(ComponentCatalog.tree());
            tree.expandAll();
            List<String> opened = new ArrayList<>();
            tree.onNodeActivated(n -> opened.add(n.getLabel()));
            tree.setSelectionRow(2);
            assertTrue(Keys.press(tree, "ENTER"));
            assertEquals(List.of("Item 1"), opened);
            NekkoraTreeNode sel = tree.getSelectedNode();
            assertEquals(Status.SUCCESS, sel.getStatus());
        });
    }

    @Test
    void sidebarSelecionaENotificaEEnterAtiva() {
        onEdt(() -> {
            NekkoraSidebar s = new NekkoraSidebar();
            NekkoraSidebarGroup g = s.addGroup("G");
            NekkoraSidebarItem a = g.addItem(new NekkoraSidebarItem("a", "A", IconName.HOME));
            NekkoraSidebarItem b = g.addItem(new NekkoraSidebarItem("b", "B", IconName.FILE));
            List<String> sel = new ArrayList<>();
            s.onSelect(i -> sel.add(i.getId()));
            s.select("a");
            assertTrue(Keys.press(b, "ENTER"));
            assertEquals(List.of("a", "b"), sel);
            assertTrue(b.isSelected());
            assertFalse(a.isSelected());
            s.setCollapsed(true);
            assertEquals("B", b.getToolTipText());
        });
    }

    @Test
    void painelRecolhivelAlternaComTeclado() {
        onEdt(() -> {
            NekkoraCollapsiblePanel p = new NekkoraCollapsiblePanel("Grupo", new JLabel("x"));
            assertTrue(p.isExpanded());
            assertTrue(Keys.press(p.getHeader(), "SPACE"));
            assertFalse(p.isExpanded());
            assertTrue(Keys.press(p.getHeader(), "ENTER"));
            assertTrue(p.isExpanded());
        });
    }

    @Test
    void cardClicavelResponde() {
        onEdt(() -> {
            AtomicInteger n = new AtomicInteger();
            NekkoraCard c = new NekkoraCard("x", null);
            c.setOnClick(n::incrementAndGet);
            assertTrue(Keys.press(c, "ENTER"));
            assertEquals(1, n.get());
        });
    }

    @Test
    void tagRemovivelComDelete() {
        onEdt(() -> {
            AtomicInteger n = new AtomicInteger();
            NekkoraTag t = new NekkoraTag("x").removable(n::incrementAndGet);
            assertTrue(Keys.press(t, "DELETE"));
            assertEquals(1, n.get());
        });
    }

    @Test
    void abasFechaveisRespeitamConfirmacao() {
        onEdt(() -> {
            NekkoraTabs tabs = new NekkoraTabs(NekkoraTabs.Style.CARD);
            tabs.addTab("a", IconName.FILE, new JLabel("a"));
            tabs.addTab("b", IconName.FILE, new JLabel("b"));
            tabs.setClosable(true, i -> i == 1);
            tabs.closeTab(0);
            assertEquals(2, tabs.getTabCount());
            tabs.closeTab(1);
            assertEquals(1, tabs.getTabCount());
        });
    }

    @Test
    void areaDeTrabalhoNaoDuplicaDocumento() {
        onEdt(() -> {
            NekkoraWorkArea w = new NekkoraWorkArea();
            w.openTab("x", "X", IconName.FILE, new JLabel("1"));
            w.openTab("x", "X", IconName.FILE, new JLabel("2"));
            w.openTab("y", "Y", IconName.FILE, new JLabel("3"));
            assertEquals(2, w.getOpenCount());
            assertTrue(w.isOpen("x"));
        });
    }

    @Test
    void statusBadgeAtualizaTomERotulo() {
        onEdt(() -> {
            NekkoraStatusBadge b = new NekkoraStatusBadge(Status.PENDING);
            b.setStatus(Status.FAILED);
            assertEquals("Falhou", b.getText());
            assertEquals(Status.FAILED.tone(), b.getTone());
        });
    }

    @Test
    void stepperMarcaEtapas() {
        onEdt(() -> {
            NekkoraStepper s = new NekkoraStepper("A", "B", "C");
            s.setCurrent(1);
            assertEquals(NekkoraStepper.StepState.COMPLETE, s.getState(0));
            assertEquals(NekkoraStepper.StepState.CURRENT, s.getState(1));
            assertEquals(NekkoraStepper.StepState.UPCOMING, s.getState(2));
        });
    }
}
