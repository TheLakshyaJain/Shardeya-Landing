package com.shardeya.platform;

import com.lowagie.text.pdf.BaseFont;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * B-11 §11 "the renderer must embed a Devanagari-capable font; a common
 * failure mode is boxes or missing conjuncts" -- the first PDF rendering
 * infrastructure in this codebase (see M3/M6 notes deferring receipt/
 * broker-statement PDFs until this milestone). Flying Saucer's ITextRenderer
 * needs well-formed XHTML (self-closing tags, a single root element), not
 * arbitrary HTML5 -- callers must pass XHTML, which DocumentGenerationService
 * builds explicitly rather than relying on the renderer to fix up loose markup.
 *
 * <p>Real Noto Sans Devanagari TTFs live in {@code src/main/resources/fonts}
 * (downloaded from Google Fonts -- {@code @fontsource/noto-sans-devanagari},
 * already a frontend dependency since M0, only ships woff/woff2, which
 * iText/OpenPDF's font embedding cannot read; a genuine TTF was needed).
 * The single font file covers both Devanagari and Latin glyphs, so one
 * embed serves both English and Hindi documents -- confirmed via Google
 * Fonts' own CSS response returning a single @font-face for
 * {@code subset=devanagari,latin} rather than two.
 */
@Service
public class PdfRenderer {

    private static final String FONT_FAMILY = "Noto Sans Devanagari";
    private final AtomicReference<Path> regularFontPath = new AtomicReference<>();
    private final AtomicReference<Path> boldFontPath = new AtomicReference<>();

    /** {@code xhtml} must be a complete, well-formed XHTML document (see class javadoc). */
    public byte[] renderPdf(String xhtml) {
        try {
            ITextRenderer renderer = new ITextRenderer();
            ITextFontResolver fontResolver = renderer.getFontResolver();
            fontResolver.addFont(extractedFontPath(regularFontPath, "/fonts/NotoSansDevanagari-Regular.ttf").toString(),
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            fontResolver.addFont(extractedFontPath(boldFontPath, "/fonts/NotoSansDevanagari-Bold.ttf").toString(),
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }

    public static String fontFamilyCss() {
        return FONT_FAMILY;
    }

    // JAR classpath resources have no real filesystem path -- iText's font
    // embedding needs one, so this extracts to a temp file once (lazily,
    // same "pay the cost on first real use" pattern MediaService.ensureBucket()
    // already established) and reuses it for every subsequent render.
    private Path extractedFontPath(AtomicReference<Path> cache, String classpathResource) throws Exception {
        Path cached = cache.get();
        if (cached != null && Files.exists(cached)) return cached;
        synchronized (cache) {
            cached = cache.get();
            if (cached != null && Files.exists(cached)) return cached;
            try (InputStream in = PdfRenderer.class.getResourceAsStream(classpathResource)) {
                if (in == null) throw new IllegalStateException("Missing bundled font resource: " + classpathResource);
                Path tempFile = Files.createTempFile("shardeya-font-", ".ttf");
                tempFile.toFile().deleteOnExit();
                Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                cache.set(tempFile);
                return tempFile;
            }
        }
    }
}
