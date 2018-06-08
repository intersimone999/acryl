package it.unimol.sdkanalyzer.static_analysis.contexts;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.shrikeCT.AnnotationsReader;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.annotations.Annotation;
import it.unimol.sdkanalyzer.analysis.AugmentedSymbolTable;
import it.unimol.sdkanalyzer.static_analysis.sequence.MethodSequence;

import java.util.*;

/**
 * @author Simone Scalabrino.
 */
public class MethodContext {
    private IMethod method;
    private IR intermediateRepresentation;
    private AugmentedSymbolTable augmentedSymbolTable;

    private ClassContext context;

    public MethodContext(IMethod method, ClassContext context) {
        this.method = method;
        this.context = context;


        this.intermediateRepresentation = this.getJarContext().getAnalysisCache().getSSACache().findOrCreateIR(
                method,
                Everywhere.EVERYWHERE,
                this.getJarContext().getAnalysisOptions().getSSAOptions());

        AnalysisOptions options = new AnalysisOptions();
        IRFactory<IMethod> factory = new DefaultIRFactory();
    }

    public AugmentedSymbolTable getAugmentedSymbolTable() {
        if (this.augmentedSymbolTable == null)
            this.buildAugmentedSymbolTable();

        return augmentedSymbolTable;
    }

    public void buildAugmentedSymbolTable() {
        this.augmentedSymbolTable = new AugmentedSymbolTable(this);
        this.augmentedSymbolTable.update();
    }

    public ClassContext getClassContext() {
        return context;
    }

    public JarContext getJarContext() {
        return context.getJarContext();
    }

    public IR getIntermediateRepresentation() {
        return intermediateRepresentation;
    }

    public boolean isConcrete() {
        return this.intermediateRepresentation != null;
    }

    public CGNode getCGNode() {
        return this.getJarContext().getCallGraph().getNode(this.method, Everywhere.EVERYWHERE);
    }

    public boolean isDeprecated() {
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.getType().getName().toString().equals("Ljava/lang/Deprecated"))
                return true;
        }

        return false;
    }

    public boolean isForcingDetectionSkip() {
        // TODO define an annotation through which developers can force the tool to skip check on a method/class
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.getType().getName().toString().equals("Lit/unimol/sdkanalyzer/ForceSkip")) {
                return true;
            }
        }

        return false;
    }

    public int getTargetAndroidSDK() {
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.getType().getName().toString().equals("Landroid/annotation/TargetApi")) {
                AnnotationsReader.ElementValue value = annotation.getNamedArguments().get("value");
                if (value instanceof AnnotationsReader.ConstantElementValue)
                    if (((AnnotationsReader.ConstantElementValue) value).val instanceof Integer)
                        return ((Integer) ((AnnotationsReader.ConstantElementValue) value).val);
            }
        }

        return 0;
    }

    public MethodSequence extractMethodSequence(int instructionNumber) {
        return this.extractMethodSequence(instructionNumber, false);
    }

    public MethodSequence extractMethodSequence(int instructionNumber, boolean isJavaInstruction) {
        DefUse defUse = new DefUse(this.intermediateRepresentation);

        MethodSequence sequence = new MethodSequence(this.getClassContext().getIClass());

        Set<Integer> analyzedUses = new HashSet<>();
        Queue<Integer> useQueue = new LinkedList<>();
        List<SSAInstruction> slice = new ArrayList<>();

        if (isJavaInstruction) {
            try {
                List<SSAInstruction> initialInstructions = this.getInstructionsForJavaLine(instructionNumber);

                boolean ok = false;
                for (int i = initialInstructions.size() - 1; i >= 0; i--) {
                    //Starts to add instructions from the last conditional branch. E.g., in for statements, the typical "++" is ignored.
                    if (initialInstructions.get(i) instanceof SSAConditionalBranchInstruction || initialInstructions.get(i) instanceof SSASwitchInstruction)
                        ok = true;

                    if (ok)
                        slice.add(0, initialInstructions.get(i));
                }

                for (SSAInstruction initialInstruction : initialInstructions) {
                    for (int i = 0; i < initialInstruction.getNumberOfUses(); i++) {
                        useQueue.add(initialInstruction.getUse(i));
                    }
                }
            } catch (InvalidClassFileException e) {
                throw new RuntimeException(e);
            }
        } else {
            SSAInstruction initialInstruction = this.intermediateRepresentation.getInstructions()[instructionNumber];
            slice.add(initialInstruction);

            for (int i = 0; i < initialInstruction.getNumberOfUses(); i++) {
                useQueue.add(initialInstruction.getUse(i));
            }
        }

        while (!useQueue.isEmpty()) {
            Integer useToAnalyze = useQueue.poll();
            assert useToAnalyze != null;

            analyzedUses.add(useToAnalyze);

            //If it is used a parameter...
            if (useToAnalyze <= this.method.getNumberOfParameters() + 1) {
                if (useToAnalyze != 1)
                    sequence.setContainsParameters(true);
            } else {
                SSAInstruction defInstruction = defUse.getDef(useToAnalyze);

                if (defInstruction != null) {
                    if (defInstruction instanceof SSAAbstractInvokeInstruction) {
                        SSAAbstractInvokeInstruction invokeInstruction = ((SSAAbstractInvokeInstruction) defInstruction);

                        if (!slice.contains(invokeInstruction))
                            slice.add(invokeInstruction);
                    } else if (defInstruction instanceof SSABinaryOpInstruction) {
                        SSABinaryOpInstruction binaryInstruction = ((SSABinaryOpInstruction) defInstruction);

                        if (!slice.contains(binaryInstruction))
                            slice.add(binaryInstruction);
                    } else if (defInstruction instanceof SSAFieldAccessInstruction) {
                        sequence.setContainsInstanceVariables(true);
                    }

                    for (int i = 0; i < defInstruction.getNumberOfUses(); i++) {
                        int usedVar = defInstruction.getUse(i);

                        if (!analyzedUses.contains(usedVar))
                            useQueue.add(usedVar);
                    }
                }
            }
        }

        slice.sort(Comparator.comparingInt(i -> i.iindex));

        for (SSAInstruction instruction : slice) {
            sequence.addMethodCall(instruction);
        }

        return sequence;
    }

    public IMethod getIMethod() {
        return method;
    }

    public int getJavaLineForInstruction(SSAInstruction instruction) throws InvalidClassFileException {
        IBytecodeMethod bytecodeMethod = (IBytecodeMethod) this.intermediateRepresentation.getMethod();
        try {
            int bytecodeIndex = bytecodeMethod.getBytecodeIndex(instruction.iindex);
            return bytecodeMethod.getLineNumber(bytecodeIndex);
        } catch (ArrayIndexOutOfBoundsException e) {
            return -1;
        }
    }

    public List<SSAInstruction> getInstructionsForJavaLine(int targetSourceLine) throws InvalidClassFileException {
        Iterator<SSAInstruction> instructionIterator = this.intermediateRepresentation.iterateAllInstructions();

        List<SSAInstruction> result = new ArrayList<>();
        while (instructionIterator.hasNext()) {
            SSAInstruction instruction = instructionIterator.next();

            int sourceLine = getJavaLineForInstruction(instruction);

            if (sourceLine == targetSourceLine)
                result.add(instruction);
        }

        return result;
    }

    public SSAInstruction getInstructionForBytecodeLine(int targetBytecodeLine) throws InvalidClassFileException {
        IBytecodeMethod bytecodeMethod = (IBytecodeMethod) this.intermediateRepresentation.getMethod();
        Iterator<SSAInstruction> instructionIterator = this.intermediateRepresentation.iterateAllInstructions();

        while (instructionIterator.hasNext()) {
            SSAInstruction instruction = instructionIterator.next();

            try {
                if (bytecodeMethod.getBytecodeIndex(instruction.iindex) == targetBytecodeLine)
                    return instruction;
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        }

        return null;
    }

    //    @Deprecated
//    public MethodSequence extractMethodSequenceFromSlice(int instructionNumber, int variableIndex) throws CancelException {
//        SSAInstruction instruction = this.intermediateRepresentation.getInstructions()[instructionNumber];
//        assert instruction instanceof SSAConditionalBranchInstruction;
//        SSAConditionalBranchInstruction branchInstruction = ((SSAConditionalBranchInstruction) instruction);
//
////        String[] names = this.intermediateRepresentation.getLocalNames(instructionNumber, variableIndex);
//        NormalStatement normalStatement = new NormalStatement(this.getCGNode(), instructionNumber);
//
//        Collection<Statement> statements = Slicer.computeBackwardSlice(normalStatement, this.getJarContext().getCallGraph(), this.getJarContext().getPointerAnalysis());
//
//        MethodSequence sequence = new MethodSequence(this.getJarContext().getHierarchy(), this.getClassContext().getIClass());
//        for (Statement statement : statements) {
//            switch (statement.getKind()) {
//                case NORMAL:
//                    SSAInstruction ssaInstruction = ((NormalStatement) statement).getInstruction();
//
//                    if (ssaInstruction instanceof SSAAbstractInvokeInstruction) {
//                        SSAAbstractInvokeInstruction invokeInstruction = ((SSAAbstractInvokeInstruction) ssaInstruction);
//
//                        sequence.addMethodCall(invokeInstruction.getDeclaredTarget().getSignature());
//                    } else if (ssaInstruction instanceof SSABinaryOpInstruction) {
//                        SSABinaryOpInstruction binaryInstruction = ((SSABinaryOpInstruction) ssaInstruction);
//
//                        sequence.addMethodCall(binaryInstruction.getOperator().toString());
//                    } else if (ssaInstruction instanceof SSAFieldAccessInstruction) {
//                        sequence.setContainsInstanceVariables(true);
//                    }
//
////                    sequence.addMethodCall();
//                    break;
//                case PARAM_CALLER:
//                    sequence.setContainsParameters(true);
//                    break;
//            }
//        }
//
//        return sequence;
//    }
}
