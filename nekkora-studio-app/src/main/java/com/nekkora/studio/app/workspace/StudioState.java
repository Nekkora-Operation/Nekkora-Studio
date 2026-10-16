package com.nekkora.studio.app.workspace;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationCatalog;
import com.nekkora.studio.domain.AutomationId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Estado da área de trabalho: catálogo de automações (fonte única dos dados), automações abertas, ativa,
 * seleção por automação, alterações não persistidas, última notificação e operação em andamento.
 *
 * <p>Somente leitura para a interface; alterado apenas por {@link WorkspaceService} (mutadores restritos ao
 * pacote). Não guarda cópias de sessões ou etapas — a interface sempre lê o domínio.
 */
public final class StudioState {
    private final AutomationCatalog catalog;
    private final List<AutomationId> open = new ArrayList<>();
    private final Map<AutomationId, Selection> selections = new HashMap<>();
    private final List<Consumer<StudioEvent>> listeners = new CopyOnWriteArrayList<>();
    private AutomationId active;
    /** STUDIO-006: estado de persistência POR automação (D-S006-13). */
    private final Map<AutomationId, ProjectState> projects = new HashMap<>();
    private Notice lastNotice;
    private StudioOperation operation;

    public StudioState(AutomationCatalog catalog) {
        this.catalog = catalog;
    }

    // ------------------------------------------------------------------ leitura

    public AutomationCatalog catalog() {
        return catalog;
    }

    public List<Automation> automations() {
        return catalog.all();
    }

    /** Automações abertas, na ordem de abertura. */
    public List<AutomationId> openAutomations() {
        return Collections.unmodifiableList(open);
    }

    public Optional<Automation> activeAutomation() {
        return active == null ? Optional.empty() : catalog.find(active);
    }

    public Optional<AutomationId> activeId() {
        return Optional.ofNullable(active);
    }

    /** Seleção da automação ativa. */
    public Selection selection() {
        return active == null ? Selection.NONE : selectionOf(active);
    }

    /** Seleção lembrada de uma automação (cada automação mantém a sua). */
    public Selection selectionOf(AutomationId id) {
        return selections.getOrDefault(id, new Selection.OfAutomation(id));
    }

    /** Alguma automação tem alterações que ainda não estão num arquivo confirmado. */
    public boolean hasUnsavedChanges() {
        return projects.values().stream().anyMatch(ProjectState::dirty);
    }

    /** Estado de persistência da automação (STUDIO-006). */
    public Optional<ProjectState> project(AutomationId id) {
        return Optional.ofNullable(projects.get(id));
    }

    /** Automações com alterações não salvas, na ordem do catálogo. */
    public List<Automation> unsavedAutomations() {
        return catalog.all().stream().filter(a -> project(a.id()).map(ProjectState::dirty).orElse(false)).toList();
    }

    public Optional<Notice> lastNotice() {
        return Optional.ofNullable(lastNotice);
    }

    public Optional<StudioOperation> currentOperation() {
        return Optional.ofNullable(operation);
    }

    public void addListener(Consumer<StudioEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<StudioEvent> listener) {
        listeners.remove(listener);
    }

    // ------------------------------------------------------------------ mutadores (WorkspaceService)

    boolean open(AutomationId id) {
        if (open.contains(id)) return false;
        open.add(id);
        return true;
    }

    /** Fecha e devolve a próxima automação a ativar (vizinha), se houver. */
    AutomationId close(AutomationId id) {
        int i = open.indexOf(id);
        if (i < 0) return active;
        open.remove(i);
        if (!id.equals(active)) return active;
        return open.isEmpty() ? null : open.get(Math.min(i, open.size() - 1));
    }

    void activate(AutomationId id) {
        active = id;
    }

    void select(AutomationId automation, Selection selection) {
        selections.put(automation, selection);
    }

    /** Uma alteração que precisa ser salva aconteceu nesta automação (nova geração de persistência). */
    void markChanged(AutomationId id) {
        projects.computeIfAbsent(id, k -> new ProjectState(0)).changed();
    }

    void putProject(AutomationId id, ProjectState p) {
        projects.put(id, p);
    }

    void forgetProject(AutomationId id) {
        projects.remove(id);
        selections.remove(id);
    }

    void notice(Notice notice) {
        lastNotice = notice;
    }

    void operation(StudioOperation op) {
        operation = op;
    }

    void fire(AutomationId automation, StudioChange first, StudioChange... rest) {
        fireFor(automation, java.util.Set.<com.nekkora.studio.domain.SessionId>of(), first, rest);
    }

    /** Notifica uma alteração restrita a uma sessão lógica (a tela atualiza só a parte afetada). */
    void fireFor(AutomationId automation, com.nekkora.studio.domain.SessionId session, StudioChange first, StudioChange... rest) {
        fireFor(automation, session == null ? java.util.Set.of() : java.util.Set.of(session), first, rest);
    }

    /** Notifica uma alteração restrita a algumas sessões (STUDIO-004: origem e destino de uma movimentação). */
    void fireFor(AutomationId automation, java.util.Set<com.nekkora.studio.domain.SessionId> sessions, StudioChange first, StudioChange... rest) {
        StudioEvent e = new StudioEvent(EnumSet.of(first, rest), automation, sessions);
        for (Consumer<StudioEvent> l : listeners) l.accept(e);
    }
}
