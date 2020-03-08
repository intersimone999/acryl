package it.unimol.sdkanalyzer.runnable;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.analysis.VersionDependentInstructionsExtractor;
import it.unimol.sdkanalyzer.analysis.VersionMethodCache;
import it.unimol.sdkanalyzer.static_analysis.contexts.ClassContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import it.unimol.sdkanalyzer.graphs.IPCFG;
import it.unimol.sdkanalyzer.graphs.SubCFG;
import it.unimol.sdkanalyzer.topic_analysis.MessageAssigner;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class APIVersionExtractor extends CommonRunner {
    public void run(String[] args) throws Exception {
        checkAndInitialize(args);

        boolean forceGraphExport = false;

        String repo = null;
        String commit = null;
        if (args.length >= 6) {
            for (int i = 5; i < args.length; i++) {
                if (args[i].equals("--graph-export"))
                    forceGraphExport = true;

                if (args[i].startsWith("--repo=")) {
                    repo = args[i].substring(args[i].indexOf('=') + 1);
                    Logger.getAnonymousLogger().info("Using source repo: " + repo);
                }

                if (args[i].startsWith("--commit=")) {
                    commit = args[i].substring(args[i].indexOf('=') + 1);
                    Logger.getAnonymousLogger().info("Forcing checkout of commit: " + commit);
                }
            }
        }

        apkContext.setClassNotFoundHandler(className -> Logger.getAnonymousLogger().warning("Class not found: " + className));

        Logger.getAnonymousLogger().info("Labeling methods...");
        VersionMethodCache cache = new VersionMethodCache(apkContext);
        cache.build();
        Logger.getAnonymousLogger().info("All method labeled!");

        String appName      = apk.getPackageName();
        String appVersion   = apk.getVersion();
        String appSdkMin    = String.valueOf(apk.getMinSDKVersion());
        String appSdkTrg    = String.valueOf(apk.getTargetSDKVersion());

        File graphDumpDirectory = new File("graph_dumps/" + appName);
        if (forceGraphExport && !graphDumpDirectory.exists()) {
            boolean mkdirsResult = graphDumpDirectory.mkdirs();
            //noinspection PointlessBooleanExpression
            assert mkdirsResult == true;
        }

        Logger.getAnonymousLogger().info("Starting analysis...");

        VersionDependentInstructionsExtractor extractor = new VersionDependentInstructionsExtractor(cache);

        IDProvider idProvider = new IDProvider();
        List<APIUsageReport> reports = new ArrayList<>();
        for (IClass iClass : apkContext.getClassesInJar(false)) {
            // Skip classes belonging to the packages under analysis
            boolean skipClass = false;
            String className = iClass.getName().toString();
            for (String packageUnderAnalysis : PACKAGE_UNDER_ANALYSIS) {
                if (className.startsWith("L" + packageUnderAnalysis.replace('.', '/'))) {
                    skipClass = true;
                    break;
                }
            }
            if (skipClass) {
                continue;
            }

            ClassContext classContext = apkContext.resolveClassContext(iClass);

            for (IMethod iMethod : classContext.getNonAbstractMethods()) {
                MethodContext methodContext = classContext.resolveMethodContext(iMethod);
                methodContext.getAugmentedSymbolTable().update(cache);

                Map<VersionChecker, SubCFG> versionDependentParts = extractor.extractVersionDependentCFG(methodContext);

                if (versionDependentParts == null)
                    continue;

                for (Map.Entry<VersionChecker, SubCFG> entry : versionDependentParts.entrySet()) {
                    IPCFG ipcfg = IPCFG.buildIPCFG(apkContext, entry.getValue());
                    String calledApis = StringUtils.join(ipcfg.getCalledAPIs(PACKAGE_UNDER_ANALYSIS), "&");

                    int id = idProvider.getAndIncrement();

                    APIUsageReport report = new APIUsageReport();
                    report.setId(idProvider.getAndIncrement());
                    report.setCheck(entry.getKey().toString());
                    report.setSignature(methodContext.getIMethod().getSignature());
                    report.setApis(calledApis);
                    report.setMethod(methodContext);

                    reports.add(report);
                    if (forceGraphExport) {
                        try {
                            File dotFile = new File(graphDumpDirectory, appVersion + "|" + id + ".dot");

                            ipcfg.exportGraph(dotFile);
                        } catch (IOException e) {
                            Logger.getAnonymousLogger().severe("Error exporting graph " + id);
                        }
                    }
                }
            }
        }

        try {
            if (repo != null) {
                File repoFile = new File(repo);
                if (repoFile.exists() && repoFile.isDirectory()) {
                    MessageAssigner assigner = new MessageAssigner(repoFile);

                    for (APIUsageReport report : reports) {
                        assigner.assign(report, commit);
                    }
                } else {
                    Logger.getAnonymousLogger().warning("The specified repository path is not an existing directory. It will be ignored.");
                }
            } else {
                Logger.getAnonymousLogger().info("You did not specify any repository. Assigning empty messages.");
            }
        } catch (Throwable e) {
            Logger.getAnonymousLogger().warning("The message assigner module failed: " + e.getMessage());
        }


        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println("id\tapp\tversion\tsdk_min\tsdk_trg\tcheck\tmethod\tapis\tmessage\tmodified_files");

            for (APIUsageReport report : reports) {
                writer.print(report.getId());
                writer.print("\t");

                writer.print(appName);
                writer.print("\t");

                writer.print(appVersion);
                writer.print("\t");

                writer.print(appSdkMin);
                writer.print("\t");

                writer.print(appSdkTrg);
                writer.print("\t");

                writer.print(report.getCheck());
                writer.print("\t");

                writer.print(report.getSignature());
                writer.print("\t");

                writer.print(report.getApis());
                writer.print("\t");

                writer.print(report.getMessage() != null ? report.getMessage().replaceAll("\\s+", " ") : "");
                writer.print("\t");

                writer.print(report.getNumberOfModifiedFiles());
                writer.print("\n");
            }
        }

        Logger.getAnonymousLogger().info("All done!");
    }

    public static void main(String[] args) throws Exception {
        new APIVersionExtractor().run(args);
    }

    private static class IDProvider {
        private int id;

        public void increment() {
            id++;
        }

        public int get() {
            return id;
        }

        public int getAndIncrement() {
            return id++;
        }
    }

    public static class APIUsageReport {
        private int id;
        private String check;
        private MethodContext method;
        private String signature;
        private String apis;
        private String message;
        private int numberOfModifiedFiles;

        public void setId(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public void setCheck(String check) {
            this.check = check;
        }

        public String getCheck() {
            return check;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public String getSignature() {
            return signature;
        }

        public void setApis(String apis) {
            this.apis = apis;
        }

        public String getApis() {
            return apis;
        }

        public void setMethod(MethodContext method) {
            this.method = method;
        }

        public MethodContext getMethod() {
            return method;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setNumberOfModifiedFiles(int numberOfModifiedFiles) {
            this.numberOfModifiedFiles = numberOfModifiedFiles;
        }

        public int getNumberOfModifiedFiles() {
            return numberOfModifiedFiles;
        }
    }
}
