package io.github.p4suta.testcompiler.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class InstrumentingClassVisitor extends ClassVisitor {
    private final String owner;
    private final boolean mutateReturns;
    private final AgentConfiguration configuration;
    private String source = "unknown";
    private SourceMap sourceMap = SourceMap.identity();

    InstrumentingClassVisitor(
            ClassVisitor delegate, String owner, boolean mutateReturns, AgentConfiguration configuration) {
        super(Opcodes.ASM9, delegate);
        this.owner = owner;
        this.mutateReturns = mutateReturns;
        this.configuration = configuration;
    }

    @Override
    public void visitSource(String sourceName, String debug) {
        if (sourceName != null) {
            source = sourceName;
        }
        sourceMap = SourceMap.parse(debug, source);
        super.visitSource(sourceName, debug);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (delegate == null || (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return delegate;
        }
        return new InstrumentingMethodVisitor(
                delegate, access, owner, name, descriptor, source, sourceMap, mutateReturns, configuration);
    }
}
