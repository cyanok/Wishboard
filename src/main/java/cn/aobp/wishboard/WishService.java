package cn.aobp.wishboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import cn.aobp.wishboard.model.PublicWish;
import cn.aobp.wishboard.model.Wish;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishService {

    private final ReactiveExtensionClient client;

    /** IP 限频记录: ip -> (timestamp, count) */
    private final Map<String, long[]> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * 分页获取公开便签
     */
    public Mono<ListResult<PublicWish>> listPublic(PublicWishQuery query) {
        var options = ListOptions.builder()
            .andQuery(Queries.in("spec.status", PublicWishQuery.PUBLIC_STATUSES))
            .andQuery(Queries.isNull("metadata.deletionTimestamp"));
        if (query.getType() != null) {
            options.andQuery(Queries.equal("spec.type", query.getType()));
        }
        if (query.getStatus() != null) {
            options.andQuery(Queries.equal("spec.status", query.getStatus()));
        }
        var direction = "createdAt,asc".equals(query.getSort()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var sort = Sort.by(direction, "spec.createdAt").and(Sort.by("metadata.name"));
        return client.listBy(Wish.class, options.build(),
                PageRequestImpl.of(query.getPage(), query.getSize(), sort))
            .map(result -> new ListResult<>(result.getPage(), result.getSize(), result.getTotal(),
                result.getItems().stream().map(PublicWish::from).toList()));
    }

    public Flux<Wish> listApproved() {
        return client.listAll(Wish.class, ListOptions.builder().build(), null)
            .filter(w -> {
                String status = w.getSpec().getStatus();
                // 排除待审核和已拒绝的，其余都展示
                return !"pending_review".equals(status) && !"rejected".equals(status);
            });
    }

    public Flux<Wish> listAll() {
        return client.listAll(Wish.class, ListOptions.builder().build(), null);
    }

    public Flux<Wish> listPendingReview() {
        return client.listAll(Wish.class, ListOptions.builder().build(), null)
            .filter(w -> "pending_review".equals(w.getSpec().getStatus()));
    }

    public Mono<Wish> create(Wish wish) {
        var spec = wish.getSpec();
        if (spec.getCreatedAt() == null) {
            spec.setCreatedAt(Instant.now());
        }
        if (spec.getColor() == null || spec.getColor().isBlank()) {
            spec.setColor(randomColor());
        }
        wish.getMetadata().setName(UUID.randomUUID().toString());
        return client.create(wish);
    }

    public Mono<Wish> update(Wish wish) {
        return client.update(wish);
    }

    public Mono<Wish> get(String name) {
        return client.get(Wish.class, name);
    }

    public Mono<Void> delete(String name) {
        return client.get(Wish.class, name)
            .flatMap(client::delete)
            .then();
    }

    /**
     * 批量删除便签
     */
    public Mono<Void> batchDelete(java.util.List<String> names) {
        return Flux.fromIterable(names)
            .flatMap(name -> client.get(Wish.class, name)
                .flatMap(client::delete)
                .onErrorResume(e -> {
                    log.warn("批量删除便签失败: {}", name, e);
                    return Mono.empty();
                }))
            .then();
    }

    /**
     * 删除某个类型下的所有便签
     */
    public Mono<Void> deleteByType(String typeSlug) {
        return client.listAll(Wish.class, ListOptions.builder().build(), null)
            .filter(w -> typeSlug.equals(w.getSpec().getType()))
            .flatMap(client::delete)
            .then();
    }


    /**
     * 检查 IP 限频
     * @return true 表示允许，false 表示超限
     */
    public boolean checkRateLimit(String ip, int maxPerHour) {
        long now = System.currentTimeMillis();
        long oneHour = 3600_000L;
        return rateLimitMap.compute(ip, (k, v) -> {
            if (v == null || (now - v[0]) > oneHour) {
                return new long[]{now, 1};
            }
            v[1]++;
            return v;
        })[1] <= maxPerHour;
    }

    private String randomColor() {
        String[] colors = {"pink", "blue", "yellow", "green", "purple"};
        return colors[(int) (Math.random() * colors.length)];
    }
}
