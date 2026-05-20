package dev.danvega.markets;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Structured output produced by the query planner LLM call.
 *
 * <p>Each field maps to a metadata key stored on document chunks in the vector store:
 * <pre>
 * {
 *   "level":           1,
 *   "title":           "Slide 4: The Portfolio Solutions Group – Our Top 4 Ideas",
 *   "file_name":       "article_thebeatoct2024.pdf",
 *   "page_number":     4,
 *   "end_page_number": 5
 * }
 * </pre>
 *
 * <p>Note: {@code title} is intentionally excluded as a filter field because stored titles
 * contain long suffixes (e.g. "Slide 4: Some Long Title") that prevent exact matching.
 * Slide references are resolved via {@code pageNumber} instead (slide N == page N).
 *
 * @param semanticQuery   Rewritten query text suitable for semantic vector search.
 * @param fileName        Exact file name filter (e.g. "article_thebeatoct2024.pdf"), or null.
 * @param level           Heading level filter (e.g. 1), or null.
 * @param pageNumber      Page/slide number filter — "slide 4" maps to page_number 4, or null.
 * @param endPageNumber   End page number filter, or null.
 */
@JsonClassDescription("Retrieval plan derived from the user query")
public record QueryPlan(

        @JsonPropertyDescription(
                "The rewritten query text, stripped of any metadata references such as slide, " +
                "page, file name, or level, optimised for semantic similarity search. " +
                "If the query is only 'summarise slide 4', use 'summary' as the semantic query.")
        String semanticQuery,

        @JsonPropertyDescription(
                "The exact file name to filter on (e.g. 'article_thebeatoct2024.pdf'). Null if not mentioned.")
        String fileName,

        @JsonPropertyDescription(
                "The heading level to filter on (integer, e.g. 1). Null if not mentioned.")
        Integer level,

        @JsonPropertyDescription(
                "The page or slide number to filter on (integer). " +
                "'slide 4' and 'page 4' both map to the integer 4. Null if not mentioned.")
        Integer pageNumber,

        @JsonPropertyDescription(
                "The end page number to filter on (integer). Null if not mentioned.")
        Integer endPageNumber
) {}
