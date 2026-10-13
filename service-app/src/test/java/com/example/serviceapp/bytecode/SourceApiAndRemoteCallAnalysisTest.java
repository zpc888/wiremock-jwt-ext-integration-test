package com.example.serviceapp.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import org.junit.jupiter.api.Test;

class SourceApiAndRemoteCallAnalysisTest {

    @Test
    void listsControllerMappingsAndRemoteCallInvocations() throws Exception {
        List<CompilationUnitTree> units = parseMainSources();
        Set<String> remoteCallFields = remoteCallFields(units);
        List<String> mappings = new ArrayList<>();
        List<String> calls = new ArrayList<>();

        for (CompilationUnitTree unit : units) {
            new TreePathScanner<Void, Void>() {
                private String className;
                private String methodName;
                private boolean controller;

                @Override
                public Void visitClass(ClassTree tree, Void unused) {
                    String previousClass = className;
                    boolean previousController = controller;
                    className = tree.getSimpleName().toString();
                    controller = className.endsWith("Controller");
                    super.visitClass(tree, unused);
                    className = previousClass;
                    controller = previousController;
                    return null;
                }

                @Override
                public Void visitMethod(MethodTree tree, Void unused) {
                    String previousMethod = methodName;
                    methodName = tree.getName().toString();
                    if (controller) {
                        tree.getModifiers().getAnnotations().stream()
                                .filter(annotation -> Set.of("GetMapping", "PostMapping", "RequestMapping")
                                        .contains(annotation.getAnnotationType().toString()))
                                .forEach(annotation -> mappings.add(mappingRecord(className, methodName, annotation)));
                    }
                    super.visitMethod(tree, unused);
                    methodName = previousMethod;
                    return null;
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                    if (tree.getMethodSelect() instanceof MemberSelectTree select
                            && remoteCallFields.contains(select.getExpression().toString())
                            && methodName != null) {
                        calls.add(className + "#" + methodName + " | " + select.getIdentifier() + " | "
                                + tree.getArguments().stream().map(Object::toString).collect(Collectors.joining(", ")));
                    }
                    return super.visitMethodInvocation(tree, unused);
                }
            }.scan(unit, null);
        }

        mappings.sort(String::compareTo);
        calls.sort(String::compareTo);
        assertThat(mappings).containsExactly(
                "CodeAnalysisController#addResource | PostMapping | \"/api/bytecode/resources\"",
                "CodeAnalysisController#getResource | GetMapping | \"/api/bytecode/resources/{resourceId}\"",
                "CodeAnalysisController#updateResource | RequestMapping | path = \"/api/bytecode/resources/{resourceId}\", method = RequestMethod.PATCH",
                "JwtItController#privateApi | GetMapping | \"/api/private\"",
                "JwtItController#publicApi | GetMapping | \"/api/public\"");
        assertThat(calls).containsExactly(
                "MimicRemoteCall#method1 | get | \"/a/b/c1\", Res1.class",
                "MimicRemoteCall#method1 | post | \"/x/y/z1\", new Req1(), Res1.class",
                "MimicRemoteCall#method2 | get | \"/a/b/c2\", Res2.class, Map.of(\"q1\", \"v1\"), Map.of(\"h1\", \"v1\")",
                "MimicRemoteCall#method3 | post | \"/x/y/z2\", new Req2(), Res2.class, Map.of(\"q2\", \"v2\"), Map.of(\"h2\", \"v2\")");
    }

    private static String mappingRecord(String className, String methodName, AnnotationTree annotation) {
        return className + "#" + methodName + " | " + annotation.getAnnotationType() + " | "
                + annotation.getArguments().stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    private static Set<String> remoteCallFields(List<CompilationUnitTree> units) {
        return units.stream().flatMap(unit -> {
            List<String> fields = new ArrayList<>();
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitVariable(VariableTree tree, Void unused) {
                    if (tree.getType() != null && "RemoteCallInterface".equals(tree.getType().toString())) {
                        fields.add(tree.getName().toString());
                    }
                    return super.visitVariable(tree, unused);
                }
            }.scan(unit, null);
            return fields.stream();
        }).collect(Collectors.toSet());
    }

    private static List<CompilationUnitTree> parseMainSources() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<Path> sources;
        try (var paths = Files.walk(sourceRoot)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).sorted(Comparator.naturalOrder()).toList();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(
                    null, files, null, List.of("-proc:none"), null, files.getJavaFileObjectsFromPaths(sources));
            List<CompilationUnitTree> units = new ArrayList<>();
            task.parse().forEach(units::add);
            return units;
        }
    }
}
