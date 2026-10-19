package com.nekkora.studio.ui.pdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.layout.MarkerLayout;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pedido da usuária (teste do PDD, 19/10/2026): depois de recortar, os números dos marcadores ficavam um em cima do outro.
 * O editor agora usa a mesma regra do PDF (MarkerLayout): números separados e dentro do recorte.
 */
class PddImageEditorMarkersTest {

    @Test
    @DisplayName("recorte justo em campos empilhados (Usuário, Senha, Entrar): números separados e dentro do recorte")
    void numerosNaoSeSobrepoemNoRecorte() throws Exception {
        List<MarkerLayout.Point> ps = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            PddImageEditor ed = new PddImageEditor();
            BufferedImage img = new BufferedImage(1350, 750, BufferedImage.TYPE_INT_RGB);
            // formulário compacto (px do dispositivo): campos de 26 px a cada 35 px. Na escala do editor (520/1350) ficam a ~13 px um
            // do outro; a regra antiga (número sempre à esquerda, no meio da altura) punha 1 e 2 sobrepostos
            List<PddImageEditor.Mark> marks = List.of(new PddImageEditor.Mark(1, new Rectangle(29, 118, 177, 26), false),
                    new PddImageEditor.Mark(2, new Rectangle(29, 153, 177, 26), false), new PddImageEditor.Mark(3, new Rectangle(29, 188, 52, 26), true));
            Rectangle crop = new Rectangle(20, 100, 200, 130); // recorte justo: à esquerda dos campos quase não há espaço
            ed.setContent(img, crop, marks);
            ed.setSize(ed.getPreferredSize()); // como na janela: a escala vem da largura do editor
            ps.addAll(ed.badgeCenters());
        });
        assertEquals(3, ps.size());
        for (int i = 0; i < ps.size(); i++) {
            for (int j = i + 1; j < ps.size(); j++) {
                double d = Math.hypot(ps.get(i).x() - ps.get(j).x(), ps.get(i).y() - ps.get(j).y());
                assertTrue(d >= 16, "números " + (i + 1) + " e " + (j + 1) + " sobrepostos: " + ps.get(i) + " " + ps.get(j));
            }
        }
    }
}
