package se.uu.it.modeltester.test;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestingReport {
	private static final Logger LOG = LogManager.getLogger(TestingReport.class);
	
	private List<Bug> reportedBugs;
	
	public TestingReport() {
		reportedBugs = new LinkedList<>();
	}
	
	/**
	 * The important thing here the toString functionality which will be 
	 * printed at the end
	 */
	public void addBug(Bug bug) {
		LOG.fatal(bug.toString());
		reportedBugs.add(bug);
	}
	
	public <T extends Bug> List<T> getBugs(Class<T> bugType) {
		return reportedBugs.stream().filter( b -> bugType.isInstance(b)).map(b -> bugType.cast(b)).collect(Collectors.toList());
	}
 	
	
	public void printReport(PrintStream ps) {
//		PrintWriter pw = new PrintWriter(writer);
		ps.println("Testing Report");
		for (Object bug : reportedBugs) {
			ps.println(bug);
		}
	}
}
