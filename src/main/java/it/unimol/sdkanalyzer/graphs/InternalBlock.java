package it.unimol.sdkanalyzer.graphs;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Simone Scalabrino.
 */
public class InternalBlock {
    private String identifier;
    private final List<SSAInstruction> instructions;
    private final List<String> stringInstructions;

    public InternalBlock() {
        this.identifier = UUID.randomUUID().toString();
        this.instructions = new ArrayList<>();
        this.stringInstructions = new ArrayList<>();
    }

    public InternalBlock(ISSABasicBlock block) {
        this();

        for (SSAInstruction instruction : block) {
            this.addInstruction(instruction);
        }
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public void addInstruction(String instruction){
        this.stringInstructions.add(instruction);
    }

    public void addInstruction(SSAInstruction instruction) {
        this.instructions.add(instruction);
    }

    public List<String> getStringInstructions() {
        return stringInstructions;
    }

    public List<SSAInstruction> getInstructions() {
        return instructions;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public String toString() {
        return StringUtils.join(instructions, "\n")+StringUtils.join(stringInstructions, "\n");
    }
}
