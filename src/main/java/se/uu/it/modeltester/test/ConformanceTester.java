package se.uu.it.modeltester.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.learnlib.oracles.CounterOracle;
import de.learnlib.oracles.CounterOracle.MealyCounterOracle;
import de.learnlib.oracles.SULOracle;
import net.automatalib.automata.transout.impl.FastMealy;
import net.automatalib.automata.transout.impl.FastMealyState;
import net.automatalib.util.automata.Automata;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;
import se.uu.it.modeltester.ConformanceTestingTask;
import se.uu.it.modeltester.config.ModelBasedTesterConfig;
import se.uu.it.modeltester.config.TestingConfig;
import se.uu.it.modeltester.mutate.FragmentationMutator;
import se.uu.it.modeltester.mutate.FragmentingTlsInput;
import se.uu.it.modeltester.mutate.fragment.FragmentationStrategy;
import se.uu.it.modeltester.mutate.fragment.RandomSwapMutator;
import se.uu.it.modeltester.mutate.fragment.SplittingMutator;
import se.uu.it.modeltester.sut.io.TlsInput;
import se.uu.it.modeltester.sut.io.TlsInputType;
import se.uu.it.modeltester.sut.io.TlsOutput;

public class ConformanceTester {
	private static final Logger LOGGER = LogManager.getLogger(ConformanceTester.class);
	public static final String TEST_REPORT_FILENAME = "testingReport.txt";
	public static final String TESTS_DIRNAME = "tests";
	
	private TestingConfig config;
	private String output;
	
	public ConformanceTester(ModelBasedTesterConfig config) {
		this.config = config.getTestingConfig();
		this.output = config.getOutput();
	}
	
	/**
	 * Tests conformance of the SUT to the specification. In doing so, it also also 
	 * checks that valid mutations of inputs (fragmentation) prompt the desired behavior
	 * on the SUT.  
	 * @throws IOException 
	 */
	public TestingReport testModel(SULOracle<TlsInput, TlsOutput> tlsOracle, ConformanceTestingTask task) throws IOException {
		LOGGER.info("Starting conformance testing");
		TestingReport report = doModelBasedTesting(tlsOracle, task);
		logResult(report);
		return report;
	}
	
	private TestingReport doModelBasedTesting(SULOracle<TlsInput, TlsOutput> tlsOracle, final ConformanceTestingTask task) {
		TestingReport report = new TestingReport();
		final MealyCounterOracle<TlsInput,TlsOutput>  testOracle = new CounterOracle.MealyCounterOracle<>(tlsOracle, "tests");
		FastMealy<TlsInput, TlsOutput> model = task.getSpecification();
		Alphabet<TlsInput> inputs = task.getAlphabet();
		Supplier<Boolean> shouldStop = () -> config.getBound() != null && config.getBound() < testOracle.getCount();

		List<Word<TlsInput>> stateCover = Automata.stateCover(model, inputs);

		int initNumFrag = config.getFromNumFrag();
		int maxNumFrags = config.getToNumFrag();
		
		for (Word<TlsInput> statePrefix : stateCover) {
			FastMealyState<TlsOutput> state = model.getState(statePrefix);
			for (TlsInput input : inputs) {
				FastMealyState<TlsOutput> nextState = model.getState(statePrefix.append(input));
				List<Word<TlsInput>> charSuffixes = Automata.stateCharacterizingSet(model, inputs, nextState);
				for (Word<TlsInput> suffix : charSuffixes) {
					if ( shouldStop.get() ) {
						return report;
					}
					
					Word<TlsInput> regularWord = new WordBuilder<TlsInput>()
							.append(statePrefix)
							.append(input)
							.append(suffix)
							.toWord();

					Word<TlsOutput> regularOutput = testOracle.answerQuery(regularWord);
					
					Word<TlsOutput> specificationOutput = model.computeOutput(regularWord);
					
					if (!specificationOutput.equals(regularOutput)) {
						SpecificationBug bug = new SpecificationBug(state, statePrefix, regularWord, specificationOutput, regularOutput);
						report.addBug(bug);
						if (!config.isExhaustive())
							break;
						continue;
					}
					
					boolean bugsFound = false;
					
					if (input.getInputType() == TlsInputType.HANDSHAKE) {
					for (boolean doShuffling : Arrays.asList(false, true)) { 
						for (FragmentationStrategy strategy : new FragmentationStrategy [] {FragmentationStrategy.EVEN, FragmentationStrategy.RANDOM}) {
							for (int numFrags=initNumFrag; numFrags<maxNumFrags; numFrags ++) {
								if ( shouldStop.get() ) {
									return report;
								}
								
								FragmentingTlsInput fragmentingInput = fragment(input, numFrags, strategy, doShuffling);
								Word<TlsInput> fuzzedWord = new WordBuilder<TlsInput>()
										.append(statePrefix)
										.append(fragmentingInput)
										.append(suffix)
										.toWord();
								Word<TlsOutput> fuzzedOutput = testOracle.answerQuery(fuzzedWord);
								
								if (!fuzzedOutput.equals(regularOutput)) {
									FragmentationBug bug = new FragmentationBug(state, statePrefix, fragmentingInput, suffix, regularOutput, fuzzedOutput);
									report.addBug(bug);
									bugsFound = true;
									break;
								}
								
							}
							if (bugsFound)
								break;
						}
						if (bugsFound)
							break;
					}
					
					if (bugsFound && !config.isExhaustive()) {
						break;
					}
				}
					}
			}
		}
		
		return report;
	}
	

	private static FragmentingTlsInput fragment(TlsInput input, int frags, FragmentationStrategy strategy, boolean doShuffling) {
		List<FragmentationMutator> mutators = new ArrayList<>();
		if (frags > 1) {
			SplittingMutator fragmentationMutator = new SplittingMutator(strategy, frags);
			mutators.add(fragmentationMutator);
		}
		if (doShuffling) {
			mutators.add(new RandomSwapMutator(0));
		}
		return new FragmentingTlsInput(input, mutators);
	}
	
	private void logResult(TestingReport report) throws IOException {
		if (output == null) {
			report.printReport(System.out);
		} else {
			File testReport = Paths.get(output, TEST_REPORT_FILENAME).toFile();
			testReport.createNewFile();
			PrintStream ps = new PrintStream(new FileOutputStream(testReport));
			report.printReport(ps);
			if (config.isTestGenerationEnabled()) {
				File testsDirectory = Paths.get(output, TESTS_DIRNAME).toFile();
				testsDirectory.mkdir();
				se.uu.it.modeltester.TestParser parser = new se.uu.it.modeltester.TestParser();
				for (Bug bug : report.getBugs()) {
					parser.writeTest(bug.getReproducingTest(), Paths.get(output, TESTS_DIRNAME, "Bug"+bug.getBugId()).toString());
				}
			}
		}
	}

}
