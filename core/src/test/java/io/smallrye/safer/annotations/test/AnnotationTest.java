package io.smallrye.safer.annotations.test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.smallrye.safer.annotations.SaferAnnotationProcessor;

public class AnnotationTest {

    static class ExpectedError {
        long line;
        long column;
        String message;

        public ExpectedError(long line, long column, String message) {
            this.line = line;
            this.column = column;
            this.message = message;
        }

        @Override
        public String toString() {
            return "[" + line + ":" + column + "] " + message;
        }

        @Override
        public int hashCode() {
            return Objects.hash(line, column, message);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || obj instanceof ExpectedError == false)
                return false;
            ExpectedError o = (ExpectedError) obj;
            return line == o.line
                    && column == o.column
                    && Objects.equals(message, o.message);
        }
    }

    @Test
    public void testValid() throws IOException {
        compile(Collections.emptySet(), Valid.class);
    }

    private void compile(Set<ExpectedError> errors, Class<?>... classes) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Set<ExpectedError> receivedErrors = new HashSet<>();
        DiagnosticListener<? super JavaFileObject> diagnosticListener = new DiagnosticListener<JavaFileObject>() {
            @Override
            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                ExpectedError error = new ExpectedError(diagnostic.getLineNumber(), diagnostic.getColumnNumber(),
                        diagnostic.getMessage(Locale.ENGLISH));
                receivedErrors.add(error);
                // use this if you modify errors to get the new list of expected errors
                //                System.err.println("new ExpectedError("+error.line+", "+error.column+", \""+error.message+"\"),");
            }
        };
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticListener, Locale.ENGLISH,
                StandardCharsets.UTF_8);
        fileManager.setLocation(StandardLocation.SOURCE_PATH, Arrays.asList(new File("src/test/java")));
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(new File("target/test-classes")));

        List<JavaFileObject> files = new ArrayList<>();
        for (Class<?> klass : classes) {
            files.add(fileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, klass.getName(), Kind.SOURCE));
        }
        CompilationTask task = compiler.getTask(null, fileManager, diagnosticListener,
                Arrays.asList("-sourcepath", "src/test/java", "-d", "target/test-classes"),
                null, files);
        task.setProcessors(Arrays.asList(new SaferAnnotationProcessor()));
        if (errors.isEmpty()) {
            Assertions.assertTrue(task.call());
        } else {
            Assertions.assertFalse(task.call());
            Assertions.assertTrue(errors.equals(receivedErrors));
            Assertions.assertEquals(errors, receivedErrors);
        }
    }

    @Test
    public void testInvalid() throws IOException {
        compile(new HashSet<>(Arrays.asList(
                new ExpectedError(34, 16,
                        "Invalid return type: must be one of: [void, java.lang.String, java.util.List<java.lang.Integer>]"),
                new ExpectedError(39, 38,
                        "Invalid parameter type: must be one of: [java.lang.Integer, java.util.List<java.lang.Integer>]"),
                new ExpectedError(43, 50,
                        "Invalid parameter type: must be one of: [java.lang.Integer, java.util.List<java.lang.Integer>]"),
                new ExpectedError(7, 20, "Invalid accessor name: notGetter must start with 'get', 'is' or 'set'"),
                new ExpectedError(12, 17, "Invalid getter return type: cannot be 'void'"),
                new ExpectedError(16, 16, "Getter cannot have parameters"),
                new ExpectedError(21, 17, "Invalid accessor name: notSetter must start with 'get', 'is' or 'set'"),
                new ExpectedError(25, 16, "Invalid setter return type: must be 'void'"),
                new ExpectedError(30, 17, "Setter must have a single parameter"))),
                Invalid.class);
    }
}
