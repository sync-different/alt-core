package com.alterante.localai;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.localai.LocalAiChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class LangChainAITest {

    LocalAiChatModel model = LocalAiChatModel.builder()
            .baseUrl("http://localhost:8080/v1")
            .modelName("whisper-base")
            .maxTokens(3)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_answer_and_return_response() throws Exception {

        AudioContent audioContent = AudioContent.from(Audio.builder().url("audio.aac").build());
        UserMessage userMessage = UserMessage.from(audioContent);
        ChatResponse response= model.chat(userMessage);

        System.out.println(response.aiMessage().text());

    }
}
