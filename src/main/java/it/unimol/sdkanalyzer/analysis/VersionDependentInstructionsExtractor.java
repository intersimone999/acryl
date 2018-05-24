package it.unimol.sdkanalyzer.analysis;

import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import it.unimol.sdkanalyzer.static_analysis.utils.CFGVisitor;
import it.unimol.sdkanalyzer.graphs.SubCFG;

import java.util.HashMap;
import java.util.Map;

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

    public Map<VersionChecker, SubCFG> extractAllSubCFGs(MethodContext methodContext) {
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
                try {
                    visitor.visitConditionalBranchingBlock(block,
                            trueSubCFG -> {
                                VersionChecker subChecker = checker != null ? checker.getVersionFor(true) : null;
                                result.put(subChecker, trueSubCFG);
                            },

                            falseSubCFG -> {
                                VersionChecker subChecker = checker != null ? checker.getVersionFor(false) : null;
                                result.put(subChecker, falseSubCFG);
                            }
                    );
                } catch (CFGVisitor.NoEndingBlockException e) {
                    System.err.println("\tCould not find ending block of " + block.toString());
                }
            }
        });

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
                        System.err.println("\tCould not find ending block of " + block.toString());
                    }
                }
            }
        });

        return result;
    }
}