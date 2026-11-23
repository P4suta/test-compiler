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

    @Test
    void mapsInlineRangesToTheLogicalKotlinFileFromWalaruCore() {
        SourceMap map = SourceMap.parse(
                "SMAP\nGenerated.kt\nKotlin\n*S Kotlin\n*F\n+ 1 Main.kt\nsrc/main/kotlin/demo/Main.kt\n+ 2 Inline.kt\nsrc/main/kotlin/lib/Inline.kt\n*L\n10#1:20\n7#2,2:30\n*E\n",
                "Generated.kt");

        assertThat(map.position(31).path()).isEqualTo("src/main/kotlin/lib/Inline.kt");
        assertThat(map.position(31).line()).isEqualTo(8);
    }
}
