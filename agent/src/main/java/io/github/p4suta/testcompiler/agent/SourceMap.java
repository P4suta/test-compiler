package io.github.p4suta.testcompiler.agent;

import java.util.ArrayList;
import java.util.List;

/** Minimal JSR-45 SMAP line mapper; unknown forms safely fall back to bytecode lines. */
record SourceMap(String defaultSource, List<Entry> entries) {
    static SourceMap identity() {
        return new SourceMap("unknown", List.of());
    }

    static SourceMap parse(String debug, String defaultSource) {
        if (debug == null || !debug.startsWith("SMAP")) {
            return new SourceMap(defaultSource, List.of());
        }
        List<Entry> result = new ArrayList<>();
        String[] lines = debug.split("\\R");
        boolean lineSection = false;
        for (String line : lines) {
            if (line.equals("*L")) {
                lineSection = true;
                continue;
            }
            if (line.startsWith("*")) {
                lineSection = false;
                continue;
            }
            if (!lineSection) continue;
            try {
                String[] sides = line.split(":", 2);
                String inputPart = sides[0];
                int fileMarker = inputPart.indexOf('#');
                if (fileMarker >= 0) {
                    int repeatMarker = inputPart.indexOf(',', fileMarker);
                    inputPart = inputPart.substring(0, fileMarker)
                            + (repeatMarker >= 0 ? inputPart.substring(repeatMarker) : "");
                }
                String[] outputParts = sides[1].split(",", 2);
                int input = Integer.parseInt(inputPart.split(",", 2)[0]);
                int output = Integer.parseInt(outputParts[0]);
                int repeat = inputPart.contains(",") ? Integer.parseInt(inputPart.substring(inputPart.indexOf(',') + 1)) : 1;
                int increment = outputParts.length == 2 ? Integer.parseInt(outputParts[1]) : 1;
                result.add(new Entry(input, output, repeat, increment));
            } catch (RuntimeException ignored) {
                // Preserve useful bytecode line information when a vendor extension is unknown.
            }
        }
        return new SourceMap(defaultSource, List.copyOf(result));
    }

    int map(int outputLine) {
        for (Entry entry : entries) {
            int end = entry.outputStart() + (entry.repeat() - 1) * entry.outputIncrement();
            if (outputLine >= entry.outputStart() && outputLine <= end) {
                return entry.inputStart() + (outputLine - entry.outputStart()) / entry.outputIncrement();
            }
        }
        return outputLine;
    }

    private record Entry(int inputStart, int outputStart, int repeat, int outputIncrement) {}
}
