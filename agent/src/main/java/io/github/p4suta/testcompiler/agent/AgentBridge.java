package io.github.p4suta.testcompiler.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime boundary called by instrumented bytecode and by framework adapters. */
public final class AgentBridge {
    private static final InheritableThreadLocal<TestContext> CURRENT_TEST = new InheritableThreadLocal<>();
    private static volatile ChallengeSelection selection = ChallengeSelection.fromSystemProperties();
    private static volatile EventWriter events;

    private AgentBridge() {}

    static void initializeFromSystemProperties() {
        String eventFile = System.getProperty("testcompiler.eventFile", "");
        if (eventFile.isBlank()) {
            return;
        }
        try {
            events = new EventWriter(Path.of(eventFile));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    EventWriter current = events;
                    if (current != null) {
                        current.close();
                    }
                } catch (IOException ignored) {
                    // The worker process result remains authoritative.
                }
            }, "test-compiler-event-close"));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot initialize test-compiler event stream", exception);
        }
    }

    public static void testStarted(String uniqueId, String publicName) {
        CURRENT_TEST.set(new TestContext(uniqueId, publicName));
        write(event("TEST_START").with("test", uniqueId).with("testName", publicName).values());
    }

    public static void testFinished(String uniqueId, String status) {
        TestContext context = CURRENT_TEST.get();
        write(event("TEST_FINISH")
                .with("test", uniqueId)
                .with("testName", context == null ? uniqueId : context.publicName())
                .with("status", status)
                .values());
        CURRENT_TEST.remove();
    }

    public static boolean mutateBoolean(
            boolean value, String owner, String method, String descriptor, String source, int line, Object[] arguments) {
        recordReturn(owner, method, descriptor, source, line, "BOOLEAN", arguments);
        return isSelected(owner, method, descriptor, "BOOLEAN_NEGATE", line) ? !value : value;
    }

    public static int mutateInt(
            int value, String owner, String method, String descriptor, String source, int line, Object[] arguments) {
        recordReturn(owner, method, descriptor, source, line, "INT", arguments);
        return isSelected(owner, method, descriptor, "ZERO_RETURN", line) ? (value == 0 ? 1 : 0) : value;
    }

    public static long mutateLong(
            long value, String owner, String method, String descriptor, String source, int line, Object[] arguments) {
        recordReturn(owner, method, descriptor, source, line, "LONG", arguments);
        return isSelected(owner, method, descriptor, "ZERO_RETURN", line) ? (value == 0L ? 1L : 0L) : value;
    }

    public static float mutateFloat(
            float value, String owner, String method, String descriptor, String source, int line, Object[] arguments) {
        recordReturn(owner, method, descriptor, source, line, "FLOAT", arguments);
        return isSelected(owner, method, descriptor, "ZERO_RETURN", line) ? (value == 0.0f ? 1.0f : 0.0f) : value;
    }

    public static double mutateDouble(
            double value, String owner, String method, String descriptor, String source, int line, Object[] arguments) {
        recordReturn(owner, method, descriptor, source, line, "DOUBLE", arguments);
        return isSelected(owner, method, descriptor, "ZERO_RETURN", line) ? (value == 0.0d ? 1.0d : 0.0d) : value;
    }

    public static Object mutateReference(
            Object value, String owner, String method, String descriptor, String source, int line, Object[] arguments) {
        String kind = value instanceof String ? "STRING" : value == null ? "NULL" : "REFERENCE";
        recordReturn(owner, method, descriptor, source, line, kind, arguments);
        if (value instanceof String && isSelected(owner, method, descriptor, "EMPTY_STRING", line)) {
            return "";
        }
        return isSelected(owner, method, descriptor, "NULL_RETURN", line) ? null : value;
    }

    public static void observeVoid(
            String owner, String method, String descriptor, String source, int line, Object[] arguments) {
        recordReturn(owner, method, descriptor, source, line, "VOID", arguments);
    }

    public static boolean effectCall(String owner, String method, String descriptor, String source, int line) {
        TestContext test = CURRENT_TEST.get();
        if (test == null) {
            return false;
        }
        write(event("EFFECT")
                .with("test", test.uniqueId())
                .with("testName", test.publicName())
                .with("owner", owner)
                .with("method", method)
                .with("descriptor", descriptor)
                .with("source", source)
                .with("line", line)
                .values());
        return isSelected(owner, method, descriptor, "SUPPRESS_EFFECT", line);
    }

    public static boolean branch(
            String owner, String method, String descriptor, String source, int line, Object[] arguments) {
        TestContext test = CURRENT_TEST.get();
        if (test != null) {
            List<String> argumentTypes = new ArrayList<>(arguments.length);
            for (Object argument : arguments) {
                argumentTypes.add(argument == null ? "nullable" : publicType(argument.getClass()));
            }
            write(event("BRANCH")
                    .with("test", test.uniqueId())
                    .with("testName", test.publicName())
                    .with("owner", owner)
                    .with("method", method)
                    .with("descriptor", descriptor)
                    .with("source", source)
                    .with("line", Math.max(1, line))
                    .with("valueKind", "BRANCH")
                    .with("argumentTypes", argumentTypes)
                    .values());
        }
        return isSelected(owner, method, descriptor, "NEGATE_BRANCH", line);
    }

    public static void globalAccess(String category, String access, Object key) {
        TestContext test = CURRENT_TEST.get();
        if (test == null) {
            return;
        }
        write(event("GLOBAL_ACCESS")
                .with("test", test.uniqueId())
                .with("testName", test.publicName())
                .with("category", category)
                .with("access", access)
                .with("keyHash", key == null ? "none" : shortHash(key.toString()))
                .values());
    }

    public static void nonCacheable(String reason) {
        TestContext test = CURRENT_TEST.get();
        if (test != null) {
            write(event("NON_CACHEABLE")
                    .with("test", test.uniqueId())
                    .with("testName", test.publicName())
                    .with("reason", reason)
                    .values());
        }
    }

    private static void recordReturn(
            String owner,
            String method,
            String descriptor,
            String source,
            int line,
            String valueKind,
            Object[] arguments) {
        TestContext test = CURRENT_TEST.get();
        if (test == null || method.equals("<clinit>")) {
            return;
        }
        List<String> argumentTypes = new ArrayList<>(arguments.length);
        for (Object argument : arguments) {
            argumentTypes.add(argument == null ? "nullable" : publicType(argument.getClass()));
        }
        write(event("METHOD_RETURN")
                .with("test", test.uniqueId())
                .with("testName", test.publicName())
                .with("owner", owner)
                .with("method", method)
                .with("descriptor", descriptor)
                .with("source", source)
                .with("line", Math.max(1, line))
                .with("valueKind", valueKind)
                .with("argumentTypes", argumentTypes)
                .values());
    }

    private static boolean isSelected(String owner, String method, String descriptor, String operator, int line) {
        return CURRENT_TEST.get() != null && selection.matches(owner, method, descriptor, operator, line);
    }

    private static String publicType(Class<?> type) {
        if (type.isArray()) {
            return type.getComponentType().getTypeName() + "[]";
        }
        if (type.isEnum()) {
            return "enum:" + type.getName();
        }
        if (type.isRecord()) {
            return "record:" + type.getName();
        }
        boolean kotlinMetadata = java.util.Arrays.stream(type.getDeclaredAnnotations())
                .anyMatch(annotation -> annotation.annotationType().getName().equals("kotlin.Metadata"));
        boolean dataShape = java.util.Arrays.stream(type.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("copy") || method.getName().startsWith("component"));
        if (kotlinMetadata && dataShape) {
            return "data:" + type.getName();
        }
        return type.getName();
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                result.append(String.format("%02x", digest[index]));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Event event(String type) {
        return new Event().with("v", 1).with("type", type);
    }

    private static void write(Map<String, ?> values) {
        EventWriter current = events;
        if (current != null) {
            current.write(values);
        }
    }

    private record TestContext(String uniqueId, String publicName) {}

    private static final class Event {
        private final Map<String, Object> values = new LinkedHashMap<>();

        Event with(String key, Object value) {
            values.put(key, value);
            return this;
        }

        Map<String, Object> values() {
            return values;
        }
    }
}
