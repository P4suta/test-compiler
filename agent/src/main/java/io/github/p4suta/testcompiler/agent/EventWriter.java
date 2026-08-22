package io.github.p4suta.testcompiler.agent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

final class EventWriter implements AutoCloseable {
    private final BufferedWriter writer;

    EventWriter(Path path) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    synchronized void write(Map<String, ?> fields) {
        try {
            writer.write('{');
            boolean first = true;
            for (Map.Entry<String, ?> field : fields.entrySet()) {
                if (!first) {
                    writer.write(',');
                }
                first = false;
                writeString(field.getKey());
                writer.write(':');
                writeValue(field.getValue());
            }
            writer.write("}\n");
            writer.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write test-compiler event stream", exception);
        }
    }

    private void writeValue(Object value) throws IOException {
        if (value == null) {
            writer.write("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            writer.write(value.toString());
        } else if (value instanceof Iterable<?> values) {
            writer.write('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) {
                    writer.write(',');
                }
                first = false;
                writeValue(item);
            }
            writer.write(']');
        } else {
            writeString(value.toString());
        }
    }

    private void writeString(String value) throws IOException {
        writer.write('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> writer.write("\\\"");
                case '\\' -> writer.write("\\\\");
                case '\n' -> writer.write("\\n");
                case '\r' -> writer.write("\\r");
                case '\t' -> writer.write("\\t");
                default -> {
                    if (character < 0x20) {
                        writer.write(String.format("\\u%04x", (int) character));
                    } else {
                        writer.write(character);
                    }
                }
            }
        }
        writer.write('"');
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }
}
