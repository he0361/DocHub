package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.DeploymentType;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VllmChatModelProviderIntegrationTest {

    @Test
    void streamingProbeSendsACompleteOpenAiRequestBody() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("data: {\"id\":\"probe\",\"object\":\"chat.completion.chunk\","
                + "\"created\":1,\"model\":\"Qwen3.8-27B\",\"choices\":[{\"index\":0,"
                + "\"delta\":{\"role\":\"assistant\",\"content\":\"OK\"},\"finish_reason\":null}]}\n\n"
                + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ModelRuntimeSpec spec = new ModelRuntimeSpec(ModelType.CHAT, DeploymentType.LOCAL,
                CompatibilityPreset.OPENAI_COMPATIBLE, "http://127.0.0.1:" + server.getAddress().getPort(),
                "/v1/chat/completions", "/v1/embeddings", "", "Qwen3.8-27B", 0.2, 128, 5_000);
            VllmChatModelProvider provider = new VllmChatModelProvider(new OpenAiCompatibleModelFactory(),
                (model, tools) -> { });

            provider.probe(provider.create(spec), false);

            assertThat(captured.get()).contains("\"model\":\"Qwen3.8-27B\"", "\"messages\"", "\"stream\":true",
                "\"chat_template_kwargs\":{\"enable_thinking\":false}");
        }
        finally {
            server.stop(0);
        }
    }
}
