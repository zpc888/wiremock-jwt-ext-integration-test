package com.example.serviceapp.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class BytecodeApiAndRemoteCallAnalysisTest {

    private static final String REMOTE_CALL_INTERFACE =
            "com/example/serviceapp/bytecode/RemoteCallInterface";

    @Test
    void listsControllerMappingsAndRemoteCallInvocationsFromBytecode() throws IOException {
        List<String> mappings = new ArrayList<>();
        List<String> calls = new ArrayList<>();

        try (var paths = Files.walk(Path.of("build/classes/java/main"))) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                new ClassReader(Files.readAllBytes(classFile)).accept(new Scanner(mappings, calls), ClassReader.SKIP_DEBUG);
            }
        }

        mappings.sort(String::compareTo);
        calls.sort(String::compareTo);
        assertThat(mappings).containsExactly(
                "CodeAnalysisController#addResource | PostMapping | value=[/api/bytecode/resources]",
                "CodeAnalysisController#getResource | GetMapping | value=[/api/bytecode/resources/{resourceId}]",
                "CodeAnalysisController#updateResource | RequestMapping | path=[/api/bytecode/resources/{resourceId}], method=[RequestMethod.PATCH]",
                "JwtItController#privateApi | GetMapping | value=[/api/private]",
                "JwtItController#publicApi | GetMapping | value=[/api/public]");
        assertThat(calls).containsExactly(
                "MimicRemoteCall#method1 | get | String(/a/b/c1), Class(Res1)",
                "MimicRemoteCall#method1 | post | String(/x/y/z1), new Req1(), Class(Res1)",
                "MimicRemoteCall#method2 | get | String(/a/b/c2), Class(Res2), Map.of(q1, v1), Map.of(h1, v1)",
                "MimicRemoteCall#method3 | post | String(/x/y/z2), new Req2(), Class(Res2), Map.of(q2, v2), Map.of(h2, v2)");
    }

    private static final class Scanner extends ClassVisitor {
        private final List<String> mappings;
        private final List<String> calls;
        private String className;

        private Scanner(List<String> mappings, List<String> calls) {
            super(Opcodes.ASM9);
            this.mappings = mappings;
            this.calls = calls;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            className = simpleName(name);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (className.endsWith("Controller")) {
                return new MappingMethodVisitor(className, name, mappings);
            }
            return new RemoteCallMethodVisitor(className, name, calls);
        }
    }

    private static final class MappingMethodVisitor extends MethodVisitor {
        private final String className;
        private final String methodName;
        private final List<String> mappings;

        private MappingMethodVisitor(String className, String methodName, List<String> mappings) {
            super(Opcodes.ASM9);
            this.className = className;
            this.methodName = methodName;
            this.mappings = mappings;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String annotation = switch (descriptor) {
                case "Lorg/springframework/web/bind/annotation/GetMapping;" -> "GetMapping";
                case "Lorg/springframework/web/bind/annotation/PostMapping;" -> "PostMapping";
                case "Lorg/springframework/web/bind/annotation/RequestMapping;" -> "RequestMapping";
                default -> null;
            };
            return annotation == null ? null : new MappingAnnotationVisitor(
                    annotation, value -> mappings.add(className + "#" + methodName + " | " + annotation + " | " + value));
        }
    }

    private static final class MappingAnnotationVisitor extends AnnotationVisitor {
        private final StringBuilder values = new StringBuilder();
        private final java.util.function.Consumer<String> completion;

        private MappingAnnotationVisitor(String annotation, java.util.function.Consumer<String> completion) {
            super(Opcodes.ASM9);
            this.completion = completion;
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return new AnnotationVisitor(Opcodes.ASM9) {
                private final List<String> entries = new ArrayList<>();

                @Override
                public void visit(String ignored, Object value) {
                    entries.add(String.valueOf(value));
                }

                @Override
                public void visitEnum(String ignored, String descriptor, String value) {
                    entries.add(simpleName(Type.getType(descriptor).getInternalName()) + "." + value);
                }

                @Override
                public void visitEnd() {
                    append(name + "=[" + String.join(", ", entries) + "]");
                }
            };
        }

        @Override
        public void visitEnd() {
            completion.accept(values.toString());
        }

        private void append(String value) {
            if (!values.isEmpty()) {
                values.append(", ");
            }
            values.append(value);
        }
    }

    private static final class RemoteCallMethodVisitor extends MethodVisitor {
        private final String className;
        private final String methodName;
        private final List<String> calls;
        private final List<String> arguments = new ArrayList<>();
        private boolean readingArguments;

        private RemoteCallMethodVisitor(String className, String methodName, List<String> calls) {
            super(Opcodes.ASM9);
            this.className = className;
            this.methodName = methodName;
            this.calls = calls;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.GETFIELD && REMOTE_CALL_INTERFACE.equals(Type.getType(descriptor).getInternalName())) {
                readingArguments = true;
                arguments.clear();
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (readingArguments) {
                arguments.add(value instanceof Type type ? "Class(" + simpleName(type.getInternalName()) + ")"
                        : "String(" + value + ")");
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (readingArguments && opcode == Opcodes.NEW) {
                arguments.add("new " + simpleName(type) + "()");
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (readingArguments && opcode == Opcodes.INVOKESTATIC && owner.equals("java/util/Map") && name.equals("of")) {
                String value = arguments.removeLast();
                String key = arguments.removeLast();
                arguments.add("Map.of(" + key.substring(7, key.length() - 1) + ", "
                        + value.substring(7, value.length() - 1) + ")");
            }
            if (opcode == Opcodes.INVOKEINTERFACE && owner.equals(REMOTE_CALL_INTERFACE)) {
                calls.add(className + "#" + methodName + " | " + name + " | " + String.join(", ", arguments));
                readingArguments = false;
                arguments.clear();
            }
        }
    }

    private static String simpleName(String internalName) {
        String className = internalName.substring(internalName.lastIndexOf('/') + 1);
        return className.substring(className.lastIndexOf('$') + 1);
    }
}
