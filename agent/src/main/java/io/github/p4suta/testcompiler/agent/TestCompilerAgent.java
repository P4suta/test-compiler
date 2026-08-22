package io.github.p4suta.testcompiler.agent;

import java.lang.instrument.Instrumentation;

/** Installs the single-pass transformer. Challenge selection remains a runtime operation. */
public final class TestCompilerAgent {
    private TestCompilerAgent() {}

    public static void premain(String ignoredArguments, Instrumentation instrumentation) {
        AgentBridge.initializeFromSystemProperties();
        instrumentation.addTransformer(new TestCompilerTransformer(AgentConfiguration.fromSystemProperties()), false);
    }
}
