package it.unimol.acryl.static_analysis.contexts;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.shrikeCT.AnnotationsReader;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.annotations.Annotation;
import it.unimol.acryl.analysis.AugmentedSymbolTable;

import java.util.*;

/**
 * @author Simone Scalabrino.
 */
public class MethodContext {
    private final IMethod method;
    private final IR intermediateRepresentation;
    private AugmentedSymbolTable augmentedSymbolTable;

    private final ClassContext context;

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
            if (annotation.getType().getName().toString().equals("Ljava/lang/SuppressWarnings")) {
                AnnotationsReader.ElementValue value = annotation.getNamedArguments().get("value");
                if (value instanceof AnnotationsReader.ConstantElementValue)
                    return ((AnnotationsReader.ConstantElementValue) value).val.equals("APICompatibilityIssues");
            }
        }

        return false;
    }

    public int getTargetAndroidSDK() {
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.getType().getName().toString().equals("Landroid/annotation/TargetApi") ||
                    annotation.getType().getName().toString().equals("Landroid/support/annotation/RequiresApi")) {
                AnnotationsReader.ElementValue value = annotation.getNamedArguments().get("value");
                if (value instanceof AnnotationsReader.ConstantElementValue)
                    if (((AnnotationsReader.ConstantElementValue) value).val instanceof Integer)
                        return ((Integer) ((AnnotationsReader.ConstantElementValue) value).val);
            }
        }

        return -1;
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
}
