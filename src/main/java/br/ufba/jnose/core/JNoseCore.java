package br.ufba.jnose.core;

import br.ufba.jnose.dto.*;
import br.ufba.jnose.core.testsmelldetector.testsmell.AbstractSmell;
import br.ufba.jnose.core.testsmelldetector.testsmell.SmellyElement;
import br.ufba.jnose.core.testsmelldetector.testsmell.TestFile;
import br.ufba.jnose.core.testsmelldetector.testsmell.TestSmellDetector;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JNoseCore {

    private final static Logger LOGGER = Logger.getLogger(JNoseCore.class.getName());

    public static void main(String[] args) throws IOException {
        String directoryPath = "/home/tassio/.jnose_projects/jnose-tests/";

        JNoseCore jNoseCore = new JNoseCore();

        List<TestClass> lista = jNoseCore.getFilesTest(directoryPath);

        for(TestClass testClass : lista){
            System.out.println(testClass.getPathFile() + " - " + testClass.getProductionFile() + " - " + testClass.getJunitVersion());

            for (TestSmell testSmell : testClass.getListTestSmell()){
                System.out.println(testSmell.getName() + " - " + testSmell.getMethod() + " - " + testSmell.getRange());
            }
        }

    }


    public List<TestClass> getFilesTest(String directoryPath) throws IOException {
        LOGGER.log(Level.INFO, "getFilesTest: start");

        String projectName = directoryPath.substring(directoryPath.lastIndexOf(File.separatorChar) + 1, directoryPath.length());

        List<TestClass> files = new ArrayList<>();

        Path startDir = Paths.get(directoryPath);

        Files.walk(startDir)
                .filter(Files::isRegularFile)
                .forEach(filePath -> {
                    if (filePath.getFileName().toString().lastIndexOf(".") != -1) {
                        String fileNameWithoutExtension = filePath.getFileName().toString().substring(0, filePath.getFileName().toString().lastIndexOf(".")).toLowerCase();

                        if (filePath.toString().toLowerCase().endsWith(".java") && fileNameWithoutExtension.matches("^.*test\\d*$")) {

                            TestClass testClass = new TestClass();
                            testClass.setProjectName(projectName);
                            testClass.setPathFile(filePath.toString());

                            if (isTestFile(testClass)) {

                                LOGGER.log(Level.INFO, "getFilesTest: " + testClass.getPathFile());

                                String productionFileName = "";
                                int index = testClass.getName().toLowerCase().lastIndexOf("test");
                                if (index > 0) {
                                    productionFileName = testClass.getName().substring(0, testClass.getName().toLowerCase().lastIndexOf("test")) + ".java";
                                }
                                testClass.setProductionFile(getFileProduction(startDir.toString(), productionFileName));

                                if (!testClass.getProductionFile().isEmpty()) {
                                    getTestSmells(testClass);
                                    files.add(testClass);
                                }
                            }
                        }
                    }
                });
        return files;
    }


    private Boolean isTestFile(TestClass testClass) {
        LOGGER.log(Level.INFO, "isTestFile: start");

        Boolean isTestFile = false;
        try {
            FileInputStream fileInputStream = null;
            fileInputStream = new FileInputStream(new File(testClass.getPathFile()));
            CompilationUnit compilationUnit = JavaParser.parse(fileInputStream);
            testClass.setNumberLine(compilationUnit.getRange().get().end.line);
            detectJUnitVersion(compilationUnit.getImports(), testClass);
            List<NodeList<?>> nodeList = compilationUnit.getNodeLists();
            for (NodeList node : nodeList) {
                isTestFile = flowClass(node, testClass);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isTestFile;
    }

    private void detectJUnitVersion(NodeList<ImportDeclaration> nodeList, TestClass testClass) {
        LOGGER.log(Level.INFO, "detectJUnitVersion: start");
        for (ImportDeclaration node : nodeList) {
            if (node.getNameAsString().contains("org.junit.jupiter")) {
                testClass.setJunitVersion(TestClass.JunitVersion.JUnit5);
            } else if (node.getNameAsString().contains("org.junit")) {
                testClass.setJunitVersion(TestClass.JunitVersion.JUnit4);
            } else if (node.getNameAsString().contains("junit.framework")) {
                testClass.setJunitVersion(TestClass.JunitVersion.JUnit3);
            }
        }
    }

    private Boolean flowClass(NodeList<?> nodeList, TestClass testClass) {
        LOGGER.log(Level.INFO, "flowClass: start -> " + nodeList.toString());
        boolean isTestClass = false;
        for (Object node : nodeList) {
            if (node instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classAtual = ((ClassOrInterfaceDeclaration) node);
                testClass.setName(classAtual.getNameAsString());
                NodeList<?> nodeList_members = classAtual.getMembers();
                testClass.setNumberMethods(classAtual.getMembers().size());
                isTestClass = flowClass(nodeList_members, testClass);
                if(isTestClass)return true;
            } else if (node instanceof MethodDeclaration) {
                isTestClass = flowClass(((MethodDeclaration) node).getAnnotations(), testClass);
                if(isTestClass)return true;
            } else if (node instanceof AnnotationExpr) {
                return ((AnnotationExpr) node).getNameAsString().toLowerCase().contains("test");
            }
        }
        return isTestClass;
    }

    public String getFileProduction(String directoryPath, String productionFileName) {
        LOGGER.log(Level.INFO, "getFileProduction: start");
        final String[] retorno = {""};
        try {
            Path startDir = Paths.get(directoryPath);
            Files.walk(startDir)
                    .filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        if (filePath.getFileName().toString().toLowerCase().equals(productionFileName.toLowerCase())) {
                            retorno[0] = filePath.toString();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return retorno[0];
    }

    public void getTestSmells(TestClass testClass) {
        LOGGER.log(Level.INFO, "getTestSmells: start");

        TestSmellDetector testSmellDetector = TestSmellDetector.createTestSmellDetector();
        TestFile testFile = new TestFile(testClass.getProjectName(), testClass.getPathFile().toString(), testClass.getProductionFile(), testClass.getNumberLine(), testClass.getNumberMethods());

        try {
            TestFile tempFile = testSmellDetector.detectSmells(testFile);
            for (AbstractSmell smell : tempFile.getTestSmells()) {
                smell.getSmellyElements();
                for (SmellyElement smellyElement : smell.getSmellyElements()) {
                    if (smellyElement.getHasSmell()) {
                        TestSmell testSmell = new TestSmell();
                        testSmell.setName(smell.getSmellName());
                        testSmell.setMethod(smellyElement.getElementName());
                        testSmell.setRange(smellyElement.getRange());
                        testClass.getListTestSmell().add(testSmell);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
