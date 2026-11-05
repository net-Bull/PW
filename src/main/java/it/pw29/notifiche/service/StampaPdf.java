package it.pw29.notifiche.service;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.PdfAConformance;
import it.pw29.notifiche.dto.VistaStampa;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * Generazione del PDF stampabile: template Thymeleaf (XHTML + CSS di stampa) reso in PDF da
 * openhtmltopdf su PDFBox (ADR-0008). Il PDF è conforme a PDF/A-2b, il profilo per l'archiviazione a
 * lungo termine: caratteri incorporati e profilo colore sRGB come OutputIntent.
 * <p>
 * Caratteri: Liberation Sans (testo) e Liberation Mono (impronte), con DejaVu Sans come riserva per i
 * glifi che Liberation non copre (il testo dei campi è libero).
 */
@Component
class StampaPdf {

    static final String TEMPLATE = "documenti/documento";
    private static final String TESTO = "Liberation Sans";
    private static final String MONO = "Liberation Mono";
    private static final String RISERVA = "DejaVu Sans";

    private final ITemplateEngine motore;
    private final byte[] profiloColore;

    StampaPdf(ITemplateEngine motore) {
        this.motore = motore;
        this.profiloColore = leggi("stampa/sRGB-IEC61966-2.1.icc");
    }

    /** HTML intermedio: utile anche per l'anteprima nel browser (Fase 4). */
    String html(VistaStampa vista) {
        Context ctx = new Context(Locale.ITALY);
        ctx.setVariable("v", vista);
        return motore.process(TEMPLATE, ctx);
    }

    byte[] pdf(VistaStampa vista) {
        String html = html(vista);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            b.usePdfAConformance(PdfAConformance.PDFA_2_B);
            b.useColorProfile(profiloColore);
            b.useFont(risorsa("stampa/font/LiberationSans-Regular.ttf"), TESTO, 400, FontStyle.NORMAL, true);
            b.useFont(risorsa("stampa/font/LiberationSans-Bold.ttf"), TESTO, 700, FontStyle.NORMAL, true);
            b.useFont(risorsa("stampa/font/LiberationMono-Regular.ttf"), MONO, 400, FontStyle.NORMAL, true);
            b.useFont(risorsa("stampa/font/DejaVuSans.ttf"), RISERVA, 400, FontStyle.NORMAL, true);
            b.useFont(risorsa("stampa/font/DejaVuSans-Bold.ttf"), RISERVA, 700, FontStyle.NORMAL, true);
            b.withProducer("PW29 notifiche CSIRT (openhtmltopdf)");
            // Nessuna risorsa esterna: CSS incorporato nel template, font dal classpath.
            b.withHtmlContent(html, null);
            b.toStream(out);
            b.run();
        } catch (IOException e) {
            throw new UncheckedIOException("Generazione del PDF non riuscita", e);
        }
        return out.toByteArray();
    }

    private static FSSupplier<InputStream> risorsa(String percorso) {
        return () -> {
            try {
                return new ClassPathResource(percorso).getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException("Risorsa di stampa mancante: " + percorso, e);
            }
        };
    }

    private static byte[] leggi(String percorso) {
        try (InputStream in = new ClassPathResource(percorso).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Risorsa di stampa mancante: " + percorso, e);
        }
    }
}
