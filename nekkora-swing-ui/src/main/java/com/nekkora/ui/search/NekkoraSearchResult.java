package com.nekkora.ui.search;

import com.nekkora.ui.core.NekkoraComponent;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.FontWeight;
import com.nekkora.tokens.Radius;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.icon.IconSize;
import com.nekkora.ui.icon.NekkoraIcon;
import com.nekkora.ui.internal.Paint;
import com.nekkora.ui.theme.NekkoraFonts;
import com.nekkora.ui.theme.Scale;
import com.nekkora.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.text.Normalizer;
import java.util.Locale;

/** Linha de resultado de busca: ícone, título com o trecho encontrado em destaque, contexto e tipo. */
public class NekkoraSearchResult extends NekkoraComponent {
    private SearchItem item;
    private String query = "";
    private boolean selected;

    public NekkoraSearchResult() {}

    public NekkoraSearchResult(SearchItem item, String query) {
        set(item, query, false);
    }

    public void set(SearchItem item, String query, boolean selected) {
        this.item = item;
        this.query = query == null ? "" : query;
        this.selected = selected;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Scale.px(420), Scale.px(48));
    }

    /** Índice do termo no texto, ignorando acentos e caixa (-1 se não houver). */
    static int indexOfIgnoringAccents(String text, String query) {
        if (query.isBlank()) return -1;
        return fold(text).indexOf(fold(query.strip()));
    }

    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (item == null) return;
        Graphics2D g2 = Paint.prepare(g);
        int w = getWidth(), h = getHeight();
        if (selected) Paint.fillRound(g2, Scale.px(4), 1, w - Scale.px(8), h - 2, Scale.px(Radius.MEDIUM), Tokens.color(ColorToken.SELECTION_BACKGROUND));
        int x = Scale.px(14);
        int is = Scale.px(IconSize.M.px());
        Color ic = Tokens.color(selected ? ColorToken.ACCENT : ColorToken.TEXT_SECONDARY);
        NekkoraIcon.of(item.icon() == null ? IconName.FILE : item.icon(), IconSize.M).paint(g2, x, (h - is) / 2, is, ic, ic);
        x += is + Scale.px(12);
        java.awt.Font tf = NekkoraFonts.font(TypeStyle.BODY, FontWeight.MEDIUM), sf = NekkoraFonts.font(TypeStyle.CAPTION);
        FontMetrics tm = g2.getFontMetrics(tf), sm = g2.getFontMetrics(sf);
        int kindW = 0;
        if (item.kind() != null) kindW = sm.stringWidth(item.kind()) + Scale.px(16);
        int avail = w - x - Scale.px(14) - kindW;
        int total = tm.getHeight() + (item.subtitle() != null ? sm.getHeight() : 0);
        int ty = (h - total) / 2;
        String title = Paint.ellipsize(item.title(), tm, avail);
        int hit = indexOfIgnoringAccents(title, query);
        if (hit >= 0 && hit + query.strip().length() <= title.length()) {
            int hx = x + tm.stringWidth(title.substring(0, hit));
            int hw = tm.stringWidth(title.substring(hit, hit + query.strip().length()));
            Paint.fillRound(g2, hx - 1, ty + Scale.px(1), hw + 2, tm.getHeight() - Scale.px(2), Scale.px(3), Tokens.color(ColorToken.ACCENT_SOFT));
        }
        g2.setFont(tf);
        g2.setColor(Tokens.color(ColorToken.TEXT_PRIMARY));
        Paint.text(this, g2, title, x, ty + tm.getAscent());
        if (item.subtitle() != null) {
            g2.setFont(sf);
            g2.setColor(Tokens.color(ColorToken.TEXT_MUTED));
            Paint.text(this, g2, Paint.ellipsizeMiddle(item.subtitle(), sm, avail), x, ty + tm.getHeight() + sm.getAscent());
        }
        if (item.kind() != null) {
            g2.setFont(sf);
            int kw = sm.stringWidth(item.kind()) + Scale.px(12), kh = Scale.px(18);
            float kx = w - Scale.px(14) - kw;
            Paint.fillRound(g2, kx, (h - kh) / 2f, kw, kh, kh / 2f, Tokens.color(ColorToken.STATUS_NEUTRAL_SOFT));
            g2.setColor(Tokens.color(ColorToken.TEXT_SECONDARY));
            Paint.text(this, g2, item.kind(), kx + Scale.px(6), Paint.centeredBaseline(sm, 0, h));
        }
        g2.dispose();
    }
}
