package com.knowledge.repository.audit;

/**
 * Holds the current request actor (from the X-Actor header) in a ThreadLocal
 * so services can attribute versions and audit rows without threading the
 * value through every method signature.
 */
public final class ActorContext {

    public static final String DEFAULT_ACTOR = "system";

    private static final ThreadLocal<String> ACTOR = ThreadLocal.withInitial(() -> DEFAULT_ACTOR);

    private ActorContext() {
    }

    public static void set(String actor) {
        ACTOR.set(actor == null || actor.isBlank() ? DEFAULT_ACTOR : actor);
    }

    public static String get() {
        return ACTOR.get();
    }

    public static void clear() {
        ACTOR.remove();
    }
}
