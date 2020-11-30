package br.ufba.jnose.core.testsmelldetector.testsmell.smell;

import br.ufba.jnose.core.testsmelldetector.testsmell.MethodUsage;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import br.ufba.jnose.core.testsmelldetector.testsmell.AbstractSmell;
import br.ufba.jnose.core.testsmelldetector.testsmell.TestClass;
import br.ufba.jnose.core.testsmelldetector.testsmell.TestMethod;

import java.io.FileNotFoundException;
import java.util.ArrayList;

public class IgnoredTest extends AbstractSmell {

    private boolean flag = false;
    private ArrayList<MethodUsage> instanceIgnored;

    public IgnoredTest() {
        super("IgnoredTest");
        instanceIgnored = new ArrayList<>();
    }

    /**
     * Analyze the test file for test methods that contain Ignored test methods
     */
    @Override
    public void runAnalysis(CompilationUnit testFileCompilationUnit, CompilationUnit productionFileCompilationUnit, String testFileName, String productionFileName) throws FileNotFoundException {
        classVisitor = new IgnoredTest.ClassVisitor();
        classVisitor.visit(testFileCompilationUnit, null);

        for (MethodUsage method : instanceIgnored) {
            TestMethod testClass = new TestMethod(method.getTestMethodName());
            testClass.setRange(method.getBlock());
//            testClass.addDataItem("begin", method.getBlock());
//            testClass.addDataItem("end", method.getBlock()); // [Remover]
            testClass.setHasSmell(true);
            smellyElementList.add(testClass);
        }
    }

    /**
     * Visitor class
     */
    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        TestClass testClass;

        /**
         * This method will check if the class has the @Ignore annotation
         */
        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            if (n.getAnnotationByName("Ignore").isPresent()) {
                testClass = new TestClass(n.getNameAsString());
                flag = true;
            }
            super.visit(n, arg);
        }

        /**
         * The purpose of this method is to 'visit' all test methods in the test file.
         */
        @Override
        public void visit(MethodDeclaration n, Void arg) {

            //JUnit 4
            //check if test method has Ignore annotation
            if (n.getAnnotationByName("Test").isPresent()) {
                if (n.getAnnotationByName("Ignore").isPresent() || flag) {
                    instanceIgnored.add(new MethodUsage(n.getNameAsString(), "",n.getRange().get().begin.line + "-" + n.getRange().get().end.line));
                    return;
                }
            }

            //JUnit 3
            //check if test method is not public
            if (n.getNameAsString().toLowerCase().startsWith("test")) {
                if (!n.getModifiers().contains(Modifier.PUBLIC)) {
                    instanceIgnored.add(new MethodUsage(n.getNameAsString(), "",n.getRange().get().begin.line + "-" + n.getRange().get().end.line));
                    return;
                }
            }
        }
    }
}
