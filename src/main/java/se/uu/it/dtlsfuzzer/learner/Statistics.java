package se.uu.it.dtlsfuzzer.learner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.learnlib.api.query.DefaultQuery;
import net.automatalib.words.Alphabet;
import se.uu.it.dtlsfuzzer.config.LearningConfig;
import se.uu.it.dtlsfuzzer.config.StateFuzzerConfig;
import se.uu.it.dtlsfuzzer.config.SulDelegate;

/**
 * Statistics collected over the learning process.
 */
public class Statistics {
	private String runDescription;
	private int alphabetSize;
	private int states;
	private long learnTests;
	private long learnInputs;
	private long allTests;
	private long allInputs;
	private List<DefaultQuery<?, ?>> counterexamples;
	private long duration;
	private long lastHypTests;
	private long lastHypInputs;
	private boolean finished;
	private List<HypothesisStatistics> hypStats;
	private String reason;

	protected Statistics() {
		runDescription = "";
	}

	@Override
	public String toString() {
		StringWriter sw = new StringWriter();
		export(sw);
		String statsString = sw.toString();
		return statsString;
	}

	public void export(Writer writer) {
		PrintWriter out = new PrintWriter(writer);
		out.println(runDescription);
		out.println("=== STATISTICS ===");
		out.println("Learning finished: " + finished);
		if (!finished) {
			out.println("Reason: " + reason);	
		}
		out.println("Size of the input alphabet: " + alphabetSize);
		out.println("Number of states: " + states);
		out.println("Number of hypotheses: " + hypStats.size());
		out.println("Number of inputs: " + allInputs);
		out.println("Number of tests: " + allTests);
		out.println("Number of learning inputs: " + learnInputs);
		out.println("Number of learning tests: " + learnTests);
		out.println("Number of inputs up to last hypothesis: " + lastHypInputs);
		out.println("Number of tests up to last hypothesis: " + lastHypTests);
		out.println("Time it took to learn model: " + duration);
		out.println("Counterexamples:");
		int ind = 1;
		for (Object ce : counterexamples) {
			out.println("CE " + (ind++) + ":" + ce);
		}
		if (!hypStats.isEmpty()) {
			out.println("Number of inputs when hypothesis was generated: " + hypStats.stream().map(s -> s.getSnapshot().getInputs()).collect(Collectors.toList()));
			out.println("Number of tests when hypothesis was generated: " + hypStats.stream().map(s -> s.getSnapshot().getTests()).collect(Collectors.toList()));
			out.println("Time when hypothesis was generated: " + hypStats.stream().map(s -> s.getSnapshot().getTime()).collect(Collectors.toList()));
			
			List<HypothesisStatistics> invalidatedHypStates = new ArrayList<>(hypStats);
			if (invalidatedHypStates.get(invalidatedHypStates.size()-1).getCounterexample() == null) {
				invalidatedHypStates.remove(invalidatedHypStates.size()-1);
			}
			out.println("Number of inputs when counterexample was found: " + invalidatedHypStates.stream().map(s -> s.getCounterexampleSnapshot().getInputs()).collect(Collectors.toList()));
			out.println("Number of tests when counterexample was found: " + invalidatedHypStates.stream().map(s -> s.getCounterexampleSnapshot().getTests()).collect(Collectors.toList()));
			out.println("Time when counterexample was found: " + invalidatedHypStates.stream().map(s -> s.getCounterexampleSnapshot().getTime()).collect(Collectors.toList()));
		}
		out.close();
	}

	protected void generateRunDescription(StateFuzzerConfig config,
			Alphabet<?> alphabet) {
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter(sw);
		out.println("=== RUN DESCRIPTION ===");
		out.println("Learning Parameters");
		out.println("Alphabet: " + alphabet);
		LearningConfig learningConfig = config.getLearningConfig();
		out.println("Learning Algorithm: "
				+ learningConfig.getLearningAlgorithm());
		out.println("Equivalence Algorithms: "
				+ learningConfig.getEquivalenceAlgorithms());
		out.println("Min Length: " + learningConfig.getMinLength());
		out.println("Max Length: " + learningConfig.getMaxLength());
		out.println("Random Length: " + learningConfig.getRandLength());
		out.println("Max Depth: " + learningConfig.getMaxDepth());
		out.println("Prob Reset: " + learningConfig.getProbReset());
		out.println("Max Queries: " + learningConfig.getNumberOfQueries());
		out.println("TLS SUL Parameters");
		SulDelegate sulDelegate = config.getSulDelegate();
		out.println("Protocol: " + sulDelegate.getProtocolVersion());
		out.println("Response Wait: " + sulDelegate.getResponseWait());
		if (sulDelegate.getCommand() != null) {
			out.println("Start Wait: " + sulDelegate.getStartWait());
			out.println("Command: " + sulDelegate.getCommand());
		}
		out.close();
		runDescription = sw.toString();
	}

	public String getRunDescription() {
		return runDescription;
	}
	
	public int getAlphabetSize() {
		return alphabetSize;
	}

	protected void setAlphabetSize(int alphabetSize) {
		this.alphabetSize = alphabetSize;
	}

	public int getStates() {
		return states;
	}

	protected void setStates(int states) {
		this.states = states;
	}

	public long getLearnTests() {
		return learnTests;
	}

	protected void setLearnTests(long learnTests) {
		this.learnTests = learnTests;
	}

	public long getLearnInputs() {
		return learnInputs;
	}

	protected void setLearnInputs(long learnInputs) {
		this.learnInputs = learnInputs;
	}

	public long getAllTests() {
		return allTests;
	}

	protected void setAllTests(long allTests) {
		this.allTests = allTests;
	}

	public long getAllInputs() {
		return allInputs;
	}

	public void setAllInputs(long allInputs) {
		this.allInputs = allInputs;
	}

	public long getDuration() {
		return duration;
	}

	public void setDuration(long duration) {
		this.duration = duration;
	}

	protected long getLastHypTests() {
		return lastHypTests;
	}

	protected void setLastHypTests(long lastHypTests) {
		this.lastHypTests = lastHypTests;
	}

	public long getLastHypInputs() {
		return lastHypInputs;
	}

	protected void setLastHypInputs(long lastHypInputs) {
		this.lastHypInputs = lastHypInputs;
	}

	protected void setCounterexamples(List<DefaultQuery<?, ?>> counterexamples) {
		this.counterexamples = counterexamples;
	}

	protected void setFinished(boolean finished, String reason) {
		this.finished = finished;
		this.reason = reason;
	}

	public void setHypStats(List<HypothesisStatistics> hypStats) {
		this.hypStats = hypStats;
	}
}
