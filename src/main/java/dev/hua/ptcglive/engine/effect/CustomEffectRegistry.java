package dev.hua.ptcglive.engine.effect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit registry for the small audited set that truly cannot use standard components. */
public final class CustomEffectRegistry {
    private final Map<String, CustomEffectHandler> handlers = new LinkedHashMap<>();

    public void register(String handlerId, CustomEffectHandler handler) {
        if (handlerId == null || !handlerId.matches("[a-z][a-z0-9_-]*"))
            throw new IllegalArgumentException("CustomEffect handler ID 无效");
        if (handlers.putIfAbsent(handlerId, Objects.requireNonNull(handler, "CustomEffect handler")) != null)
            throw new IllegalArgumentException("重复 CustomEffect handler：" + handlerId);
    }

    public Optional<CustomEffectHandler> find(String handlerId) { return Optional.ofNullable(handlers.get(handlerId)); }
    public boolean contains(String handlerId) { return handlers.containsKey(handlerId); }
    public Map<String, CustomEffectHandler> handlers() { return Map.copyOf(handlers); }
}
