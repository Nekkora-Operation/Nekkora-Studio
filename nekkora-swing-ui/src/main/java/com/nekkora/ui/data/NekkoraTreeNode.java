package com.nekkora.ui.data;

import com.nekkora.ui.core.Status;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Nó de árvore com apresentação semântica: rótulo, ícone (e tom), texto secundário, status
 * e contador. O {@code userObject} fica livre para o objeto do domínio.
 */
public class NekkoraTreeNode extends DefaultMutableTreeNode {
    private String label;
    private IconName icon;
    private Tone iconTone;
    private String secondary;
    private Status status;
    private Integer count;

    public NekkoraTreeNode(String label) {
        this(label, null, null);
    }

    public NekkoraTreeNode(String label, IconName icon) {
        this(label, icon, null);
    }

    public NekkoraTreeNode(String label, IconName icon, Object userObject) {
        super(userObject);
        this.label = label;
        this.icon = icon;
    }

    public NekkoraTreeNode add(NekkoraTreeNode... children) {
        for (NekkoraTreeNode c : children) super.add(c);
        return this;
    }

    public String getLabel() {
        return label;
    }

    public NekkoraTreeNode setLabel(String label) {
        this.label = label;
        return this;
    }

    public IconName getIcon() {
        return icon;
    }

    public NekkoraTreeNode setIcon(IconName icon) {
        this.icon = icon;
        return this;
    }

    public Tone getIconTone() {
        return iconTone;
    }

    /** Colore o ícone com um tom (padrão: cor secundária do texto). */
    public NekkoraTreeNode setIconTone(Tone tone) {
        this.iconTone = tone;
        return this;
    }

    public String getSecondary() {
        return secondary;
    }

    public NekkoraTreeNode setSecondary(String secondary) {
        this.secondary = secondary;
        return this;
    }

    public Status getStatus() {
        return status;
    }

    /** Mostra um ícone de status à direita do rótulo. */
    public NekkoraTreeNode setStatus(Status status) {
        this.status = status;
        return this;
    }

    public Integer getCount() {
        return count;
    }

    public NekkoraTreeNode setCount(Integer count) {
        this.count = count;
        return this;
    }

    @Override
    public String toString() {
        return label;
    }
}
