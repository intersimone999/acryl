package it.unimol.sdkanalyzer.static_analysis.sequence;

import it.unimol.sdkanalyzer.static_analysis.contexts.ClassContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Simone Scalabrino.
 */
public class DatasetExtractor {
    private final GarbageCounter missingCounter;
    private JarContext[] contexts;
    private File csvFile;
    private Collection<ProbabilisticMethodSequence> sequences;

    private static final int MINIMUM_NUMBER_OF_OBSERVATIONS = 1;

    public DatasetExtractor(JarContext context, File csvFile) {
        this(new JarContext[] {context}, csvFile);
    }

    public DatasetExtractor(JarContext[] contexts, File csvFile) {
        this.contexts = contexts;
        this.csvFile = csvFile;
        this.missingCounter = new GarbageCounter();
    }

    public Collection<ProbabilisticMethodSequence> extractAllSequences(ExceptionHandler handler) throws IOException {
        if (sequences != null)
            return sequences;

        sequences = new ArrayList<>();

        try (FileReader fileReader = new FileReader(csvFile)) {
            BufferedReader reader = new BufferedReader(fileReader);

            reader.lines().forEach(line -> {
                try {
                    String[] parts = line.split("\t");

                    String className = parts[0];
                    String methodName = parts[1];
                    int javaLine = Integer.parseInt(parts[2]);
                    int bytecodeOffset = Integer.parseInt(parts[3]);
                    String type = parts[4];
                    int hitsTrue = Integer.parseInt(parts[5]);
                    int hitsFalse = Integer.parseInt(parts[6]);

                    if (hitsTrue + hitsFalse < MINIMUM_NUMBER_OF_OBSERVATIONS) {
                        missingCounter.missing++;
                        return;
                    }

                    ClassContext classContext = null;
                    for (JarContext context : this.contexts) {
                        try {
                            if (classContext == null)
                                classContext = context.resolveClassContext(className);
                        } catch (RuntimeException ignore) {
                        }
                    }
                    if (classContext == null)
                        throw new RuntimeException("Class " + className + " in no given context!");
                    MethodContext methodContext = classContext.resolveMethodContext(methodName);

                    ProbabilisticMethodSequence sequence = new ProbabilisticMethodSequence(methodContext, javaLine, bytecodeOffset);

                    if (type.startsWith("S") && !type.equals("Sdefault"))
                        sequence.getSequence().addMethodCall("eq");
                    sequence.setHitsTrue(hitsTrue);
                    sequence.setHitsFalse(hitsFalse);

                    sequences.add(sequence);
                } catch (Exception e) {
                    if (!handler.handleException(e)) {
                        throw new RuntimeException(e);
                    }
                }
            });

            reader.close();
        }

        return sequences;
    }

    public int getSkipped() {
        return missingCounter.missing;
    }

    public ProbabilisticSequenceDataset extractAndUseDataset() throws IOException {
        return extractAndUseDataset(false);
    }

    public ProbabilisticSequenceDataset extractAndUseDataset(boolean ignoreMissingClasses) throws IOException {
        Collection<ProbabilisticMethodSequence> sequences = this.extractAllSequences(e -> {
            if (e instanceof RuntimeException && e.getMessage().startsWith("No such a class")) {
                System.err.println(e.getMessage());
                return true;
            } else {
                return ignoreMissingClasses && e instanceof RuntimeException && e.getMessage().contains("in no given context");
            }
        });
        return ProbabilisticSequenceDataset.getFirstInstance(sequences);
    }

    public interface ExceptionHandler {
        boolean handleException(Exception e);
    }

    private class GarbageCounter {
        public int missing;
    }
}
