package cn.aobp.wishboard;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListResult;
import run.halo.app.plugin.ReactiveSettingFetcher;
import cn.aobp.wishboard.model.PublicWish;
import cn.aobp.wishboard.model.Wish;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

/**
 * 公开接口（匿名可访问）：查看心愿列表、投稿、AI润色。
 * 使用独立的 apiGroup 以便 RBAC 精确授权给 anonymous 角色。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WishPublicRouter implements CustomEndpoint {

    private final WishService wishService;
    private final AiService aiService;
    private final ReactiveSettingFetcher settingFetcher;

    /**
     * 安全获取设置值，防御 Reactor 3.8.3 cacheInvalidateIf 空完成异常。
     */
    @SuppressWarnings("unchecked")
    private Mono<Object> getSetting(String group) {
        return ((Mono<Object>) (Mono<?>) settingFetcher.getSettingValue(group))
            .onErrorResume(e -> Mono.empty());
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "anonymous.wishboard.aobp.cn/v1alpha1/Wishboard";
        return SpringdocRouteBuilder.route()
            .GET("wishes", this::listPublic,
                b -> b.operationId("ListPublicWishes").tag(tag)
                    .description("分页获取公开便签，仅包含 approved、pending、doing、done 且未删除的记录")
                    .parameter(parameterBuilder().name("page").in(ParameterIn.QUERY)
                        .description("页码，从 1 开始；page 与 size 的乘积不能超过 2147483647")
                        .schema(schemaBuilder().implementation(Integer.class).minimum("1").defaultValue("1")))
                    .parameter(parameterBuilder().name("size").in(ParameterIn.QUERY)
                        .description("每页条数")
                        .schema(schemaBuilder().implementation(Integer.class).minimum("1").maximum("100")
                            .defaultValue("20")))
                    .parameter(parameterBuilder().name("type").in(ParameterIn.QUERY)
                        .description("类型 slug 精确匹配，省略或空白表示全部类型").implementation(String.class))
                    .parameter(parameterBuilder().name("status").in(ParameterIn.QUERY)
                        .description("公开状态，省略或空白表示全部公开状态")
                        .schema(schemaBuilder().implementation(String.class)
                            .allowableValues(new String[]{"approved", "pending", "doing", "done"})))
                    .parameter(parameterBuilder().name("sort").in(ParameterIn.QUERY)
                        .description("创建时间排序，同时间按 metadata.name 升序；省略或空白使用默认顺序")
                        .schema(schemaBuilder().implementation(String.class).defaultValue("createdAt,desc")
                            .allowableValues(new String[]{"createdAt,desc", "createdAt,asc"})))
                    .response(responseBuilder().responseCode("200").description("公开便签分页结果")
                        .implementation(ListResult.generateGenericClass(PublicWish.class)))
                    .response(responseBuilder().responseCode("400")
                        .description("查询参数无效，返回 {\"error\": \"说明\"}")
                        .implementation(Map.class)))
            .POST("wishes/-/submit", this::submitWish,
                b -> b.operationId("SubmitWish").tag(tag)
                    .description("访客投稿便签（树洞/心愿）")
                    .response(responseBuilder().description("投稿结果")))
            .POST("wishes/-/polish", this::aiPolish,
                b -> b.operationId("AiPolishWish").tag(tag)
                    .description("AI 润色心愿内容")
                    .response(responseBuilder().description("润色结果")))
            .build();
    }

    private Mono<ServerResponse> listPublic(ServerRequest request) {
        final PublicWishQuery query;
        try {
            query = PublicWishQuery.of(integerParameter(request, "page"), integerParameter(request, "size"),
                request.queryParam("type").orElse(null), request.queryParam("status").orElse(null),
                request.queryParam("sort").orElse(null));
        } catch (IllegalArgumentException e) {
            return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("error", e.getMessage()));
        }
        return wishService.listPublic(query)
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result));
    }

    private static Integer integerParameter(ServerRequest request, String name) {
        return request.queryParam(name).map(value -> {
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " 必须是有效整数");
            }
        }).orElse(null);
    }

    private Mono<ServerResponse> submitWish(ServerRequest request) {
        // 安全检查：后端强制校验 enableSubmit，防止绕过前端直接调用 API
        return getSetting("treehole")
            .flatMap(treeholeSetting -> {
                if (!SettingValues.bool(treeholeSetting, "enableSubmit", true)) {
                    return ServerResponse.status(403)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("error", "投稿功能已关闭"));
                }
                return doSubmitWish(request, treeholeSetting);
            })
            .switchIfEmpty(doSubmitWish(request, null));
    }

    private Mono<ServerResponse> doSubmitWish(ServerRequest request,
                                               Object treeholeSetting) {
        return request.bodyToMono(Map.class).flatMap(body -> {
            String content = stripHtml((String) body.getOrDefault("content", ""));
            String nickname = stripHtml((String) body.getOrDefault("nickname", "匿名"));
            String type = stripHtml((String) body.getOrDefault("type", "treehole"));
            String color = stripHtml((String) body.getOrDefault("color", ""));
            boolean anonymous = Boolean.TRUE.equals(body.get("anonymous"));

            if (content == null || content.isBlank()) {
                return ServerResponse.badRequest()
                    .bodyValue(Map.of("error", "内容不能为空"));
            }
            // 昵称长度限制
            final String safeNickname = nickname.length() > 20 ? nickname.substring(0, 20) : nickname;

            String ip = request.remoteAddress()
                .map(InetSocketAddress::getHostString).orElse("unknown");

            // 验证颜色值
            var allowedColors = java.util.Set.of("pink", "blue", "yellow", "green", "purple", "orange");
            if (!allowedColors.contains(color)) {
                color = "green";
            }
            final String safeColor = color;

            // 使用已获取的 treeholeSetting（可能为 null）
            Mono<Object> treeholeMono = treeholeSetting != null
                ? Mono.just(treeholeSetting)
                : getSetting("treehole");

            return treeholeMono.flatMap(ts -> {
                int rateLimit = SettingValues.integer(ts, "rateLimit", 3);
                int maxLength = SettingValues.integer(ts, "maxLength", 200);
                String blockedWords = SettingValues.text(ts, "blockedWords", "");

                if (content.length() > maxLength) {
                    return ServerResponse.badRequest()
                        .bodyValue(Map.of("error", "内容超过" + maxLength + "字限制"));
                }
                if (!wishService.checkRateLimit(ip, rateLimit)) {
                    return ServerResponse.status(429)
                        .bodyValue(Map.of("error", "投稿太频繁，请稍后再试"));
                }
                if (!blockedWords.isBlank()) {
                    for (String word : blockedWords.split("\n")) {
                        if (!word.isBlank() &&
                            (content.contains(word.trim()) || safeNickname.contains(word.trim()))) {
                            return ServerResponse.badRequest()
                                .bodyValue(Map.of("error", "内容包含敏感词"));
                        }
                    }
                }

                Wish wish = new Wish();
                wish.setSpec(new Wish.WishSpec());
                wish.getSpec().setContent(content);
                wish.getSpec().setNickname(safeNickname);
                wish.getSpec().setType(type);
                wish.getSpec().setColor(safeColor);
                wish.getSpec().setAnonymous(anonymous);
                wish.getSpec().setIp(ip);
                wish.getSpec().setPriority("normal");
                wish.getSpec().setCreatedAt(Instant.now());
                wish.setMetadata(new run.halo.app.extension.Metadata());

                String reviewMode = SettingValues.text(ts, "reviewMode", "ai");
                return determineStatus(reviewMode, content, wish);
            });
        });
    }

    private Mono<ServerResponse> determineStatus(String reviewMode, String content, Wish wish) {
        Mono<String> statusMono;
        if ("none".equals(reviewMode)) {
            statusMono = Mono.just("approved");
        } else if ("manual".equals(reviewMode)) {
            statusMono = Mono.just("pending_review");
        } else {
            statusMono = getSetting("ai").flatMap(aiSetting -> {
                if (!SettingValues.bool(aiSetting, "enabled", false) ||
                    !SettingValues.bool(aiSetting, "enableContentReview", false)) {
                    return Mono.just("pending_review");
                }
                return aiService.reviewContent(content, wish.getSpec().getNickname())
                    .map(passed -> passed ? "approved" : "pending_review");
            }).onErrorResume(e -> {
                log.warn("[Wishboard] AI review failed, fallback to manual: {}", e.getMessage());
                return Mono.just("pending_review");
            });
        }

        return statusMono.flatMap(status -> {
            wish.getSpec().setStatus(status);
            // 只有审核通过才调用 AI 生成暖心回复/情绪标签，避免浪费额度
            Mono<Wish> enrichedMono = "approved".equals(status)
                ? enrichWithAi(wish)
                : Mono.just(wish);
            return enrichedMono.flatMap(enriched ->
                wishService.create(enriched).flatMap(created ->
                    ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                            "message", "approved".equals(status) ? "发布成功" : "已提交，等待审核",
                            "status", status,
                            "wish", created
                        ))
                )
            );
        });
    }

    private Mono<Wish> enrichWithAi(Wish wish) {
        return getSetting("ai").flatMap(aiSetting -> {
            if (!SettingValues.bool(aiSetting, "enabled", false)) {
                return Mono.just(wish);
            }
            String content = wish.getSpec().getContent();

            Mono<String> replyMono = Mono.just("");
            Mono<String> emotionMono = Mono.just("💭");

            if (SettingValues.bool(aiSetting, "enableWarmReply", false)) {
                String prompt = SettingValues.text(aiSetting, "warmReplyPrompt",
                    "你是一个温暖的树洞倾听者。用简短温暖的一句话回应，不超过30字，带一个合适的emoji。");
                replyMono = aiService.generateWarmReply(content, prompt);
            }
            if (SettingValues.bool(aiSetting, "enableEmotionTag", false)) {
                emotionMono = aiService.detectEmotion(content);
            }

            return Mono.zip(replyMono, emotionMono).map(tuple -> {
                wish.getSpec().setAiReply(tuple.getT1());
                wish.getSpec().setEmotionTag(tuple.getT2());
                return wish;
            });
        }).onErrorResume(e -> {
            log.warn("[Wishboard] AI enrich failed, skipping: {}", e.getMessage());
            return Mono.just(wish);
        }).defaultIfEmpty(wish);
    }

    private Mono<ServerResponse> aiPolish(ServerRequest request) {
        // 安全检查：投稿关闭时润色也不可用
        return getSetting("treehole")
            .flatMap(treeholeSetting -> {
                if (!SettingValues.bool(treeholeSetting, "enableSubmit", true)) {
                    return ServerResponse.status(403)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("error", "投稿功能已关闭"));
                }
                return doAiPolish(request);
            })
            .switchIfEmpty(doAiPolish(request));
    }

    private Mono<ServerResponse> doAiPolish(ServerRequest request) {
        return request.bodyToMono(Map.class).flatMap(body -> {
            String content = (String) body.getOrDefault("content", "");
            String nickname = (String) body.getOrDefault("nickname", "");
            if (content.isBlank()) {
                return ServerResponse.badRequest().bodyValue(Map.of("error", "内容不能为空"));
            }
            return getSetting("treehole").flatMap(treeholeSetting -> {
                // 先用敏感词黑名单拦截，避免浪费 AI 额度
                String blockedWords = SettingValues.text(treeholeSetting, "blockedWords", "");
                if (!blockedWords.isBlank()) {
                    for (String word : blockedWords.split("\n")) {
                        String w = word.trim();
                        if (!w.isEmpty() && (content.contains(w) || nickname.contains(w))) {
                            return ServerResponse.badRequest()
                                .bodyValue(Map.of("error", "内容包含违规词，无法润色"));
                        }
                    }
                }
                return getSetting("ai").flatMap(aiSetting -> {
                    if (!SettingValues.bool(aiSetting, "enabled", false)) {
                        return ServerResponse.ok().bodyValue(Map.of("polished", content));
                    }
                    String prompt = "你是一个文案助手。将用户的心愿润色得更有诗意和仪式感，保持原意，不超过50字，可以加emoji。";
                    return aiService.generateWarmReply(content, prompt)
                        .flatMap(polished -> ServerResponse.ok()
                            .bodyValue(Map.of("polished", polished.isBlank() ? content : polished)));
                });
            }).switchIfEmpty(
                // treehole 设置不存在时直接走 AI
                getSetting("ai").flatMap(aiSetting -> {
                    if (!SettingValues.bool(aiSetting, "enabled", false)) {
                        return ServerResponse.ok().bodyValue(Map.of("polished", content));
                    }
                    String prompt = "你是一个文案助手。将用户的心愿润色得更有诗意和仪式感，保持原意，不超过50字，可以加emoji。";
                    return aiService.generateWarmReply(content, prompt)
                        .flatMap(polished -> ServerResponse.ok()
                            .bodyValue(Map.of("polished", polished.isBlank() ? content : polished)));
                })
            );
        });
    }

    /**
     * 去除 HTML 标签，防止存储型 XSS
     */
    private static String stripHtml(String input) {
        if (input == null) return "";
        return input.replaceAll("<[^>]*>", "").trim();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("anonymous.wishboard.aobp.cn/v1alpha1");
    }
}
