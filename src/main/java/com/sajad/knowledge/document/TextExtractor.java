package com.sajad.knowledge.document;

import com.sajad.knowledge.api.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

@Component
public class TextExtractor {
    public record Page(Integer number, String text) {}
    public record Chunk(int ordinal, Integer page, String text) {}
    public String safeName(String original) {
        if (original == null) throw new ApiException(400, "FILENAME", "A filename is required.");
        String name = original.replace('\\','/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank() || name.length() > 180) throw new ApiException(400, "FILENAME", "Use a filename of 1–180 characters.");
        return name;
    }
    public List<Chunk> extract(String name, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024)
            throw new ApiException(400, "FILE_SIZE", "Upload a nonempty file no larger than 5 MB.");
        List<Page> pages = new ArrayList<>();
        try {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pdf")) {
                try (var pdf = Loader.loadPDF(bytes)) {
                    if (pdf.isEncrypted() || pdf.getNumberOfPages() > 60)
                        throw new ApiException(400, "PDF_LIMIT", "Use an unencrypted PDF with at most 60 pages.");
                    var reader = new PDFTextStripper();
                    int total = 0;
                    for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                        reader.setStartPage(i); reader.setEndPage(i);
                        String text = clean(reader.getText(pdf)); total += text.length();
                        if (total > 60000) throw tooLong();
                        pages.add(new Page(i, text));
                    }
                }
            } else if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
                pages.add(new Page(null, clean(text)));
            } else throw new ApiException(415, "FILE_TYPE", "Supported files: UTF-8 .txt, .md and text-based .pdf.");
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw new ApiException(400, "UNREADABLE_FILE", "The file could not be read. Use UTF-8 text or an unencrypted text-based PDF."); }
        if (pages.stream().mapToInt(p -> p.text().length()).sum() > 60000) throw tooLong();
        List<Chunk> chunks = new ArrayList<>();
        for (Page page : pages) {
            String text = page.text();
            for (int start = 0; start < text.length();) {
                int end = Math.min(start + 1200, text.length());
                if (end < text.length()) {
                    int space = text.lastIndexOf(' ', end);
                    if (space > start + 900) end = space;
                }
                String value = text.substring(start, end).strip();
                if (!value.isEmpty()) chunks.add(new Chunk(chunks.size()+1, page.number(), value));
                if (end == text.length()) break;
                start = end - 200;
            }
        }
        if (chunks.isEmpty()) throw new ApiException(422, "NO_TEXT", "No readable text was found. Scanned PDFs need OCR before upload.");
        if (chunks.size() > 100) throw tooLong();
        return List.copyOf(chunks);
    }
    private String clean(String text) { return text.replace('\u0000', ' ').replaceAll("\\s+", " ").strip(); }
    private ApiException tooLong() { return new ApiException(413, "TEXT_LIMIT", "Document exceeds 60,000 extracted characters or 100 chunks. Split it into smaller files."); }
}
