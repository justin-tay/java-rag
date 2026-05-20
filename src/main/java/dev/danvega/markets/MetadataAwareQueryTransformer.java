package dev.danvega.markets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link QueryTransformer} that uses the LLM to extract metadata filter intent from
 * the user query and rewrites it into a clean semantic query.
 *
 * <p>Supports all filterable metadata fields stored on document chunks:
 * <ul>
 *   <li>{@code file_name}       — exact source file name, e.g. "article_thebeatoct2024.pdf"</li>
 *   <li>{@code level}           — heading level integer, e.g. 1</li>
 *   <li>{@code page_number}     — start page number; "slide N" is resolved to page N</li>
 *   <li>{@code end_page_number} — end page number</li>
 * </ul>
 *
 * <p>Note: {@code title} is not used as a filter because stored titles contain long suffixes
 * (e.g. "Slide 4: The Portfolio Solutions Group – Our Top 4 Ideas") that prevent exact matching,
 * and the Spring AI filter DSL does not support LIKE. Slide references are resolved via
 * {@code page_number} instead.
 *
 * <p>All filters use exact equality via {@link FilterExpressionBuilder}, which is the only
 * operator set supported by the Spring AI filter DSL for pgvector.
 * Multiple filters are combined with AND.
 */
public class MetadataAwareQueryTransformer implements QueryTransformer {

    private static final Logger log = LoggerFactory.getLogger(MetadataAwareQueryTransformer.class);

    private static final String SYSTEM_PROMPT = """
            You are a query planner for a RAG system that retrieves content from PDF documents.
            Each document chunk has the following metadata fields:
              - file_name:       the source PDF file name, e.g. "article_thebeatoct2024.pdf"
              - level:           the heading level as an integer, e.g. 1
              - page_number:     the start page number as an integer (slide N == page N), e.g. 4
              - end_page_number: the end page number as an integer, e.g. 5

            Given a user query, produce a JSON object with:
              - semanticQuery:   the query rewritten for semantic vector search, stripped of all metadata references.
                                 If the query is only about a specific slide/page, use a generic term like "summary".
              - fileName:        exact file name if mentioned, otherwise null
              - level:           heading level as integer if mentioned, otherwise null
              - pageNumber:      the page or slide number as integer if mentioned ("slide 4" → 4), otherwise null
              - endPageNumber:   end page number as integer if mentioned, otherwise null

            Examples:
              "summarise slide 4"
                → semanticQuery: "summary", fileName: null, level: null, pageNumber: 4, endPageNumber: null

              "summarise slide 4 of the file article_thebeatoct2024.pdf"
                → semanticQuery: "summary", fileName: "article_thebeatoct2024.pdf", level: null, pageNumber: 4, endPageNumber: null

              "what does page 3 of article_thebeatoct2024.pdf say about inflation?"
                → semanticQuery: "inflation", fileName: "article_thebeatoct2024.pdf", level: null, pageNumber: 3, endPageNumber: null

              "how did the Fed rate cut impact bonds?"
                → semanticQuery: "Fed rate cut impact bonds", fileName: null, level: null, pageNumber: null, endPageNumber: null
            """;

    private final ChatClient chatClient;

    public MetadataAwareQueryTransformer(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Query transform(Query query) {
        log.debug("Planning query: {}", query.text());

        QueryPlan plan = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(query.text())
                .call()
                .entity(QueryPlan.class);

        log.debug("Query plan: {}", plan);

        Map<String, Object> updatedContext = new HashMap<>(query.context());

        var filterExpression = buildFilterExpression(plan);
        if (filterExpression != null) {
            updatedContext.put(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filterExpression);
            log.debug("Applied filter expression: {}", filterExpression);
        }

        return query.mutate()
                .text(plan.semanticQuery())
                .context(updatedContext)
                .build();
    }

    /**
     * Builds a compound AND filter expression using {@link FilterExpressionBuilder}.
     * All fields use exact equality — the only operators supported by the Spring AI filter DSL.
     * Returns null when no filters are needed (pure semantic search).
     */
    private Object buildFilterExpression(QueryPlan plan) {
        var b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> clauses = new ArrayList<>();

        if (plan.fileName() != null && !plan.fileName().isBlank()) {
            clauses.add(b.eq("file_name", plan.fileName()));
        }
        if (plan.level() != null) {
            clauses.add(b.eq("level", plan.level()));
        }
        if (plan.pageNumber() != null) {
            clauses.add(b.eq("page_number", plan.pageNumber()));
        }
        if (plan.endPageNumber() != null) {
            clauses.add(b.eq("end_page_number", plan.endPageNumber()));
        }

        if (clauses.isEmpty()) {
            return null;
        }
        if (clauses.size() == 1) {
            return clauses.get(0).build();
        }

        // Fold all clauses into a left-associative AND tree
        FilterExpressionBuilder.Op combined = clauses.get(0);
        for (int i = 1; i < clauses.size(); i++) {
            combined = b.and(combined, clauses.get(i));
        }
        return combined.build();
    }
}
