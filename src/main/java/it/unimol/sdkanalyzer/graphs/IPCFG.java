package it.unimol.sdkanalyzer.graphs;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import it.unimol.sdkanalyzer.static_analysis.contexts.ClassContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.jgrapht.Graph;
import org.jgrapht.ext.StringComponentNameProvider;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Inter-procedural CFG
 * @author Simone Scalabrino.
 */
public class IPCFG extends DefaultDirectedGraph<InternalBlock, DefaultEdge> {
    private final JarContext context;
    private InternalBlock head;
    private InternalBlock tail;
    private final boolean interProcedural;

    private final Map<String, InternalBlock> includedMethods;

    public IPCFG(JarContext context, boolean interProcedural) {
        super(DefaultEdge.class);

        this.head = null;
        this.tail = null;

        this.interProcedural = interProcedural;

        this.context = context;

        this.includedMethods = new HashMap<>();
    }

    public boolean isInterProcedural() {
        return interProcedural;
    }

    protected void setHead(InternalBlock head) {
        this.head = head;
    }

    public InternalBlock getHead() {
        return head;
    }

    protected void setTail(InternalBlock tail) {
        this.tail = tail;
    }

    public InternalBlock getTail() {
        return tail;
    }

    public Collection<String> getCalledAPIs() {
        return getCalledAPIs(new ArrayList<>());
    }

    @Deprecated
    public Collection<String> getCalledAPIs(String filter) {
        return getCalledAPIs(Collections.singletonList(filter));
    }

    public Collection<String> getCalledAPIs(List<String> filters) {
        Set<String> apiCalls = new HashSet<>();

        for (InternalBlock internalBlock : this.vertexSet()) {
            for (SSAInstruction instruction : internalBlock.getInstructions()) {
                if (instruction instanceof SSAAbstractInvokeInstruction) {
                    SSAAbstractInvokeInstruction invokeInstruction = (SSAAbstractInvokeInstruction) instruction;

                    String className = invokeInstruction.getDeclaredTarget().getDeclaringClass().getName().toString();
                    ClassContext classContext = this.context.resolveClassContext(className);

                    String methodSelector = invokeInstruction.getDeclaredTarget().getSelector().toString();

                    String calledMethodSignature;
                    try {
                        MethodContext calledMethodContext = classContext.resolveMethodContext(methodSelector);
                        calledMethodSignature = calledMethodContext.getIMethod().getSignature();
                    } catch (RuntimeException e) {
                        calledMethodSignature = "<??? Not resolved ???>";
                    }

                    boolean isOk = false;
                    if (filters.size() == 0) {
                        isOk = true;
                    } else {
                        for (String filter : filters) {
                            if (calledMethodSignature.startsWith(filter))
                                isOk = true;
                        }
                    }

                    if (isOk)
                        apiCalls.add(calledMethodSignature);
                }
            }
        }

        return apiCalls;
    }

    @Override
    public boolean addVertex(InternalBlock internalBlock) {
        this.includedMethods.put(internalBlock.getIdentifier(), internalBlock);
        return super.addVertex(internalBlock);
    }

    public boolean includesMethod(String methodSignature){
        return this.includedMethods.containsKey(methodSignature);
    }

    public InternalBlock getMethodFromSignature(String methodSignature) {
        return this.includedMethods.get(methodSignature);
    }

    public static IPCFG buildIPCFG(JarContext jarContext, SubCFG subCFG) throws IOException {
        return buildIPCFG(jarContext, subCFG, true);
    }

    public static IPCFG buildIPCFG(JarContext jarContext, SubCFG subCFG, boolean interProcedural) throws IOException {
        IPCFG ipcfg = new IPCFG(jarContext, interProcedural);
        InternalBlock[] headAndTail = buildFromSubCFG(ipcfg, jarContext, subCFG);

        ipcfg.setHead(headAndTail[0]);
        ipcfg.setTail(headAndTail[1]);

        return ipcfg;
    }

    public static InternalBlock[] buildFromSubCFG(IPCFG ipcfg, JarContext jarContext, SubCFG cfg) throws IOException {
        return buildFromSubCFG(ipcfg, jarContext, cfg, null, null);
    }

    public static InternalBlock[] buildFromSubCFG(IPCFG ipcfg, JarContext jarContext, SubCFG cfg, String headID, String tailID) throws IOException {
        InternalBlock head = null;
        InternalBlock currentInternalBlock = null;

        Map<ISSABasicBlock, InternalBlock> internalBlocks = new HashMap<>();

        for (ISSABasicBlock basicBlock : cfg.vertexSet()) {
            if (basicBlock.isEntryBlock() || basicBlock.isExitBlock())
                continue;

            if (!internalBlocks.containsKey(basicBlock)) {
                internalBlocks.put(basicBlock, new InternalBlock());
                ipcfg.addVertex(internalBlocks.get(basicBlock));
            }
            currentInternalBlock = internalBlocks.get(basicBlock);

            if (head == null) {
                head = currentInternalBlock;
                if (headID != null)
                    head.setIdentifier(headID);

                ipcfg.includedMethods.put(headID, head);
            }

            for (SSAInstruction instruction : basicBlock) {
                if (instruction instanceof SSAAbstractInvokeInstruction) {
                    SSAAbstractInvokeInstruction invokeInstruction = (SSAAbstractInvokeInstruction) instruction;

                    String className = invokeInstruction.getDeclaredTarget().getDeclaringClass().getName().toString();
                    ClassContext classContext = jarContext.resolveClassContext(className);

                    String methodSelector = invokeInstruction.getDeclaredTarget().getSelector().toString();

                    MethodContext calledMethodContext = null;
                    String calledMethodSignature;
                    boolean isClassInJar = false;
                    try {
                        calledMethodContext = classContext.resolveMethodContext(methodSelector);
                        calledMethodSignature = calledMethodContext.getIMethod().getSignature();
                        isClassInJar = jarContext.isClassInJar(jarContext.resolveClassContext(calledMethodContext.getIMethod().getDeclaringClass()), false);
                    } catch (RuntimeException e) {
                        calledMethodSignature = "<??? Not resolved ???>";
                    }

                    if (ipcfg.isInterProcedural() && isClassInJar) {
                        InternalBlock otherHead;
                        InternalBlock otherTail;
                        if (!ipcfg.includesMethod(calledMethodSignature)) {
                            InternalBlock[] blocks = buildFromMethod(ipcfg, calledMethodContext);
                            otherHead = blocks[0];
                            otherTail = blocks[1];
                        } else {
                            otherHead = ipcfg.getMethodFromSignature(calledMethodSignature);
                            otherTail = ipcfg.getMethodFromSignature(calledMethodSignature + "$TAIL");
                        }

                        ipcfg.addEdge(currentInternalBlock, otherHead);
                        currentInternalBlock = new InternalBlock();
                        ipcfg.addVertex(currentInternalBlock);
                        if (otherTail != null)
                            ipcfg.addEdge(otherTail, currentInternalBlock);
                    } else {
                        currentInternalBlock.addInstruction(instruction);
                    }
                } else {
                    currentInternalBlock.addInstruction(instruction);
                }
            }

            for (DefaultEdge edge : cfg.outgoingEdgesOf(basicBlock)) {
                ISSABasicBlock successor = cfg.getEdgeTarget(edge);

                if (!internalBlocks.containsKey(successor)) {
                    internalBlocks.put(successor, new InternalBlock());
                    ipcfg.addVertex(internalBlocks.get(successor));
                }

                ipcfg.addEdge(currentInternalBlock, internalBlocks.get(successor));
            }
        }

        if (tailID != null) {
            assert currentInternalBlock != null;

            currentInternalBlock.setIdentifier(tailID);
            ipcfg.includedMethods.put(tailID, currentInternalBlock);
        }

        return new InternalBlock[] {head, currentInternalBlock};
    }

    public static InternalBlock[] buildFromMethod(IPCFG ipcfg, MethodContext methodContext) throws IOException  {
        JarContext jarContext = methodContext.getJarContext();

        if (methodContext.getIntermediateRepresentation() == null) {
            InternalBlock[] result = new InternalBlock[2];
            result[0] = new InternalBlock();
            result[1] = result[0];

            result[0].addInstruction("NOT RESOLVED");

            ipcfg.addVertex(result[0]);
            return result;
        }


        SSACFG cfg = methodContext.getIntermediateRepresentation().getControlFlowGraph();
        SubCFG subCFG = new SubCFG(cfg);

        return buildFromSubCFG(
                ipcfg,
                jarContext,
                subCFG,
                methodContext.getIMethod().getSignature(),
                methodContext.getIMethod().getSignature() + "$TAIL"
        );
    }

    public synchronized void exportGraph(File destination) throws IOException {
        StringComponentNameProvider<InternalBlock> idProvider = new StringComponentNameProvider<InternalBlock>() {
            @Override
            public String getName(InternalBlock component) {
                String id = component.getIdentifier().replaceAll("[^A-Za-z0-9]", "_");
                id = "x" + id;
                return String.valueOf(id);
            }
        };

        StringComponentNameProvider<InternalBlock> labelProvider = new StringComponentNameProvider<InternalBlock>() {
            @Override
            public String getName(InternalBlock component) {
                return component.toString().replace("\n", "\\n");
            }
        };

        StringComponentNameProvider<DefaultEdge> edgeLabelProvider = new StringComponentNameProvider<DefaultEdge>() {
            @Override
            public String getName(DefaultEdge component) {
                return "";
            }
        };

        StringComponentNameProvider<Graph<InternalBlock, DefaultEdge>> graphIdProvider = new StringComponentNameProvider<Graph<InternalBlock, DefaultEdge>>() {
            @Override
            public String getName(Graph component) {
                return "G";
            }
        };

        GraphTools.getInstance().graph2dot(this, destination, idProvider, labelProvider, edgeLabelProvider, null, null, graphIdProvider);
    }
}
