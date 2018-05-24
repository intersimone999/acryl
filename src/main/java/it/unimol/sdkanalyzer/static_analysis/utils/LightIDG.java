package it.unimol.sdkanalyzer.static_analysis.utils;

import com.ibm.wala.ssa.*;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;

import java.util.*;

/**
 * A lightweight Instruction Dependency Graph
 * @author Simone Scalabrino.
 */
public class LightIDG {
    private final MethodContext methodContext;
    private final SSACFG cfg;
    private Map<Integer, Set<Integer>> predecessors;

    public LightIDG(MethodContext methodContext) {
        this.methodContext = methodContext;
        this.predecessors = new HashMap<>();
        this.cfg = methodContext.getIntermediateRepresentation().getControlFlowGraph();
    }

    public void build() {
        Map<Integer, Set<Integer>> bbPredecessors = new HashMap<>();
        for (ISSABasicBlock basicBlock : cfg) {
            computePredecessors(basicBlock.getNumber(), bbPredecessors);
        }

        this.predecessors = bbPredecessors;

//        for (Map.Entry<Integer, Set<Integer>> predecessorEntry : bbPredecessors.entrySet()) {
//            ISSABasicBlock block = cfg.getBasicBlock(predecessorEntry.getKey());
//            for (SSAInstruction instruction : block) {
//                Set<Integer> instructionPredecessors = new HashSet<>();
//
//                for (Integer predecessorBlock : predecessorEntry.getValue()) {
//                    for (SSAInstruction predecessorInstruction : cfg.getBasicBlock(predecessorBlock)) {
//                        instructionPredecessors.add(predecessorInstruction.iindex);
//                    }
//                }
//
//                predecessors.put(instruction.iindex, instructionPredecessors);
//            }
//        }
    }

    private Set<Integer> computePredecessors(int bindex, Map<Integer, Set<Integer>> cache) {
        if (!cache.containsKey(bindex))
            cache.put(bindex, new HashSet<>());
        else
            return cache.get(bindex);

        for (ISSABasicBlock basicBlock : cfg.getNormalPredecessors(cfg.getBasicBlock(bindex))) {
            cache.get(bindex).add(basicBlock.getNumber());
            cache.get(bindex).addAll(computePredecessors(basicBlock.getNumber(), cache));
        }

        for (ISSABasicBlock basicBlock : cfg.getExceptionalPredecessors(cfg.getBasicBlock(bindex))) {
            cache.get(bindex).add(basicBlock.getNumber());
            cache.get(bindex).addAll(computePredecessors(basicBlock.getNumber(), cache));
        }

        return cache.get(bindex);
    }

    public Set<Integer> getPredecessors(int iindex) {
        return this.predecessors.get(iindex);
    }

    public int getMinimumBlockWithPredecessorsOfBlock(ISSABasicBlock basicBlock) {
        Set<Integer> toCheck = new HashSet<>();

        SSACFG cfg = this.methodContext.getIntermediateRepresentation().getControlFlowGraph();

        for (ISSABasicBlock block : cfg.getNormalSuccessors(basicBlock)) {
            toCheck.add(block.getNumber());
        }

        for (ISSABasicBlock block : cfg.getExceptionalSuccessors(basicBlock)) {
            toCheck.add(block.getNumber());
        }

        return this.getMinimumBlockWithPredecessors(toCheck);
    }

    /**add
     * Returns the index of the instruction with common dependency (used, for example, to detect the end of an if condition)
     * @param match set of integer to be matched (at least 2)
     * @return
     */
    public int getMinimumBlockWithPredecessors(Set<Integer> match) {
        assert match.size() >= 2;

        List<Integer> candidates = new ArrayList<>();

        for (ISSABasicBlock basicBlock : cfg) {
            int blockNumber = basicBlock.getNumber();

            Set<Integer> allPredecessors = new HashSet<>(this.getPredecessors(blockNumber));
            allPredecessors.add(blockNumber);

            if (allPredecessors.containsAll(match)) {
                candidates.add(blockNumber);
            }
        }

        return candidates.stream()
                .min(Comparator.naturalOrder())
                .orElse(-1);
    }
}
