package io.github.p4suta.testcompiler.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceMapTest {
    @Test
    void mapsKotlinSmapOutputLines() {
        SourceMap map = SourceMap.parse("SMAP\nExample.kt\nKotlin\n*L\n7#1,3:20\n*E\n", "Example.kt");

        assertThat(map.map(20)).isEqualTo(7);
        assertThat(map.map(22)).isEqualTo(9);
        assertThat(map.map(100)).isEqualTo(100);
    }
}
