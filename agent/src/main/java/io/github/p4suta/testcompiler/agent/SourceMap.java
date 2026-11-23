package io.github.p4suta.testcompiler.agent;

import io.github.p4suta.walaru.model.KotlinSourceMap;

/** Compatibility facade over the JVM execution model owned by Walaru. */
record SourceMap(KotlinSourceMap delegate) {
    static SourceMap identity() {
        return parse(null, "unknown");
    }

    static SourceMap parse(String debug, String defaultSource) {
        return new SourceMap(KotlinSourceMap.parse(debug, defaultSource));
    }

    int map(int outputLine) {
        return position(outputLine).line();
    }

    Position position(int outputLine) {
        KotlinSourceMap.Position position = delegate.position(outputLine);
        return new Position(position.path(), position.line());
    }

    record Position(String path, int line) {}
}
