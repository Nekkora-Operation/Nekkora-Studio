package com.nekkora.ui.data;

import com.nekkora.ui.core.Status;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.table.AbstractTableModel;

/**
 * Modelo de tabela baseado em lista + definições de coluna. A tela descreve <b>o que</b> mostrar;
 * a {@link NekkoraTable} decide <b>como</b>.
 *
 * <pre>{@code
 * var model = new NekkoraTableModel<Item>(List.of(
 *         Column.text("Nome", Item::name),
 *         Column.status("Status", Item::status),
 *         Column.mono("ID", Item::id).width(120),
 *         Column.secondary("Atualizado", Item::updatedAt)));
 * model.setRows(itens);
 * }</pre>
 */
public class NekkoraTableModel<T> extends AbstractTableModel {

    /** Como o valor da coluna é apresentado. */
    public enum Kind {
        /** Texto principal. */
        TEXT,
        /** Texto secundário (metadados). */
        SECONDARY,
        /** Monoespaçado (IDs, códigos, caminhos). */
        MONO,
        /** Número alinhado à direita. */
        NUMBER,
        /** {@link Status} como badge. */
        STATUS
    }

    /** Definição de coluna. */
    public static final class Column<T> {
        private final String title;
        private final Kind kind;
        private final Function<T, ?> value;
        private final Class<?> type;
        private int width = -1;
        private boolean sortable = true;

        private Column(String title, Kind kind, Class<?> type, Function<T, ?> value) {
            this.title = Objects.requireNonNull(title);
            this.kind = kind;
            this.type = type;
            this.value = Objects.requireNonNull(value);
        }

        public static <T> Column<T> text(String title, Function<T, ?> value) {
            return new Column<>(title, Kind.TEXT, Object.class, value);
        }

        public static <T> Column<T> secondary(String title, Function<T, ?> value) {
            return new Column<>(title, Kind.SECONDARY, Object.class, value);
        }

        public static <T> Column<T> mono(String title, Function<T, ?> value) {
            return new Column<>(title, Kind.MONO, Object.class, value);
        }

        public static <T> Column<T> number(String title, Function<T, ? extends Number> value) {
            return new Column<>(title, Kind.NUMBER, Number.class, value);
        }

        public static <T> Column<T> status(String title, Function<T, Status> value) {
            return new Column<>(title, Kind.STATUS, Status.class, value);
        }

        /** Largura preferida em px lógicos. */
        public Column<T> width(int logicalWidth) {
            this.width = logicalWidth;
            return this;
        }

        public Column<T> notSortable() {
            this.sortable = false;
            return this;
        }

        public String title() {
            return title;
        }

        public Kind kind() {
            return kind;
        }

        public int width() {
            return width;
        }

        public boolean sortable() {
            return sortable;
        }
    }

    private final List<Column<T>> columns;
    private final List<T> rows = new ArrayList<>();

    public NekkoraTableModel(List<Column<T>> columns) {
        this.columns = List.copyOf(columns);
    }

    public void setRows(Collection<? extends T> newRows) {
        rows.clear();
        rows.addAll(newRows);
        fireTableDataChanged();
    }

    public void addRow(T row) {
        rows.add(row);
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    public void removeRow(int modelRow) {
        rows.remove(modelRow);
        fireTableRowsDeleted(modelRow, modelRow);
    }

    /** Atualiza a linha que contém {@code row} (por igualdade). */
    public void updateRow(T row) {
        int i = rows.indexOf(row);
        if (i >= 0) fireTableRowsUpdated(i, i);
    }

    public T getRow(int modelRow) {
        return rows.get(modelRow);
    }

    public List<T> getRows() {
        return List.copyOf(rows);
    }

    public Column<T> getColumn(int index) {
        return columns.get(index);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column).title;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return columns.get(column).type;
    }

    @Override
    public Object getValueAt(int row, int column) {
        return columns.get(column).value.apply(rows.get(row));
    }
}
