package dev.danvega.markets;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam(required=false) String prompt) {
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
