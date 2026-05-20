package dev.danvega.markets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class IngestionService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private final VectorStore vectorStore;

    @Value("classpath:/docs/article_thebeatoct2024.pdf")
    private Resource marketPDF;

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) throws Exception {
        var pdfReader = new ParagraphPdfDocumentReader(marketPDF);
        TextSplitter textSplitter = new TokenTextSplitter();
        List<Document> documents = textSplitter.apply(pdfReader.get())
                .stream()
                .map(this::enrichWithMetadata)
                .toList();
        vectorStore.accept(documents);
        log.info("VectorStore Loaded with data!");
    }

    /**
     * Returns a new {@link Document} with key metadata fields prepended as plain text
     * so the embedding vector encodes slide title, page number, and file name.
     * This allows natural language queries like "summarise slide 4" to find the right
     * chunks through semantic similarity without relying on exact metadata filters.
     *
     * <p>Example enriched content:
     * <pre>
     * Source: article_thebeatoct2024.pdf | Slide: Slide 4: The Portfolio Solutions Group – Our Top 4 Ideas | Page: 4
     * [original chunk text...]
     * </pre>
     */
    private Document enrichWithMetadata(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String fileName = String.valueOf(meta.getOrDefault("file_name",   ""));
        String title    = String.valueOf(meta.getOrDefault("title",       ""));
        String pageNum  = String.valueOf(meta.getOrDefault("page_number", ""));

        String enrichedText = "Source: %s | Slide: %s | Page: %s\n%s"
                .formatted(fileName, title, pageNum, doc.getText());

        return Document.builder()
                .id(doc.getId())
                .text(enrichedText)
                .metadata(meta)
                .build();
    }
}
