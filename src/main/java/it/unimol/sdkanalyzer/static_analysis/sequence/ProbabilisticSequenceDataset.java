package it.unimol.sdkanalyzer.static_analysis.sequence;

import org.apache.commons.lang3.SerializationUtils;

import java.io.*;
import java.util.*;

/**
 * @author Simone Scalabrino.
 */
public class ProbabilisticSequenceDataset implements Serializable {
    private static final long serialVersionUID = 2L;

    private static final int STD_EXPONENT = 5;
    private static final double STD_STRUCTURAL_WEIGHT = 0D;

    private static ProbabilisticSequenceDataset instance;
    private int exponent;
    private double structuralWeight;
    private Map<String, Integer> operationsDictionary;

    private static transient Map<MethodSequence, Double> cachedSumProbabilities;
    private static transient Map<MethodSequence, Double> cachedSumWeights;

    static {
        cachedSumProbabilities = new HashMap<>();
        cachedSumWeights = new HashMap<>();
    }

    public static void cleanCaches() {
        cachedSumProbabilities.clear();
        cachedSumWeights.clear();
    }

    public static ProbabilisticSequenceDataset getFirstInstance(Collection<ProbabilisticMethodSequence> sequences) {
        instance = new ProbabilisticSequenceDataset(sequences);
        return instance;
    }

    public static ProbabilisticSequenceDataset load(File file) throws IOException {
        return load(file, true);
    }

    public static ProbabilisticSequenceDataset load(File file, boolean compress) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            instance = SerializationUtils.deserialize(stream);
            if (compress)
                instance.compress();
            instance.build();

            return instance;
        }
    }

    public static ProbabilisticSequenceDataset getInstance() {
        return instance;
    }

    private Collection<ProbabilisticMethodSequence> sequences;

    public ProbabilisticSequenceDataset(Collection<ProbabilisticMethodSequence> sequences) {
        this.sequences = sequences;
        this.exponent = STD_EXPONENT;
        this.structuralWeight = STD_STRUCTURAL_WEIGHT;
    }

    public void build() {
        this.operationsDictionary = buildOperationsDictionary();
    }

    public Map<String, Integer> getOperationsDictionary() {
        return operationsDictionary;
    }

    public void setExponent(int exponent) {
        this.exponent = exponent;
    }

    public void setStructuralWeight(double structuralWeight) {
        this.structuralWeight = structuralWeight;
    }

    public double estimateProbability(MethodSequence sequence, double baseProbability) {
        return estimateProbability(sequence, baseProbability, true);
    }

    public double estimateProbability(MethodSequence sequence, double baseProbability, boolean type) {
        double sumProbability = 0;
        double totalWeight = 0;

        if (cachedSumProbabilities.containsKey(sequence)) {
            sumProbability += cachedSumProbabilities.get(sequence);
            totalWeight += cachedSumWeights.get(sequence);
        } else {
            for (ProbabilisticMethodSequence probabilisticMethodSequence : sequences) {
//            double similarity = probabilisticMethodSequence.getSequence().similarity(sequence);
                double similarity = sequence.similarity(probabilisticMethodSequence.getSequence(), this.exponent);
                double probability = probabilisticMethodSequence.getProbability(type);

                sumProbability += similarity * probability;
                totalWeight += similarity;
            }

            cachedSumProbabilities.put(sequence, sumProbability);
            cachedSumWeights.put(sequence, totalWeight);
        }

        sumProbability += baseProbability * structuralWeight;
        totalWeight += structuralWeight;

        if (totalWeight == 0) {
            return baseProbability;
        } else
            return sumProbability / totalWeight;
    }

    public void save(File file) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            SerializationUtils.serialize(this, stream);
        }
    }

    public Collection<ProbabilisticMethodSequence> getSequences() {
        return sequences;
    }

    public ProbabilisticSequenceDataset compress() {
        return compress(0);
    }

    public ProbabilisticSequenceDataset compress(int shitFilterThreshold) {
        Queue<ProbabilisticMethodSequence> sequenceCopy = new LinkedList<>(sequences);

        List<ProbabilisticMethodSequence> compressedSequence = new ArrayList<>();
        while (sequenceCopy.size() > 0) {
            ProbabilisticMethodSequence analyzingSequence = sequenceCopy.poll();

            for (ProbabilisticMethodSequence sequence : sequences) {
                if (sequence == analyzingSequence)
                    continue;

                if (sequence.getSequence().equals(analyzingSequence.getSequence())) {
                    analyzingSequence.merge(sequence);
                    sequenceCopy.remove(sequence);
                }
            }

            if (analyzingSequence.getHitsTrue() + analyzingSequence.getHitsFalse() >= shitFilterThreshold)
                compressedSequence.add(analyzingSequence);
        }

        this.sequences = compressedSequence;
        return this;
    }

    private Map<String, Integer> buildOperationsDictionary() {
        Map<String, Integer> result = new HashMap<>();

        int maxIndex = 0;
        for (ProbabilisticMethodSequence sequence : this.sequences) {
            for (String s : sequence.getSequence().getOperations()) {
                if (!result.containsKey(s))
                    result.put(s, maxIndex++);
            }
        }
        return result;
    }

    public List<Double> buildOperationsVector(ProbabilisticMethodSequence sequence) {
        List<Double> vector = new ArrayList<>();

        for (int i = 0; i < this.operationsDictionary.size(); i++) {
            vector.add(0.0);
        }

        for (String s : sequence.getSequence().getOperations()) {
            int index = this.operationsDictionary.get(s);

            vector.set(index, vector.get(index) + 1);
        }

        return vector;
    }
}
