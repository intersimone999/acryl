package it.unimol.sdkanalyzer.analysis;

import com.ibm.wala.ssa.*;
import it.unimol.sdkanalyzer.static_analysis.utils.CFGVisitor;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;

/**
 * Labels methods that return the SDK version number with either:
 * - the specific version returned
 * - the version condition
 *
 * @author Simone Scalabrino.
 */
public class VersionMethodLabeler {
    public SDKInfo labelMethod(MethodContext methodContext) {
        if (methodContext.getIntermediateRepresentation() == null)
            return null;

        CFGVisitor visitor = new CFGVisitor(methodContext);

        AugmentedSymbolTable symbolTable = methodContext.getAugmentedSymbolTable();

        SDKInfo result = new SDKInfo();

        visitor.visit(block -> {
            if (block.getLastInstruction() instanceof SSAConditionalBranchInstruction) {
                SSAConditionalBranchInstruction lastInstruction = (SSAConditionalBranchInstruction) block.getLastInstruction();

                SDKInfo checker = symbolTable.getCheckingInstructionsTable().get(lastInstruction.iindex);
                if (checker != null) {
                    try {
                        visitor.visitConditionalBranchingBlock(block,
                                trueSubCFG -> {
                                    for (ISSABasicBlock basicBlock : trueSubCFG.vertexSet()) {
                                        if (basicBlock.getLastInstruction() instanceof SSAReturnInstruction) {
                                            SSAReturnInstruction returnInstruction = (SSAReturnInstruction) basicBlock.getLastInstruction();

                                            result.setDirect(false);

                                            VersionChecker versionChecker = checker.getVersionFor(true);

                                            result.addChecker(symbolTable.get(returnInstruction.getUse(0)), versionChecker);
                                        }
                                    }
                                },

                                falseSubCFG -> {
                                    for (ISSABasicBlock basicBlock : falseSubCFG.vertexSet()) {
                                        if (basicBlock.getLastInstruction() instanceof SSAReturnInstruction) {
                                            SSAReturnInstruction returnInstruction = (SSAReturnInstruction) basicBlock.getLastInstruction();

                                            result.setDirect(false);
                                            VersionChecker falseChecker = checker.getVersionFor(false);
                                            result.addChecker(symbolTable.get(returnInstruction.getUse(0)), falseChecker);
                                        }
                                    }
                                }
                        );
                    } catch (CFGVisitor.NoEndingBlockException e) {
                        System.err.println("\tCould not find ending block for " + block.toString());
                    }
                }
            }

            if (block.getLastInstruction() instanceof SSAReturnInstruction) {
                SSAReturnInstruction returnInstruction = (SSAReturnInstruction) block.getLastInstruction();

                SDKInfo checker = symbolTable.getSDKInfo(returnInstruction.getUse(0));
                if (checker != null && checker.isDirect()) {
                    result.setDirect(true);
                }
            }
        });

        if (!result.isActuallyNull())
            return result;
        else
            return null;
    }

    public SDKInfo labelBasicBlock(ISSABasicBlock block) {
        // TODO implement
        throw new RuntimeException("Not implemented yet");
    }
}