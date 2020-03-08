package it.unimol.acryl.analysis;

import com.ibm.wala.shrikeBT.IUnaryOpInstruction;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import it.unimol.acryl.static_analysis.contexts.MethodContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Simone Scalabrino.
 */
public class AugmentedSymbolTable {
    private final MethodContext methodContext;
    private Map<Integer, Object> variableTable;
    private Map<Integer, SDKInfo> checkingInstructionsTable;

    public AugmentedSymbolTable(MethodContext methodContext) {
        this.methodContext = methodContext;
    }

    public Object get(int variable) {
        return variableTable.getOrDefault(variable, null);
    }

    public SDKInfo getSDKInfo(int variable) {
        Object versionChecker = variableTable.getOrDefault(variable, null);

        if (versionChecker instanceof SDKInfo) {
            return ((SDKInfo) versionChecker);
        }

        return null;
    }

    public Map<Integer, SDKInfo> getCheckingInstructionsTable() {
        return checkingInstructionsTable;
    }

    public void update() {
        this.update(new MockVersionMethodCache());
    }

    public void update(IVersionMethodCache methodLabels) {
        variableTable               = new HashMap<>();
        checkingInstructionsTable   = new HashMap<>();

        // Imports the static symbol table
        IR ir = methodContext.getIntermediateRepresentation();

        if (ir == null)
            return;

        SymbolTable symbolTable = ir.getSymbolTable();

        int max = symbolTable.getMaxValueNumber();
        for (int i = 1; i <= max; i++) {
            if (symbolTable.getValue(i) instanceof ConstantValue) {
                variableTable.put(i, ((ConstantValue) symbolTable.getValue(i)).getValue());
            } else if (symbolTable.getValue(i) instanceof PhiValue) {
                variableTable.put(i, symbolTable.getValue(i));
            }
        }

        for (SSAInstruction ssaInstruction : ir.getInstructions()) {
            if (ssaInstruction == null)
                continue;

            if (ssaInstruction instanceof SSAFieldAccessInstruction) {
                updateForFieldAccess((SSAFieldAccessInstruction) ssaInstruction);
            } else if (ssaInstruction instanceof SSAComparisonInstruction) {
                updateForComparison((SSAComparisonInstruction) ssaInstruction);
            } else if (ssaInstruction instanceof SSAConditionalBranchInstruction) {
                updateForConditionalBranch((SSAConditionalBranchInstruction) ssaInstruction);
            } else if (ssaInstruction instanceof SSAPhiInstruction) {
                updateForPhi((SSAPhiInstruction) ssaInstruction);
            } else if (ssaInstruction instanceof SSABinaryOpInstruction) {
                updateForBinary((SSABinaryOpInstruction) ssaInstruction);
            } else if (ssaInstruction instanceof SSAUnaryOpInstruction) {
                updateForUnary((SSAUnaryOpInstruction) ssaInstruction);
            } else if (ssaInstruction instanceof SSAAbstractInvokeInstruction) {
                updateForInvoke((SSAAbstractInvokeInstruction) ssaInstruction, methodLabels);
            } else {
                updateForGenericInstruction(ssaInstruction);
            }
        }
    }

    private void updateForInvoke(SSAAbstractInvokeInstruction invokeInstruction, IVersionMethodCache methodLabels) {
        if (invokeInstruction.getNumberOfDefs() == 0)
            return;

        MethodReference calledMethod = invokeInstruction.getDeclaredTarget();

        String signature = calledMethod.getSignature();
        SDKInfo sdkInfo = methodLabels.getVersionNumbers(signature);

        if (sdkInfo != null) {
            if (sdkInfo.isDirect()) {
                SDKInfo info = new SDKInfo();
                info.setDirect(true);
                variableTable.put(invokeInstruction.getDef(), info);
            } else {
                variableTable.put(invokeInstruction.getDef(), sdkInfo);
            }
        }
    }

    private void updateForUnary(SSAUnaryOpInstruction unaryOpInstruction) {
        Object o1 = variableTable.getOrDefault(unaryOpInstruction.getUse(0), null);

        if (o1 instanceof SDKInfo) {
            SDKInfo info = ((SDKInfo) o1);

            assert !info.isDirect();

            SDKInfo newInfo = info.copy();

            for (VersionChecker dependentChecker : newInfo.getCheckerMap().values()) {
                if (unaryOpInstruction.getOpcode().equals(IUnaryOpInstruction.Operator.NEG)) {
                    dependentChecker.invertComparator(true);
                }
            }

            variableTable.put(unaryOpInstruction.getDef(), info);
        }
    }

    private void updateForBinary(SSABinaryOpInstruction binaryOpInstruction) {
        Object o1 = variableTable.getOrDefault(binaryOpInstruction.getUse(0), null);
        Object o2 = variableTable.getOrDefault(binaryOpInstruction.getUse(1), null);

        SDKInfo checker = null;
        if (o1 instanceof SDKInfo)
            checker = ((SDKInfo) o1);

        if (o2 instanceof SDKInfo) {
            if (checker == null)
                checker = ((SDKInfo) o2);
            else {
                SDKInfo checker2 = ((SDKInfo) o2);

                for (VersionChecker versionChecker : checker.getCheckerMap().values()) {
                    for (VersionChecker versionChecker2 : checker2.getCheckerMap().values()) {
                        versionChecker.compound(versionChecker2, binaryOpInstruction.getOperator());
                    }
                }
            }
        }

        if (checker != null)
            variableTable.put(binaryOpInstruction.getDef(), checker);
    }

    private void updateForPhi(SSAPhiInstruction phiInstruction) {
        // TODO implement properly
        this.variableTable.put(phiInstruction.getDef(), null);
    }

    @SuppressWarnings("SpellCheckingInspection")
    private void updateForFieldAccess(SSAFieldAccessInstruction fieldAccessInstruction) {
        if (fieldAccessInstruction.getDeclaredField().getName().toString().equals("SDK_INT") &&
                fieldAccessInstruction.getDeclaredField().getDeclaringClass().getName().toString().equals("Landroid/os/Build$VERSION")) {
            // Adds a reference #variable -> versionChecker
            SDKInfo info = new SDKInfo();
            info.setDirect(true);
            variableTable.put(fieldAccessInstruction.getDef(), info);
        }
    }

    private void updateForComparison(SSAComparisonInstruction comparisonInstruction) {
        int defined = comparisonInstruction.getDef();

        int use1 = comparisonInstruction.getUse(0);
        int use2 = comparisonInstruction.getUse(1);

        SDKInfo checker;
        boolean actualVersionInSecondVariable = false;

        checker = getSDKInfo(use1);
        if (checker == null) {
            actualVersionInSecondVariable = true;
            checker = getSDKInfo(use2);
        }

        if (checker != null) {
            checker = checker.copy();

            for (VersionChecker versionChecker : checker.getCheckerMap().values()) {
                switch (comparisonInstruction.getOperator()) {
                    case CMP:
                        versionChecker.setComparator(VersionChecker.Comparator.EQ);
                        break;

                    case CMPL:
                        versionChecker.setComparator(VersionChecker.Comparator.LT);
                        break;

                    case CMPG:
                        versionChecker.setComparator(VersionChecker.Comparator.GT);
                        break;
                }
                assignVersion(use1, use2, versionChecker, actualVersionInSecondVariable);
            }

            variableTable.put(defined, checker);
        }
    }

    private void updateForGenericInstruction(SSAInstruction ssaInstruction) {
        if (ssaInstruction.getNumberOfDefs() == 1) {
            for (int i = 0; i < ssaInstruction.getNumberOfUses(); i++) {
                int usedVariable = ssaInstruction.getUse(i);

                if (variableTable.containsKey(usedVariable)) {
                    Object use = variableTable.get(ssaInstruction.getUse(i));

                    if (use instanceof VersionChecker) {
                        variableTable.put(usedVariable, use);
                    }
                }
            }
        }
    }

    private void updateForConditionalBranch(SSAConditionalBranchInstruction conditionalBranchInstruction) {
        int use1 = conditionalBranchInstruction.getUse(0);
        int use2 = conditionalBranchInstruction.getUse(1);

        SDKInfo sdkInfo;
        boolean sdkVersionInSecondVariable = false;

        sdkInfo = getSDKInfo(use1);
        if (sdkInfo == null) {
            sdkVersionInSecondVariable = true;
            sdkInfo = getSDKInfo(use2);
        }

        if (sdkInfo != null) {
            SDKInfo instructionSdkInfo = new SDKInfo();
            instructionSdkInfo.setDirect(false);

            if (sdkInfo.isDirect()) {
                VersionChecker versionChecker = new VersionChecker();
                switch (conditionalBranchInstruction.getOperator().toString().toUpperCase()) {
                    case "EQ":
                        versionChecker.setComparator(VersionChecker.Comparator.EQ);
                        break;
                    case "NE":
                        versionChecker.setComparator(VersionChecker.Comparator.NE);
                        break;
                    case "LT":
                        versionChecker.setComparator(VersionChecker.Comparator.LT);
                        break;
                    case "GE":
                        versionChecker.setComparator(VersionChecker.Comparator.GE);
                        break;
                    case "GT":
                        versionChecker.setComparator(VersionChecker.Comparator.GT);
                        break;
                    case "LE":
                        versionChecker.setComparator(VersionChecker.Comparator.LE);
                        break;
                }
                assignVersion(use1, use2, versionChecker, sdkVersionInSecondVariable);

                VersionChecker invertedVersionChecker = versionChecker.copy();
                invertedVersionChecker.invertComparator(true);

                instructionSdkInfo.addChecker(true, versionChecker);
                instructionSdkInfo.addChecker(false, invertedVersionChecker);

            } else {
                int otherVariable = sdkVersionInSecondVariable ? use1 : use2;

                Object otherVariableValue = variableTable.get(otherVariable);

                assert (otherVariableValue instanceof Integer);
                assert (conditionalBranchInstruction.getOperator().toString().toUpperCase().equals("EQ") ||
                        conditionalBranchInstruction.getOperator().toString().toUpperCase().equals("NE"));

                boolean invert = !conditionalBranchInstruction.getOperator().toString().toUpperCase().equals("EQ");

                VersionChecker versionChecker = sdkInfo.getVersionFor(otherVariableValue);
                if (versionChecker == null)
                    return;

                versionChecker = versionChecker.copy();

                VersionChecker invertedVersionChecker = versionChecker.copy();
                invertedVersionChecker.invertComparator(true);

                if (!invert) {
                    instructionSdkInfo.addChecker(true, versionChecker);
                    instructionSdkInfo.addChecker(false, invertedVersionChecker);
                } else {
                    instructionSdkInfo.addChecker(true, invertedVersionChecker);
                    instructionSdkInfo.addChecker(false, versionChecker);
                }
            }

            checkingInstructionsTable.put(conditionalBranchInstruction.iindex, instructionSdkInfo);
        }
    }

    private void assignVersion(int use1, int use2, VersionChecker checker, boolean versionInSecondVariable) {
        Integer checkedVersion = null;

        int actualUse;
        if (versionInSecondVariable) {
            checker.invertComparator(false);

            actualUse = use1;
        } else {
            actualUse = use2;
        }

        Object data = variableTable.getOrDefault(actualUse, null);
        if (data instanceof Integer)
            checkedVersion = ((Integer) data);
        else if (data instanceof Float)
            checkedVersion = Math.round((Float) data);

        if (checkedVersion != null)
            checker.setCheckedVersion(checkedVersion);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("_________________________\n");

        builder.append("Variables:\n");
        for (Map.Entry<Integer, Object> entry : this.variableTable.entrySet()) {
            builder.append("\t").append(String.valueOf(entry.getKey())).append(" -> ").append(String.valueOf(entry.getValue())).append("\n");
        }

        builder.append("\n").append("Instructions:\n");
        for (Map.Entry<Integer, SDKInfo> entry : this.checkingInstructionsTable.entrySet()) {
            builder.append("\t").append(String.valueOf(entry.getKey())).append(" -> ").append(String.valueOf(entry.getValue())).append("\n");
        }

        builder.append("_________________________");

        return builder.toString();
    }
}
