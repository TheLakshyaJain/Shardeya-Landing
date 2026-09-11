package com.shardeya.foundation.media;

/**
 * Server validates uploads by magic bytes, never by extension or the
 * client-supplied MIME type (00-ARCHITECTURE.md §4.10 / CLAUDE.md pitfall #6
 * equivalent for uploads generally). Deliberately minimal — a full MIME
 * sniffing library (Apache Tika) would be a heavy dependency for a handful of
 * signatures this milestone actually needs.
 */
public final class MagicBytes {

    private MagicBytes() {
    }

    public static String detect(byte[] head) {
        if (matches(head, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (matches(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (head.length >= 12 && matches(head, 0x52, 0x49, 0x46, 0x46)
                && head[8] == 0x57 && head[9] == 0x45 && head[10] == 0x42 && head[11] == 0x50) {
            return "image/webp";
        }
        if (matches(head, 0x25, 0x50, 0x44, 0x46)) return "application/pdf";
        // XLSX (M-07 plot-import template) is a ZIP container under the hood
        // -- same PK local-file-header signature as any OOXML/ZIP file.
        // Distinguishing xlsx from a generic zip/docx/pptx would need
        // inspecting the archive's internal [Content_Types].xml, which is
        // more than this app needs: only the plot-import template flows
        // through here, so the ZIP signature is treated as xlsx directly.
        if (matches(head, 0x50, 0x4B, 0x03, 0x04)) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return null;
    }

    private static boolean matches(byte[] head, int... signature) {
        if (head.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
