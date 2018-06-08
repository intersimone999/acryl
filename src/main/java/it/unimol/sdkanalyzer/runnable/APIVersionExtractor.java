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
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class APIVersionExtractor extends CommonRunner {
    public void run(String[] args) throws Exception {
        checkAndInitialize(args);

        boolean forceGraphExport = false;
        if (args.length > 6) {
            for (int i = 5; i < args.length; i++) {
                if (args[i].equals("--graph-export"))
                    forceGraphExport = true;
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
        if (!graphDumpDirectory.exists()) {
            boolean mkdirsResult = graphDumpDirectory.mkdirs();
            //noinspection PointlessBooleanExpression
            assert mkdirsResult == true;
        }

        Logger.getAnonymousLogger().info("Starting analysis...");
        IDProvider idProvider = new IDProvider();
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println("id\tapp\tversion\tsdk_min\tsdk_trg\tcheck\tmethod\tapis");

            VersionDependentInstructionsExtractor extractor = new VersionDependentInstructionsExtractor(cache);
            for (IClass iClass : apkContext.getClassesInJar(false)) {
                ClassContext classContext = apkContext.resolveClassContext(iClass);

                for (IMethod iMethod : classContext.getNonAbstractMethods()) {
                    MethodContext methodContext = classContext.resolveMethodContext(iMethod);
                    methodContext.getAugmentedSymbolTable().update(cache);

                    Map<VersionChecker, SubCFG> versionDependentParts = extractor.extractVersionDependentCFG(methodContext);

                    if (versionDependentParts == null)
                        continue;

                    for (Map.Entry<VersionChecker, SubCFG> entry : versionDependentParts.entrySet()) {
                        IPCFG ipcfg = IPCFG.buildIPCFG(apkContext, entry.getValue());

                        int id = idProvider.getAndIncrement();
                        writer.print(id);
                        writer.print("\t");

                        writer.print(appName);
                        writer.print("\t");

                        writer.print(appVersion);
                        writer.print("\t");

                        writer.print(appSdkMin);
                        writer.print("\t");

                        writer.print(appSdkTrg);
                        writer.print("\t");

                        writer.print(entry.getKey().toString());
                        writer.print("\t");

                        writer.print(methodContext.getIMethod().getSignature());
                        writer.print("\t");

                        String calledApis = StringUtils.join(ipcfg.getCalledAPIs(Collections.singletonList(PACKAGE_UNDER_ANALYSIS)), "&");
                        writer.print(calledApis);
                        writer.print("\n");

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
}
