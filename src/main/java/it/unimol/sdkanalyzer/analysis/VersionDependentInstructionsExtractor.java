package it.unimol.sdkanalyzer.analysis;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import it.unimol.sdkanalyzer.static_analysis.utils.CFGVisitor;
import it.unimol.sdkanalyzer.graphs.SubCFG;

import java.util.*;
import java.util.logging.Logger;

/**
 * Labels methods that return the SDK version number with either:
 * - the specific version returned
 * - the version condition
 *
 * @author Simone Scalabrino.
 */
public class VersionDependentInstructionsExtractor {
    private final VersionMethodCache versionMethodCache;

    public VersionDependentInstructionsExtractor(VersionMethodCache versionMethodCache) {
        this.versionMethodCache = versionMethodCache;
    }

    public Set<SubCFG> extractVersionIndependentCFGs(MethodContext methodContext, Collection<SubCFG> checkedSubCFG) {
        if (methodContext.getIntermediateRepresentation() == null)
            return null;

        Set<SubCFG> result = new HashSet<>();
        Set<ISSABasicBlock> checkedBlocks = new HashSet<>();
        for (SubCFG subCFG : checkedSubCFG) {
            checkedBlocks.addAll(subCFG.vertexSet());
        }

        SSACFG cfg = methodContext.getIntermediateRepresentation().getControlFlowGraph();
        for (ISSABasicBlock basicBlock : cfg) {
            if (!checkedBlocks.contains(basicBlock)) {
                SubCFG subCFG = new SubCFG(cfg, Collections.singleton(basicBlock));
                result.add(subCFG);
            }
        }
        
        return result;
    }

    public Map<VersionChecker, SubCFG> extractVersionDependentCFG(MethodContext methodContext) {
        if (methodContext.getIntermediateRepresentation() == null)
            return null;

        CFGVisitor visitor = new CFGVisitor(methodContext);

        AugmentedSymbolTable symbolTable = methodContext.getAugmentedSymbolTable();
        symbolTable.update(this.versionMethodCache);

        Map<VersionChecker, SubCFG> result = new HashMap<>();

        visitor.visit(block -> {
            if (block.getLastInstruction() instanceof SSAConditionalBranchInstruction) {
                SSAConditionalBranchInstruction lastInstruction = (SSAConditionalBranchInstruction) block.getLastInstruction();

                SDKInfo checker = symbolTable.getCheckingInstructionsTable().get(lastInstruction.iindex);
                if (checker != null) {
                    try {
                        visitor.visitConditionalBranchingBlock(block,
                                trueSubCFG -> {
                                    VersionChecker subChecker = checker.getVersionFor(true);
                                    result.put(subChecker, trueSubCFG);
                                },

                                falseSubCFG -> {
                                    VersionChecker subChecker = checker.getVersionFor(false);
                                    result.put(subChecker, falseSubCFG);
                                }
                        );
                    } catch (CFGVisitor.NoEndingBlockException e) {
                        Logger.getAnonymousLogger().warning("\tCould not find ending block of " + block.toString());
                    }
                }
            }
        });

        return result;
    }
}