package it.unimol.acryl.static_analysis.utils;

import com.ibm.wala.ssa.*;
import it.unimol.acryl.static_analysis.contexts.MethodContext;
import it.unimol.acryl.graphs.SubCFG;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;

/**
 * @author Simone Scalabrino.
 */
public class CFGVisitor {
    private final SSACFG cfg;
    @SuppressWarnings("unused")
    private final MethodContext context;
    private GraphUtils.BackDominators<ISSABasicBlock> backDominators;

    public CFGVisitor(MethodContext context) {
        this.context = context;
        this.cfg = context.getIntermediateRepresentation().getControlFlowGraph();
    }

    public void visit(CFGVisitorAction action) {
        for (ISSABasicBlock basicBlock : cfg) {
            if (basicBlock.isEntryBlock() || basicBlock.isExitBlock())
                continue;

                action.visit(basicBlock);
        }
    }

    @SuppressWarnings("unused")
    public void visit(ISSABasicBlock from, CFGVisitorAction action) {
        SubCFG subCFG = new SubCFG(cfg);

        DijkstraShortestPath<ISSABasicBlock, DefaultEdge> pathGenerator = new DijkstraShortestPath<>(subCFG);

        for (ISSABasicBlock basicBlock : cfg) {
            if (basicBlock.isEntryBlock() || basicBlock.isExitBlock())
                continue;

            if (pathGenerator.getPath(from, basicBlock) != null) {
                action.visit(basicBlock);
            }
        }
    }

    public void visit(ISSABasicBlock from, ISSABasicBlock to, CFGVisitorAction action) {
        SubCFG subCFG = new SubCFG(cfg);

        DijkstraShortestPath<ISSABasicBlock, DefaultEdge> pathGenerator = new DijkstraShortestPath<>(subCFG);

        for (ISSABasicBlock basicBlock : cfg) {
            if (basicBlock.isEntryBlock() || basicBlock.isExitBlock())
                continue;

            if (basicBlock == to)
                continue;

            if (pathGenerator.getPath(from, basicBlock) != null &&
                    pathGenerator.getPath(basicBlock, to) != null) {
                action.visit(basicBlock);
            }
        }
    }

    public void visitConditionalBranchingBlock(ISSABasicBlock branchingBlock, CFGSubGraphVisitorAction actionOnTrue, CFGSubGraphVisitorAction actionOnFalse)
            throws NoEndingBlockException{
        assert this.cfg.getNormalSuccessors(branchingBlock).size() > 1;
        assert branchingBlock.getLastInstruction() instanceof SSAConditionalBranchInstruction;

        if (backDominators == null) {
            backDominators = GraphUtils.buildBackDominators(this.cfg);
        }

        SSAConditionalBranchInstruction branchInstruction = (SSAConditionalBranchInstruction) branchingBlock.getLastInstruction();

        ISSABasicBlock endingBlock  = GraphUtils.getBackDominator(this.backDominators, branchingBlock);

        if (endingBlock == null)
            throw new NoEndingBlockException();

        for (ISSABasicBlock normalSuccessor : this.cfg.getNormalSuccessors(branchingBlock)) {
            List<ISSABasicBlock> involvedBlocks = new ArrayList<>();
            this.visit(normalSuccessor, endingBlock, involvedBlocks::add);

            SubCFG subCFG = new SubCFG(this.cfg, involvedBlocks);

            int successorNumber = cfg.getNumber(normalSuccessor);
            SSACFG.BasicBlock targetBlock = cfg.getBlockForInstruction(branchInstruction.getTarget());
            if (cfg.getNumber(targetBlock) == successorNumber) {
                // True block
                actionOnTrue.visit(subCFG);
            } else {
                // False block
                actionOnFalse.visit(subCFG);
            }
        }
    }

    public interface CFGVisitorAction {
        void visit(ISSABasicBlock block);
    }

    public interface CFGSubGraphVisitorAction {
        void visit(SubCFG subCFG);
    }

    public class NoEndingBlockException extends Exception {
    }
}
