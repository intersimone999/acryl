package it.unimol.sdkanalyzer.topic_analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.Range;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.type.Type;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.TypeReference;
import it.unimol.sdkanalyzer.runnable.APIVersionExtractor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Simone Scalabrino.
 */
public class MessageAssigner {
    private final Git git;

    public MessageAssigner(File repository) throws IOException {
        this.git = Git.open(repository);
    }

    public void assign(APIVersionExtractor.APIUsageReport report, String checkoutCommit) throws GitAPIException, IOException {
        if (checkoutCommit != null)
            this.git.checkout().setForce(true).setName(checkoutCommit).call();

        String classPath = report.getMethod().getClassContext().getIClass().getName().toString();
        classPath = classPath.substring(1);
        if (classPath.contains("$")) {
            classPath = classPath.substring(0, classPath.indexOf("$"));
        }
        classPath += ".java";

        final String finalClassPath = classPath;

        Path filePath = Files.walk(Paths.get(this.git.getRepository().getDirectory().getParentFile().getPath()))
                .filter(path -> path.endsWith(finalClassPath))
                .findFirst()
                .orElse(null);

        if (filePath != null) {
            Path relativeFilePath = Paths.get(this.git.getRepository().getDirectory().getParentFile().getPath()).relativize(filePath);

            CompilationUnit compilationUnit = JavaParser.parse(filePath);

            IMethod method = report.getMethod().getIMethod();


            CallableDeclaration declaration = compilationUnit.findAll(CallableDeclaration.class).stream()
                    .filter(methodDeclaration -> {
                                if (method.isInit()) {
                                    if (!methodDeclaration.isConstructorDeclaration())
                                        return false;
                                } else {
                                    if (!methodDeclaration.getName().getIdentifier().equals(method.getName().toString()))
                                        return false;
                                }

                                if (methodDeclaration.getParameters().size() != method.getNumberOfParameters() - 1)
                                    return false;

                                for (int i = 0; i < methodDeclaration.getParameters().size(); i++) {
                                    Type type = methodDeclaration.getParameter(i).getType();
                                    TypeReference apkParameterType = method.getParameterType(i + 1);

                                    if (apkParameterType.isPrimitiveType()) {
                                        String translatedType = "?";
                                        switch (apkParameterType.getName().toString()) {
                                            case "I":
                                                translatedType = "int";
                                                break;
                                            case "J":
                                                translatedType = "long";
                                                break;
                                            case "Z":
                                                translatedType = "boolean";
                                                break;
                                            case "D":
                                                translatedType = "double";
                                                break;
                                            case "F":
                                                translatedType = "float";
                                                break;
                                            case "B":
                                                translatedType = "byte";
                                                break;
                                            case "C":
                                                translatedType = "char";
                                                break;
                                            case "S":
                                                translatedType = "short";
                                                break;
                                        }
                                        assert !translatedType.equals("?");

                                        if (!type.asString().equals(translatedType))
                                            return false;
                                    } else {
                                        String apkParameterTypeName = apkParameterType.getName().toString();
                                        apkParameterTypeName = apkParameterTypeName.substring(apkParameterTypeName.lastIndexOf("/") + 1);

                                        if (apkParameterTypeName.contains("$"))
                                            apkParameterTypeName = apkParameterTypeName.substring(apkParameterTypeName.lastIndexOf("$") + 1);

                                        if (!type.asString().equals(apkParameterTypeName))
                                            return false;
                                    }
                                }

                                return true;
                            }
                    ).findFirst().orElse(null);

            if (declaration == null) {
                Logger.getAnonymousLogger().warning("Unable to find the declaration from the source code");
                return;
            }

            List<IfStmt> sdkCheck = declaration.findAll(IfStmt.class).stream()
                    .filter(
                            ifStmt -> ifStmt.getCondition().toString().contains("SDK_INT")
                    ).collect(Collectors.toList());

            if (sdkCheck.size() == 0) {
                sdkCheck = declaration.findAll(IfStmt.class);

                //Last attempt: if there is only one if, use that one!
                if (sdkCheck.size() > 1) {
                    Logger.getAnonymousLogger().warning("No SDK checks in the method. Skipping");
                    return;
                }
            }

            List<RevCommit> commits = new ArrayList<>();
            for (IfStmt ifStmt : sdkCheck) {
                TokenRange ifTokenRange = ifStmt.getTokenRange().orElse(null);

                if (ifTokenRange == null)
                    continue;

                Range lineRange = ifTokenRange.getBegin().getRange().orElse(null);

                if (lineRange == null) {
                    Logger.getAnonymousLogger().warning("Unable to retrieve the range");
                    return;
                }


                RevCommit blamedCommit = this.git.blame()
                        .setFilePath(relativeFilePath.toString())
                        .setFollowFileRenames(true)
                        .call()
                        .getSourceCommit(lineRange.begin.line);

                if (commits.stream().noneMatch(alreadyBlamedCommit -> alreadyBlamedCommit.getId().equals(blamedCommit.getId())))
                    commits.add(blamedCommit);
            }

            if (commits.size() > 1) {
                Logger.getAnonymousLogger().warning("Too many commits blamed: " + commits.size());
                return;
            } else if (commits.size() == 0) {
                Logger.getAnonymousLogger().warning("No commit blamed");
                return;
            }

            report.setMessage(commits.get(0).getFullMessage());

            try (ObjectReader reader = git.getRepository().newObjectReader()) {
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                oldTreeIter.reset(reader, commits.get(0).getParent(0).getTree());

                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                newTreeIter.reset(reader, commits.get(0).getTree());

                // finally get the list of changed files
                List<DiffEntry> diffs = git.diff()
                        .setNewTree(newTreeIter)
                        .setOldTree(oldTreeIter)
                        .call();

                report.setNumberOfModifiedFiles(diffs.size());
            }
        }
    }
}
