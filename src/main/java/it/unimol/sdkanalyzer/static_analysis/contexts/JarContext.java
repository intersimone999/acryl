package it.unimol.sdkanalyzer.static_analysis.contexts;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.*;
import com.ibm.wala.util.MonitorUtil;
import it.unimol.sdkanalyzer.static_analysis.utils.WalaUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Simone Scalabrino.
 */
public class JarContext {
    private final AnalysisCache analysisCache;
    private final AnalysisOptions analysisOptions;
    private Map<Boolean, Collection<IClass>> classesInJar;
    protected String jarPath;
    private File[] dependencies;
    private ClassHierarchy fullHierarchy;
    private CallGraph callGraph;
    private AnalysisScope analysisScope;
    private PointerAnalysis pointerAnalysis;
    protected String classFilter;
    private ClassNotFoundHandler classNotFoundHandler;
    private boolean warmedUp;

    private Map<String, ClassContext> classMap;

    public JarContext(String jarPath) throws IOException, ClassHierarchyException {
        this(jarPath, false);
    }

    public JarContext(String jarPath, boolean withCallGraph) throws IOException, ClassHierarchyException {
        this(new File[] {}, jarPath, withCallGraph);
    }

    public JarContext(File[] classpath, String jarPath, boolean withCallGraph) throws IOException, ClassHierarchyException {
        File file = new File(jarPath);

        this.jarPath = jarPath;
        this.dependencies = classpath;

        this.classesInJar = new HashMap<>();

        this.classFilter = "";

        this.analysisScope = WalaUtils.getAnalysisScope(file, GlobalContext.getExclusionsFile(), classpath);
        this.fullHierarchy = ClassHierarchyFactory.make(analysisScope);

        Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(this.analysisScope, this.fullHierarchy);
        this.analysisOptions = new AnalysisOptions(this.analysisScope, entrypoints);
        this.analysisOptions.getSSAOptions().setPiNodePolicy(new AllIntegerDueToBranchePiPolicy());

        IRFactory<IMethod> factory = new DefaultIRFactory();
        this.analysisCache = new AnalysisCache(
                factory,
                analysisOptions.getSSAOptions(),
                new SSACache(factory, new AuxiliaryCache(), new AuxiliaryCache())
        );

        if (withCallGraph) {
            CallGraphBuilder cgb = Util.makeZeroCFABuilder(
                    analysisOptions,
                    analysisCache,
                    this.fullHierarchy,
                    this.analysisScope,
                    null,
                    null);
            try {
                this.callGraph = cgb.makeCallGraph(analysisOptions, new MonitorUtil.IProgressMonitor() {
                    @Override
                    public void beginTask(String s, int i) {
                        System.out.println("Task: " + s + " " + i);
                    }

                    @Override
                    public void subTask(String s) {
                        System.out.println("Subtask: " + s);
                    }

                    @Override
                    public void cancel() {

                    }

                    @Override
                    public boolean isCanceled() {
                        return false;
                    }

                    @Override
                    public void done() {
                        System.out.println("Done!");
                    }

                    @Override
                    public void worked(int i) {
                        System.out.println("Worked " + i);
                    }

                    @Override
                    public String getCancelMessage() {
                        return null;
                    }
                });
                this.pointerAnalysis = cgb.getPointerAnalysis();
            } catch (CallGraphBuilderCancelException e) {
                throw new RuntimeException("Call graph canceled");
            }
        }
        this.classMap = new HashMap<>();
    }

    public AnalysisScope getAnalysisScope() {
        return analysisScope;
    }

    public AnalysisOptions getAnalysisOptions() {
        return analysisOptions;
    }

    public AnalysisCache getAnalysisCache() {
        return analysisCache;
    }

    public PointerAnalysis getPointerAnalysis() {
        return pointerAnalysis;
    }

    public ClassHierarchy getHierarchy() {
        return fullHierarchy;
    }

    public CallGraph getCallGraph() {
        return callGraph;
    }

    public ClassContext resolveClassContext(IClass iClass) {
        return this.resolveClassContext(iClass.getName().toString());
    }

    public void warmup() {
        if (warmedUp)
            return;

        for (IClass iClass : this.fullHierarchy) {
            ClassContext context = new ClassContext(iClass, this);
            this.classMap.put(iClass.getName().toString(), context);
        }

        this.warmedUp = true;
    }

    public ClassContext resolveClassContext(String signature) {
        if (!signature.startsWith("L") && !signature.startsWith("[")) {
            signature = "L"+signature;
            signature = signature.replace('.', '/');
        }
        if (warmedUp) {
            if (this.classMap.containsKey(signature)) {
                return this.classMap.get(signature);
            }
        } else {
            if (!this.classMap.containsKey(signature)) {
                for (IClass iClass : this.fullHierarchy) {
                    if (iClass.getName().toString().equals(signature)) {
                        ClassContext context = new ClassContext(iClass, this);
                        this.classMap.put(signature, context);
                        return context;
                    }
                }
            } else
                return this.classMap.get(signature);
        }

        //If arrays don't work, use Object
        if (signature.startsWith("[")) {
            return resolveClassContext("Ljava/lang/Object");
        }
        if (this.classNotFoundHandler == null) {
            throw new RuntimeException("No such a class: " + signature + " in jar " + this.jarPath);
        } else {
            this.classNotFoundHandler.notFound(signature);
            return null;
        }
    }

    public Collection<IClass> getClassesInContext(boolean keepIgnored) {
        Collection<IClass> result = new HashSet<>();
        for (IClass iClass : this.fullHierarchy) {
            if (iClass.getClassLoader().getName().toString().equals("Application"))
                if (!analysisScope.getExclusions().contains(iClass.getName().toString()) || !keepIgnored)
                    result.add(iClass);
        }
        return result;
    }

    public Collection<IClass> getClassesInJar(boolean useClassFilter) throws IOException {
        if (classesInJar.containsKey(useClassFilter))
            return classesInJar.get(useClassFilter);

        Collection<IClass> resultingClasses = internalGetClassesInJar(useClassFilter);

        this.classesInJar.put(useClassFilter, resultingClasses);

        return resultingClasses;
    }

    protected Collection<IClass> internalGetClassesInJar(boolean useClassFilter) throws IOException {
        List<IClass> resultingClasses = new ArrayList<>();
        ZipInputStream zip = new ZipInputStream(new FileInputStream(this.jarPath));
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                // This ZipEntry represents a class. Now, what class does it represent?
                String className = entry.getName().replace('/', '.'); // including ".class"
                className = className.substring(0, className.length() - ".class".length());

                if (!useClassFilter || className.startsWith(this.classFilter)) {
                    ClassContext classContext = this.resolveClassContext(className);
                    if (classContext != null)
                        resultingClasses.add(classContext.getIClass());
                }
            }
        }
        return resultingClasses;
    }

    public boolean isClassInJar(ClassContext context, boolean useClassFilter) throws IOException {
        Collection<IClass> classesInJar = this.getClassesInJar(useClassFilter);

        for (IClass iClass : classesInJar) {
            if (iClass.equals(context.getIClass()))
                return true;
        }

        return false;
    }

    public Collection<IClass> getClassesInContext() {
        return this.getClassesInContext(false);
    }

    public File[] getCompleteClasspath() {
        File[] completeClasspath = new File[this.dependencies.length+1];
        for (int i = 0; i < dependencies.length; i++) {
            completeClasspath[i] = dependencies[i];
        }
        completeClasspath[dependencies.length] = getJarPath();

        return completeClasspath;
    }

    public File getJarPath() {
        return new File(this.jarPath);
    }

    public File[] getDependencies() {
        return this.dependencies;
    }

    public String getClassFilter() {
        return classFilter;
    }

    public void setClassFilter(String classFilter) {
        this.classFilter = classFilter;
    }

    public ClassNotFoundHandler getClassNotFoundHandler() {
        return classNotFoundHandler;
    }

    public void setClassNotFoundHandler(ClassNotFoundHandler classNotFoundHandler) {
        this.classNotFoundHandler = classNotFoundHandler;
    }

    public interface ClassNotFoundHandler {
        void notFound(String className);
    }
}
