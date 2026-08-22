package io.github.p4suta.testcompiler.agent;

import java.util.Locale;
import org.objectweb.asm.Label;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

final class InstrumentingMethodVisitor extends AdviceAdapter {
    private static final Type BRIDGE = Type.getType(AgentBridge.class);
    private static final Method EFFECT = Method.getMethod(
            "boolean effectCall(String,String,String,String,int)");
    private static final Method GLOBAL = Method.getMethod("void globalAccess(String,String,Object)");
    private static final Method NON_CACHEABLE = Method.getMethod("void nonCacheable(String)");

    private final String owner;
    private final String methodName;
    private final String descriptor;
    private final String source;
    private final SourceMap sourceMap;
    private final boolean mutateReturns;
    private final AgentConfiguration configuration;
    private int argumentsLocal = -1;
    private int currentLine = 1;
    private boolean testNgTest;

    InstrumentingMethodVisitor(
            MethodVisitor delegate,
            int access,
            String owner,
            String name,
            String descriptor,
            String source,
            SourceMap sourceMap,
            boolean mutateReturns,
            AgentConfiguration configuration) {
        super(Opcodes.ASM9, delegate, access, name, descriptor);
        this.owner = owner;
        this.methodName = name;
        this.descriptor = descriptor;
        this.source = source;
        this.sourceMap = sourceMap;
        this.mutateReturns = mutateReturns;
        this.configuration = configuration;
    }

    @Override
    protected void onMethodEnter() {
        if (mutateReturns) {
            Type[] arguments = Type.getArgumentTypes(descriptor);
            push(arguments.length);
            newArray(Type.getType(Object.class));
            argumentsLocal = newLocal(Type.getType(Object[].class));
            storeLocal(argumentsLocal);
            for (int index = 0; index < arguments.length; index++) {
                loadLocal(argumentsLocal);
                push(index);
                loadArg(index);
                box(arguments[index]);
                arrayStore(Type.getType(Object.class));
            }
        }
        if (testNgTest) {
            push(testNgId());
            push(owner.replace('/', '.') + "#" + methodName);
            invokeStatic(BRIDGE, Method.getMethod("void testStarted(String,String)"));
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
        if (annotationDescriptor.equals("Lorg/testng/annotations/Test;")) {
            testNgTest = true;
        }
        return super.visitAnnotation(annotationDescriptor, visible);
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        currentLine = Math.max(1, sourceMap.map(line));
        super.visitLineNumber(line, start);
    }

    @Override
    public void visitMethodInsn(int opcode, String calledOwner, String name, String calledDescriptor, boolean isInterface) {
        CallKind callKind = CallKind.classify(calledOwner, name, calledDescriptor, configuration);
        if (callKind.effect()) {
            instrumentEffect(opcode, calledOwner, name, calledDescriptor, isInterface);
            return;
        }
        if (callKind.access() != null) {
            instrumentObservedCall(opcode, calledOwner, name, calledDescriptor, isInterface, callKind);
            return;
        }
        super.visitMethodInsn(opcode, calledOwner, name, calledDescriptor, isInterface);
    }

    @Override
    public void visitFieldInsn(int opcode, String fieldOwner, String name, String fieldDescriptor) {
        if (opcode == GETSTATIC || opcode == PUTSTATIC) {
            push("static-field");
            push(opcode == GETSTATIC ? "READ" : "WRITE");
            push(fieldOwner + "#" + name);
            invokeStatic(BRIDGE, GLOBAL);
        }
        super.visitFieldInsn(opcode, fieldOwner, name, fieldDescriptor);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        int opposite = opposite(opcode);
        if (!mutateReturns || opposite < 0 || argumentsLocal < 0) {
            super.visitJumpInsn(opcode, label);
            return;
        }
        pushMetadata();
        loadLocal(argumentsLocal);
        invokeStatic(BRIDGE, Method.getMethod("boolean branch(String,String,String,String,int,Object[])"));
        Label original = new Label();
        Label done = new Label();
        super.visitJumpInsn(IFEQ, original);
        super.visitJumpInsn(opposite, label);
        goTo(done);
        mark(original);
        super.visitJumpInsn(opcode, label);
        mark(done);
    }

    private int opposite(int opcode) {
        return switch (opcode) {
            case IFEQ -> IFNE;
            case IFNE -> IFEQ;
            case IFLT -> IFGE;
            case IFGE -> IFLT;
            case IFGT -> IFLE;
            case IFLE -> IFGT;
            case IF_ICMPEQ -> IF_ICMPNE;
            case IF_ICMPNE -> IF_ICMPEQ;
            case IF_ICMPLT -> IF_ICMPGE;
            case IF_ICMPGE -> IF_ICMPLT;
            case IF_ICMPGT -> IF_ICMPLE;
            case IF_ICMPLE -> IF_ICMPGT;
            case IF_ACMPEQ -> IF_ACMPNE;
            case IF_ACMPNE -> IF_ACMPEQ;
            case IFNULL -> IFNONNULL;
            case IFNONNULL -> IFNULL;
            default -> -1;
        };
    }

    private void instrumentEffect(int opcode, String calledOwner, String name, String calledDescriptor, boolean isInterface) {
        SpilledCall call = spillCall(opcode, calledDescriptor);
        push(calledOwner);
        push(name);
        push(calledDescriptor);
        push(source);
        push(currentLine);
        invokeStatic(BRIDGE, EFFECT);
        Label execute = new Label();
        Label done = new Label();
        super.visitJumpInsn(IFEQ, execute);
        pushDefault(Type.getReturnType(calledDescriptor));
        goTo(done);
        mark(execute);
        call.reload();
        super.visitMethodInsn(opcode, calledOwner, name, calledDescriptor, isInterface);
        mark(done);
    }

    private void instrumentObservedCall(
            int opcode, String calledOwner, String name, String calledDescriptor, boolean isInterface, CallKind kind) {
        SpilledCall call = spillCall(opcode, calledDescriptor);
        if (kind.nonCacheableReason() != null) {
            push(kind.nonCacheableReason());
            invokeStatic(BRIDGE, NON_CACHEABLE);
        }
        push(kind.category());
        push(kind.access());
        if (call.argumentLocals.length == 0) {
            visitInsn(ACONST_NULL);
        } else {
            loadLocal(call.argumentLocals[0], call.argumentTypes[0]);
            box(call.argumentTypes[0]);
        }
        invokeStatic(BRIDGE, GLOBAL);
        call.reload();
        super.visitMethodInsn(opcode, calledOwner, name, calledDescriptor, isInterface);
    }

    private SpilledCall spillCall(int opcode, String calledDescriptor) {
        Type[] argumentTypes = Type.getArgumentTypes(calledDescriptor);
        int[] argumentLocals = new int[argumentTypes.length];
        for (int index = argumentTypes.length - 1; index >= 0; index--) {
            argumentLocals[index] = newLocal(argumentTypes[index]);
            storeLocal(argumentLocals[index], argumentTypes[index]);
        }
        int receiverLocal = -1;
        if (opcode != INVOKESTATIC) {
            receiverLocal = newLocal(Type.getType(Object.class));
            storeLocal(receiverLocal);
        }
        return new SpilledCall(receiverLocal, argumentLocals, argumentTypes);
    }

    private void pushDefault(Type type) {
        switch (type.getSort()) {
            case Type.VOID -> { }
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> push(0);
            case Type.LONG -> push(0L);
            case Type.FLOAT -> push(0.0f);
            case Type.DOUBLE -> push(0.0d);
            default -> visitInsn(ACONST_NULL);
        }
    }

    @Override
    protected void onMethodExit(int opcode) {
        if (testNgTest) {
            push(testNgId());
            push(opcode == ATHROW ? "FAILED" : "SUCCESSFUL");
            invokeStatic(BRIDGE, Method.getMethod("void testFinished(String,String)"));
        }
        if (!mutateReturns || opcode == ATHROW || argumentsLocal < 0) return;
        Type returnType = Type.getReturnType(descriptor);
        if (returnType.getSort() == Type.VOID) {
            pushMetadata();
            loadLocal(argumentsLocal);
            invokeStatic(BRIDGE, Method.getMethod("void observeVoid(String,String,String,String,int,Object[])") );
            return;
        }
        int valueLocal = newLocal(returnType);
        storeLocal(valueLocal, returnType);
        loadLocal(valueLocal, returnType);
        pushMetadata();
        loadLocal(argumentsLocal);
        Method mutation = switch (returnType.getSort()) {
            case Type.BOOLEAN -> Method.getMethod("boolean mutateBoolean(boolean,String,String,String,String,int,Object[])");
            case Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Method.getMethod("int mutateInt(int,String,String,String,String,int,Object[])");
            case Type.LONG -> Method.getMethod("long mutateLong(long,String,String,String,String,int,Object[])");
            case Type.FLOAT -> Method.getMethod("float mutateFloat(float,String,String,String,String,int,Object[])");
            case Type.DOUBLE -> Method.getMethod("double mutateDouble(double,String,String,String,String,int,Object[])");
            default -> Method.getMethod("Object mutateReference(Object,String,String,String,String,int,Object[])");
        };
        invokeStatic(BRIDGE, mutation);
        if (returnType.getSort() == Type.ARRAY || returnType.getSort() == Type.OBJECT) {
            checkCast(returnType);
        }
    }

    private String testNgId() {
        return "testng:" + owner.replace('/', '.') + "#" + methodName;
    }

    private void pushMetadata() {
        push(owner);
        push(methodName);
        push(descriptor);
        push(source);
        push(currentLine);
    }

    private final class SpilledCall {
        private final int receiverLocal;
        private final int[] argumentLocals;
        private final Type[] argumentTypes;

        SpilledCall(int receiverLocal, int[] argumentLocals, Type[] argumentTypes) {
            this.receiverLocal = receiverLocal;
            this.argumentLocals = argumentLocals;
            this.argumentTypes = argumentTypes;
        }

        void reload() {
            if (receiverLocal >= 0) {
                loadLocal(receiverLocal);
            }
            for (int index = 0; index < argumentLocals.length; index++) {
                loadLocal(argumentLocals[index], argumentTypes[index]);
            }
        }
    }

    private record CallKind(boolean effect, String category, String access, String nonCacheableReason) {
        static CallKind classify(String owner, String name, String descriptor, AgentConfiguration configuration) {
            String lowerOwner = owner.toLowerCase(Locale.ROOT);
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (isEffect(owner, lowerOwner, lowerName) || configuration.isCustomEffect(owner, name)) {
                return new CallKind(true, null, null, null);
            }
            if (owner.equals("java/lang/System") && (name.equals("setProperty") || name.equals("clearProperty"))) {
                return new CallKind(false, "system-property", "WRITE", null);
            }
            if (owner.equals("java/lang/System") && name.equals("getProperty")) {
                return new CallKind(false, "system-property", "READ", null);
            }
            if ((owner.equals("java/util/Locale") || owner.equals("java/util/TimeZone")) && lowerName.contains("default")) {
                return new CallKind(false, owner.substring(owner.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT),
                        lowerName.startsWith("set") ? "WRITE" : "READ", null);
            }
            if (owner.equals("java/nio/file/Files")) {
                boolean write = lowerName.startsWith("write") || lowerName.startsWith("delete")
                        || lowerName.startsWith("create") || lowerName.startsWith("move") || lowerName.startsWith("copy");
                return new CallKind(false, "file", write ? "WRITE" : "READ", null);
            }
            if (owner.equals("java/io/File")) {
                boolean write = lowerName.startsWith("delete") || lowerName.startsWith("create")
                        || lowerName.startsWith("mkdir") || lowerName.startsWith("rename") || lowerName.contains("writ");
                return new CallKind(false, "file", write ? "WRITE" : "READ", null);
            }
            if (lowerOwner.startsWith("java/net/") || lowerOwner.contains("socketchannel")) {
                return new CallKind(false, "network", "READ_WRITE", "network-access");
            }
            if (owner.equals("java/lang/System") && (name.equals("load") || name.equals("loadLibrary"))) {
                return new CallKind(false, "native", "READ", "native-access");
            }
            return new CallKind(false, null, null, null);
        }

        private static boolean isEffect(String owner, String lowerOwner, String lowerName) {
            if (lowerOwner.contains("slf4j") || lowerOwner.contains("log4j") || lowerOwner.contains("micrometer")) {
                return false;
            }
            return (owner.equals("org/springframework/context/ApplicationEventPublisher") && lowerName.equals("publishevent"))
                    || (lowerOwner.startsWith("java/sql/") && (lowerName.startsWith("execute") || lowerName.equals("commit")))
                    || (lowerOwner.contains("messaging") && (lowerName.contains("send") || lowerName.contains("publish")));
        }
    }
}
