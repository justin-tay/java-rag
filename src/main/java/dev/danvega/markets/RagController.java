package dev.danvega.markets;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagController {

    private final ChatClient chatClient;

    public RagController(ChatClient.Builder builder, VectorStore vectorStore) {
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                //.queryTransformers(new MetadataAwareQueryTransformer(builder.build().mutate()))
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .build())
                .build();

        this.chatClient = builder
                .defaultAdvisors(ragAdvisor)
                .build();
    }

    @GetMapping("/rag")
    public String rag(@RequestParam(required = false) String prompt) {
        String actualPrompt = prompt;
        if (prompt == null || prompt.isEmpty()) {
            actualPrompt = "How did the Federal Reserve's recent interest rate cut impact various asset classes according to the analysis";
        }
        return chatClient.prompt()
                .user(actualPrompt)
                .call()
                .content();
    }
}
