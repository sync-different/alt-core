package com.alterante.ollama;

public class MyTest {
     public static void main(String[] args)  {

            System.out.println("hello ollama2");
            //OllamaChatModelTest tester = new OllamaChatModelTest();
            OllamaStreamingChatModelTest tester2 = new OllamaStreamingChatModelTest();
            tester2.streaming_example();
            System.out.println("bye...");
     }
    }
