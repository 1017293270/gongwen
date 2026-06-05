package com.gongwen.assistant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class DeepSeekModelAdapterParsingTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsJsonFromMarkdownFence() {
        String content = """
                ```json
                {"title":"test","sections":[]}
                ```
                """;

        assertThat(DeepSeekModelAdapter.extractJsonObject(content))
                .isEqualTo("{\"title\":\"test\",\"sections\":[]}");
    }

    @Test
    void extractsFirstJsonObjectFromExplanatoryText() {
        String content = "Here is the result:\n{\"content\":\"value with } inside text\"}\nDone.";

        assertThat(DeepSeekModelAdapter.extractJsonObject(content))
                .isEqualTo("{\"content\":\"value with } inside text\"}");
    }

    @Test
    void rejectsResponseWithoutJsonObject() {
        assertThatThrownBy(() -> DeepSeekModelAdapter.extractJsonObject("not json"))
                .isInstanceOf(ModelAdapterException.class)
                .hasMessage("DeepSeek 返回结构无效");
    }

    @Test
    void parsesChineseNestedOutlineFields() throws Exception {
        DeepSeekModelAdapter adapter = newAdapter();

        AiOutlineResponse response = adapter.parseOutlinePayload(prompt("草稿标题"), objectMapper.readTree("""
                {
                  "建议标题": "关于做好年度述职工作的报告",
                  "正文结构": {
                    "章节列表": [
                      {
                        "章节标题": "一、工作开展情况",
                        "写作要点": [
                          {"内容": "概述年度重点任务"},
                          {"内容": "说明关键成效"}
                        ]
                      },
                      {
                        "章节标题": "二、下一步安排",
                        "写作要点": "明确改进方向；提出落实举措"
                      }
                    ]
                  },
                  "缺失信息提示": ["具体时间", "数据口径"]
                }
                """));

        assertThat(response.titleSuggestion()).isEqualTo("关于做好年度述职工作的报告");
        assertThat(response.sections()).hasSize(2);
        assertThat(response.sections().getFirst().heading()).isEqualTo("一、工作开展情况");
        assertThat(response.sections().getFirst().points()).containsExactly("概述年度重点任务", "说明关键成效");
        assertThat(response.sections().get(1).points()).containsExactly("明确改进方向", "提出落实举措");
        assertThat(response.missingInformation()).containsExactly("具体时间", "数据口径");
    }

    @Test
    void fallsBackToDraftTitleWhenModelOmitsTitle() throws Exception {
        DeepSeekModelAdapter adapter = newAdapter();

        AiOutlineResponse response = adapter.parseOutlinePayload(prompt("述职报告"), objectMapper.readTree("""
                {
                  "sections": [
                    {"heading": "一、履职情况", "points": ["梳理重点工作"]}
                  ],
                  "missingInformation": []
                }
                """));

        assertThat(response.titleSuggestion()).isEqualTo("述职报告");
        assertThat(response.sections()).singleElement()
                .satisfies(section -> {
                    assertThat(section.heading()).isEqualTo("一、履职情况");
                    assertThat(section.points()).containsExactly("梳理重点工作");
                });
    }

    @Test
    void parsesMapStyleOutlineSections() throws Exception {
        DeepSeekModelAdapter adapter = newAdapter();

        AiOutlineResponse response = adapter.parseOutlinePayload(prompt("草稿标题"), objectMapper.readTree("""
                {
                  "titleSuggestion": "草稿标题",
                  "outline": {
                    "一、背景依据": ["说明背景", "列明依据"],
                    "二、工作要求": {"points": ["明确责任", "提出时限"]}
                  }
                }
                """));

        assertThat(response.sections()).extracting(AiOutlineSection::heading)
                .containsExactly("一、背景依据", "二、工作要求");
        assertThat(response.sections().getFirst().points()).containsExactly("说明背景", "列明依据");
        assertThat(response.sections().get(1).points()).containsExactly("明确责任", "提出时限");
    }

    @Test
    void parsesMarkdownTextOutlineSections() throws Exception {
        DeepSeekModelAdapter adapter = newAdapter();

        AiOutlineResponse response = adapter.parseOutlinePayload(prompt("述职报告"), objectMapper.readTree("""
                {
                  "titleSuggestion": "述职报告",
                  "outline": "- 一、履职情况：总结重点任务；说明工作成效\\n- 二、存在问题：分析短板不足\\n- 第三部分 下一步安排：明确改进方向",
                  "missingInformation": []
                }
                """));

        assertThat(response.sections()).extracting(AiOutlineSection::heading)
                .containsExactly("一、履职情况", "二、存在问题", "第三部分 下一步安排");
        assertThat(response.sections().getFirst().points()).containsExactly("总结重点任务", "说明工作成效");
        assertThat(response.sections().get(1).points()).containsExactly("分析短板不足");
        assertThat(response.sections().get(2).points()).containsExactly("明确改进方向");
    }

    @Test
    void parsesSingleTextOutlineAsFallbackSectionWhenItContainsMultiplePoints() throws Exception {
        DeepSeekModelAdapter adapter = newAdapter();

        AiOutlineResponse response = adapter.parseOutlinePayload(prompt("述职报告"), objectMapper.readTree("""
                {
                  "titleSuggestion": "述职报告",
                  "sections": "主要职责履行情况；重点任务完成情况；下一步改进措施",
                  "missingInformation": ["具体数据"]
                }
                """));

        assertThat(response.sections()).singleElement()
                .satisfies(section -> {
                    assertThat(section.heading()).isEqualTo("第 1 部分");
                    assertThat(section.points()).containsExactly("主要职责履行情况", "重点任务完成情况", "下一步改进措施");
                });
        assertThat(response.missingInformation()).containsExactly("具体数据");
    }

    @Test
    void reportsNetworkErrorWhenDeepSeekCannotConnect() {
        DeepSeekModelAdapter adapter = newAdapter(new TransportFailingHttpClient());

        ModelAdapterException exception = catchThrowableOfType(
                () -> adapter.generateOutline(prompt("草稿标题")),
                ModelAdapterException.class
        );

        assertThat(exception.errorCode()).isEqualTo("AI_DEEPSEEK_NETWORK_ERROR");
        assertThat(exception).hasMessage("DeepSeek 连接失败，请检查网络或 Base URL");
    }

    private DeepSeekModelAdapter newAdapter() {
        return newAdapter(HttpClient.newHttpClient());
    }

    private DeepSeekModelAdapter newAdapter(HttpClient httpClient) {
        AiRuntimeProperties properties = new AiRuntimeProperties(
                "deepseek",
                new AiRuntimeProperties.DeepSeek(true, "https://api.deepseek.com", "deepseek-v4-pro", "sk-test", 60),
                "storage/ai-settings.key"
        );
        return new DeepSeekModelAdapter(
                new AiConfigurationState(properties, new InMemoryAiSettingsRepository()),
                objectMapper,
                httpClient
        );
    }

    private OutlinePrompt prompt(String title) {
        return new OutlinePrompt(
                PromptBuilder.OUTLINE_PROMPT_VERSION,
                "REPORT",
                title,
                List.of(),
                List.of(),
                "",
                "documentType=REPORT"
        );
    }

    private static final class InMemoryAiSettingsRepository implements AiSettingsRepository {
        @Override
        public Optional<AiSettingsSnapshot> find() {
            return Optional.empty();
        }

        @Override
        public void save(AiSettingsSnapshot settings) {
        }
    }

    private static final class TransportFailingHttpClient extends HttpClient {
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) throws IOException {
            throw new ConnectException("connect failed");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
            future.completeExceptionally(new ConnectException("connect failed"));
            return future;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return sendAsync(request, responseBodyHandler);
        }
    }
}
