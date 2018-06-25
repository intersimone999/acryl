package it.unimol.sdkanalyzer.topic_analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.Range;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.type.Type;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.io.FileUtil;
import com.sun.org.apache.xml.internal.security.utils.HelperNodeList;
import it.unimol.sdkanalyzer.runnable.APIVersionExtractor;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import sun.rmi.runtime.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Simone Scalabrino.
 */
public class TopicAssigner {
    private final Git git;

    public TopicAssigner(File repository) throws IOException, GitAPIException {
        this.git = Git.wrap(new FileRepository(repository));
    }

    public void assign(APIVersionExtractor.APIUsageReport report, String commit) throws GitAPIException, IOException {
        this.git.checkout().setForce(true).setName(commit).call();

        String classPath = report.getMethod().getClassContext().getIClass().getName().toString();
        classPath = classPath.replace(".", "/");
        if (classPath.contains("$")) {
            classPath = classPath.substring(0, classPath.indexOf("$"));
        }
        classPath += ".java";

        final String finalClassPath = classPath;

        Path filePath = Files.walk(Paths.get(this.git.getRepository().getDirectory().getPath()))
                .filter(path -> path.endsWith(finalClassPath))
                .findFirst()
                .orElse(null);

        if (filePath != null) {
            Path relativeFilePath = filePath.relativize(Paths.get(this.git.getRepository().getDirectory().getPath()));

            CompilationUnit compilationUnit = JavaParser.parse(filePath);

            IMethod method = report.getMethod().getIMethod();

            MethodDeclaration declaration = compilationUnit.findAll(MethodDeclaration.class).stream()
                    .filter(methodDeclaration -> {
                                if (!methodDeclaration.getName().getIdentifier().equals(method.getName().toString()))
                                    return false;

                                if (methodDeclaration.getParameters().size() != method.getNumberOfParameters())
                                    return false;

                                for (int i = 0; i < methodDeclaration.getParameters().size(); i++) {
                                    Type type = methodDeclaration.getParameter(i).getType();
                                    TypeReference apkParameterType = method.getParameterType(i);

                                    if (!type.asString().equals(apkParameterType.toString().substring(1).replace('/','.')))
                                        return false;
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
                Logger.getAnonymousLogger().warning("No SDK checks in the method. Skipping");
                return;
            }

            List<RevCommit> commits = new ArrayList<>();
            for (IfStmt ifStmt : sdkCheck) {
                TokenRange ifTokenRange = ifStmt.getTokenRange().orElse(null);

                if (ifTokenRange == null)
                    continue;

                Range lineRange = ifTokenRange.getBegin().findFirstToken().getRange().orElse(null);

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

            report.setMessage(commits.);
        }
    }
}
