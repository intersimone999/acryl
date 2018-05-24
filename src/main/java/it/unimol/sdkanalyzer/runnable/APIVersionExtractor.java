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
import java.util.Map;

/**
 * @author Simone Scalabrino.
 */
public class APIVersionExtractor extends CommonRunner {
    public void run(String[] args) throws Exception {
        checkAndInitialize(args);

        apkContext.setClassNotFoundHandler(className -> System.err.println("Class not found: " + className));

        System.out.println("Labeling methods...");
        VersionMethodCache cache = new VersionMethodCache(apkContext);
        cache.build();
        System.out.println("All method labeled!");

        String appName      = apk.getPackageName();
        String appVersion   = apk.getVersion();
        String appSdkMin    = String.valueOf(apk.getMinSDKVersion());
        String appSdkTrg    = String.valueOf(apk.getTargetSDKVersion());

        File graphDumpDirectory = new File("graph_dumps/" + appName);
        if (!graphDumpDirectory.exists())
            graphDumpDirectory.mkdirs();

        System.out.println("Starting analysis...");
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

                        String calledApis = StringUtils.join(ipcfg.getCalledAPIs("android"), "&");
                        writer.print(calledApis);
                        writer.print("\n");

                        try {
                            File dotFile = new File(graphDumpDirectory, appVersion + "|" + id + ".dot");
//                            File pdfFile = new File(graphDumpDirectory, appVersion + "|" + id + ".pdf");

                            ipcfg.exportGraph(dotFile);
//                            GraphTools.getInstance().dot2pdf(dotFile, pdfFile);
                        } catch (IOException e) {
                            System.err.println("Error exporting graph " + id);
                        }
                    }
                }
            }
        }
        System.out.println("All done!");
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
