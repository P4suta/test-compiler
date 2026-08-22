package io.github.p4suta.testcompiler.agent;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

record AgentConfiguration(List<Path> productionRoots, List<Path> testRoots, List<EffectPattern> customEffects) {
    static AgentConfiguration fromSystemProperties() {
        return new AgentConfiguration(
                parseRoots(System.getProperty("testcompiler.productionRoots", "")),
                parseRoots(System.getProperty("testcompiler.testRoots", "")),
                parseEffects(System.getProperty("testcompiler.customEffects", "")));
    }

    private static List<Path> parseRoots(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .filter(part -> !part.isBlank())
                .map(AgentConfiguration::toPath)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    private static Path toPath(String value) {
        if (value.startsWith("file:")) {
            return Path.of(URI.create(value));
        }
        return Path.of(value);
    }

    private static List<EffectPattern> parseEffects(String value) {
        if (value.isBlank()) return List.of();
        return Arrays.stream(value.split(";"))
                .map(String::trim)
                .filter(part -> part.contains("#"))
                .map(part -> new EffectPattern(part.substring(0, part.indexOf('#')).replace('.', '/'),
                        part.substring(part.indexOf('#') + 1)))
                .toList();
    }

    boolean isCustomEffect(String owner, String method) {
        return customEffects.stream().anyMatch(pattern -> pattern.owner().equals(owner) && pattern.method().equals(method));
    }

    CodeKind codeKind(Path location) {
        Path normalized = location.toAbsolutePath().normalize();
        if (matches(testRoots, normalized)) {
            return CodeKind.TEST;
        }
        if (matches(productionRoots, normalized)) {
            return CodeKind.PRODUCTION;
        }
        return CodeKind.OTHER;
    }

    private static boolean matches(List<Path> roots, Path location) {
        return roots.stream().anyMatch(root -> location.startsWith(root) || root.startsWith(location));
    }

    enum CodeKind { PRODUCTION, TEST, OTHER }

    record EffectPattern(String owner, String method) {}
}
