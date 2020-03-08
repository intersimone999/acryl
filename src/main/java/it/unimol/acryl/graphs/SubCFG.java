package it.unimol.acryl.graphs;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import it.unimol.acryl.static_analysis.contexts.MethodContext;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class SubCFG extends DefaultDirectedGraph<ISSABasicBlock, DefaultEdge> implements Cloneable {
    private MethodContext methodContext;
    private List<Integer> involvedJavaLines;

    public SubCFG(SSACFG cfg, boolean addExceptional, Collection<ISSABasicBlock> blocks) {
        super(DefaultEdge.class);

        for (ISSABasicBlock block : blocks) {
            this.addVertex(block);
        }

        for (ISSABasicBlock block : blocks) {
            for (ISSABasicBlock successor : cfg.getNormalSuccessors(block)) {
                if (blocks.contains(successor)) {
                    this.addEdge(block, successor);
                }
            }

            if (addExceptional) {
                for (ISSABasicBlock successor : cfg.getExceptionalSuccessors(block)) {
                    if (blocks.contains(successor)) {
                        this.addEdge(block, successor);
                    }
                }
            }
        }
    }

    public SubCFG(SSACFG cfg, Collection<ISSABasicBlock> blocks) {
        this(cfg, true, blocks);
    }

    public SubCFG(SSACFG cfg, boolean addExceptional) {
        super(DefaultEdge.class);

        for (ISSABasicBlock block : cfg) {
            this.addVertex(block);
        }

        for (ISSABasicBlock block : cfg) {
            for (ISSABasicBlock successor : cfg.getNormalSuccessors(block)) {
                this.addEdge(block, successor);
            }

            if (addExceptional) {
                for (ISSABasicBlock successor : cfg.getExceptionalSuccessors(block)) {
                    this.addEdge(block, successor);
                }
            }
        }
    }

    public SubCFG(SSACFG cfg) {
        this(cfg, true);
    }

    public void setMethodContext(MethodContext methodContext) {
        this.methodContext = methodContext;
    }

    public int getMinLine() {
        updateInvolvedLines();

        return this.involvedJavaLines.stream().min(Comparator.naturalOrder()).orElse(-1);
    }

    public int getMaxLine() {
        updateInvolvedLines();

        return this.involvedJavaLines.stream().max(Comparator.naturalOrder()).orElse(-1);
    }

    private void updateInvolvedLines() {
        if (this.involvedJavaLines != null)
            return;

        this.involvedJavaLines = new ArrayList<>();
        try {
            for (ISSABasicBlock basicBlock : this.vertexSet()) {
                IBytecodeMethod method = (IBytecodeMethod) this.methodContext.getIntermediateRepresentation().getMethod();
                for (SSAInstruction instruction : basicBlock) {
                    int bytecodeIndex = method.getBytecodeIndex(instruction.iindex);
                    int sourceLine = method.getLineNumber(bytecodeIndex);

                    this.involvedJavaLines.add(sourceLine);
                }
            }
        } catch (Exception e) {
            Logger.getAnonymousLogger().warning("Unable to retrieve source code line information.");
        }
    }

    public SubCFG copy() {
        return ((SubCFG) this.clone());
    }
}
