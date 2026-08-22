package io.github.p4suta.testcompiler.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

final class TestCompilerTransformer implements ClassFileTransformer {
    private static final String OWN_PACKAGE = "io/github/p4suta/testcompiler/";
    private final AgentConfiguration configuration;

    TestCompilerTransformer(AgentConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        if (className == null || className.startsWith(OWN_PACKAGE) || protectionDomain == null
                || protectionDomain.getCodeSource() == null) {
            return null;
        }
        try {
            AgentConfiguration.CodeKind kind = configuration.codeKind(
                    java.nio.file.Path.of(protectionDomain.getCodeSource().getLocation().toURI()));
            if (kind == AgentConfiguration.CodeKind.OTHER) {
                return null;
            }
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, loader);
            ClassVisitor visitor = new InstrumentingClassVisitor(
                    writer, className, kind == AgentConfiguration.CodeKind.PRODUCTION, configuration);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable failure) {
            AgentBridge.nonCacheable("instrumentation-error:" + className);
            return null;
        }
    }

    private static final class SafeClassWriter extends ClassWriter {
        private final ClassLoader loader;

        SafeClassWriter(ClassReader reader, int flags, ClassLoader loader) {
            super(reader, flags);
            this.loader = loader;
        }

        @Override
        protected String getCommonSuperClass(String left, String right) {
            try {
                Class<?> leftClass = Class.forName(left.replace('/', '.'), false, loader);
                Class<?> rightClass = Class.forName(right.replace('/', '.'), false, loader);
                if (leftClass.isAssignableFrom(rightClass)) return left;
                if (rightClass.isAssignableFrom(leftClass)) return right;
                if (leftClass.isInterface() || rightClass.isInterface()) return "java/lang/Object";
                do {
                    leftClass = leftClass.getSuperclass();
                } while (leftClass != null && !leftClass.isAssignableFrom(rightClass));
                return leftClass == null ? "java/lang/Object" : leftClass.getName().replace('.', '/');
            } catch (Throwable ignored) {
                return "java/lang/Object";
            }
        }
    }
}
