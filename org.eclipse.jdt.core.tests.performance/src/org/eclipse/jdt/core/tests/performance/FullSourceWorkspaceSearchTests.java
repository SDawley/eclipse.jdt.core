/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.core.tests.performance;

import java.text.NumberFormat;

import junit.framework.*;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.search.*;
import org.eclipse.jdt.internal.core.search.processing.IJob;


/**
 */
public class FullSourceWorkspaceSearchTests extends FullSourceWorkspaceTests implements IJavaSearchConstants {
	

	// Tests counters
	private static int TESTS_COUNT = 0;
	private final static int ITERATIONS_COUNT = 10;

	// Search stats
	private static int[] REFERENCES = new int[4];
	private static int ALL_TYPES_NAMES = 0;

	// Scopes
	IJavaSearchScope workspaceScope;

	/**
	 * @param name
	 */
	public FullSourceWorkspaceSearchTests(String name) {
		super(name);
	}

//	static {
//		TESTS_NAMES = new String[] { "testPerfSearchType" };
//	}
	public static Test suite() {
		Test suite = buildSuite(FullSourceWorkspaceSearchTests.class);
		TESTS_COUNT = suite.countTestCases();
		return suite;
	}
	protected void setUp() throws Exception {
		super.setUp();
		this.resultCollector = new JavaSearchResultCollector();
		this.workspaceScope = SearchEngine.createWorkspaceScope();
	}
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		TESTS_COUNT--;
		if (TESTS_COUNT == 0) {
			// Print statistics
			System.out.println("-------------------------------------");
			System.out.println("Search performance test statistics:");
			NumberFormat intFormat = NumberFormat.getIntegerInstance();
			System.out.println("  - "+intFormat.format(REFERENCES[0])+" type references found.");
			System.out.println("  - "+intFormat.format(REFERENCES[1])+" field references found.");
			System.out.println("  - "+intFormat.format(REFERENCES[2])+" method references found.");
			System.out.println("  - "+intFormat.format(REFERENCES[3])+" constructor references found.");
			System.out.println("  - "+intFormat.format(ALL_TYPES_NAMES)+" all types names.");
			System.out.println("-------------------------------------\n");
		}
	}
	/**
	 * Simple search result collector: only count matches.
	 */
	class JavaSearchResultCollector implements IJavaSearchResultCollector {
		int count = 0;
		public void aboutToStart() {
		}
		public void accept(IResource resource, int start, int end, IJavaElement element, int accuracy) {
			this.count++;
		}
		public void done() {
		}
		public IProgressMonitor getProgressMonitor() {
			return null;
		}
	}
	/**
	 * Simple type name requestor: only count classes and interfaces.
	 */
	class SearchTypeNameRequestor implements ITypeNameRequestor {
		int count = 0;
		public void acceptClass(char[] packageName, char[] simpleTypeName, char[][] enclosingTypeNames, String path){
			this.count++;
		}
		public void acceptInterface(char[] packageName, char[] simpleTypeName, char[][] enclosingTypeNames, String path){
			this.count++;
		}
	}
	/**
	 * Simple Job which does nothing
	 */
	class	 DoNothing implements IJob {
		/**
		 * Answer true if the job belongs to a given family (tag)
		 */
		public boolean belongsTo(String jobFamily) {
			return true;
		}
		/**
		 * Asks this job to cancel its execution. The cancellation
		 * can take an undertermined amount of time.
		 */
		public void cancel() {
			// nothing to cancel
		}
		/**
		 * Ensures that this job is ready to run.
		 */
		public boolean isReadyToRun() {
			// always ready to do nothing
			return true;
		}
		/**
		 * Execute the current job, answer whether it was successful.
		 */
		public boolean execute(IProgressMonitor progress) {
			// always succeed to do nothing
			return true;
		}
	}
	/**
	 * Job to measure times in same thread than index manager.
	 */
	class	 Measuring implements IJob {
		boolean start;
		Measuring(boolean start) {
			this.start = start;
		}
		public boolean belongsTo(String jobFamily) {
			return true;
		}
		public void cancel() {
			// nothing to cancel
		}
		public boolean isReadyToRun() {
			return true;
		}
		/**
		 * Execute the current job, answer whether it was successful.
		 */
		public boolean execute(IProgressMonitor progress) {
			if (start) {
				startMeasuring();
			} else {
				stopMeasuring();
				commitMeasurements();
				assertPerformance();
			}
			return true;
		}
	}

	protected JavaSearchResultCollector resultCollector;

	protected void search(String patternString, int searchFor, int limitTo) throws CoreException {
		new SearchEngine().search(
			ResourcesPlugin.getWorkspace(), 
			patternString, 
			searchFor,
			limitTo,
			SearchEngine.createWorkspaceScope(), 
			this.resultCollector);
	}

	// Do NOT forget that tests must start with "testPerf"

	/**
	 * Performance tests for search: Indexing.
	 * 
	 * First wait that already started indexing jobs end before perform test.
	 * Consider this initial indexing jobs as warm-up for this test.
	 */
	public void testIndexing() throws CoreException {
		tagAsSummary("Search>Indexing", true); // put in fingerprint

		// Wait for indexing end (we use initial indexing as warm-up)
		waitUntilIndexesReady();
		
		// Remove all previous indexing
		INDEX_MANAGER.removeIndexFamily(new Path(""));
		INDEX_MANAGER.reset();
		
		// Clean memory
		runGc();

		// Restart brand new indexing
		INDEX_MANAGER.request(new Measuring(true/*start measuring*/));
		for (int i=0, length=ALL_PROJECTS.length; i<length; i++) {
			INDEX_MANAGER.indexAll(ALL_PROJECTS[i].getProject());
		}
		
		// Wait for indexing end
		waitUntilIndexesReady();
		
		// Commit
		INDEX_MANAGER.request(new Measuring(false /*end measuring*/));
		waitUntilIndexesReady();
	}

	/**
	 * Performance tests for search: Declarations Types Names.
	 * 
	 * First wait that already started indexing jobs end before perform test.
	 * Perform one search before measure performance for warm-up.
	 * 
	 * @deprecated As we use deprecated API
	 */
	public void testSearchAllTypeNames() throws CoreException {
		tagAsSummary("Search>Names>Workspace", true); // put in fingerprint
		SearchTypeNameRequestor requestor = new SearchTypeNameRequestor();

		// Wait for indexing end
		waitUntilIndexesReady();

		// Warm up
		new SearchEngine().searchAllTypeNames(
			ResourcesPlugin.getWorkspace(),
			null,
			null,
			PATTERN_MATCH,
			CASE_INSENSITIVE,
			IJavaSearchConstants.TYPE,
			this.workspaceScope, 
			requestor,
			WAIT_UNTIL_READY_TO_SEARCH,
			null);

		// Clean memory
		runGc();

		// Measures
		for (int i=0; i<MEASURES_COUNT; i++) {
			startMeasuring();
			for (int j=0; j<ITERATIONS_COUNT; j++) {
				new SearchEngine().searchAllTypeNames(
					ResourcesPlugin.getWorkspace(),
					null,
					null,
					PATTERN_MATCH,
					CASE_INSENSITIVE,
					IJavaSearchConstants.TYPE,
					this.workspaceScope, 
					requestor,
					WAIT_UNTIL_READY_TO_SEARCH,
					null);
			}
			stopMeasuring();
		}
		
		// Commit
		commitMeasurements();
		assertPerformance();

		// Store counter
		ALL_TYPES_NAMES = requestor.count;
	}

	/**
	 * Performance tests for search: Occurence Types.
	 * 
	 * First wait that already started indexing jobs end before perform test.
	 * Perform one search before measure performance for warm-up.
	 * 
	 * Note that following search have been tested:
	 *		- "String":				> 65000 macthes (CAUTION: needs -Xmx512M)
	 *		- "Object":			13497 matches
	 *		- ""IResource":	5886 macthes
	 *		- "JavaCore":		2145 matches
	 */
	public void testSearchType() throws CoreException {
		tagAsSummary("Search>Occurences>Types", true); // put in fingerprint

		// Wait for indexing end
		waitUntilIndexesReady();

		// Warm up
		search("JavaCore", TYPE, ALL_OCCURRENCES);

		// Clean memory
		runGc();

		// Measures
		for (int i=0; i<MEASURES_COUNT; i++) {
			startMeasuring();
			search("JavaCore", TYPE, ALL_OCCURRENCES);
			stopMeasuring();
		}
		
		// Commit
		commitMeasurements();
		assertPerformance();

		// Store counter
		REFERENCES[0] = this.resultCollector.count;
	}

	/**
	 * Performance tests for search: Declarations Types Names.
	 * 
	 * First wait that already started indexing jobs end before perform test.
	 * Perform one search before measure performance for warm-up.
	 */
	public void testSearchField() throws CoreException {
		tagAsSummary("Search>Occurences>Fields", true); // put in fingerprint

		// Wait for indexing end
		waitUntilIndexesReady();

		// Warm up
		search("FILE", FIELD, ALL_OCCURRENCES);

		// Clean memory
		runGc();

		// Measures
		for (int i=0; i<MEASURES_COUNT; i++) {
			startMeasuring();
			search("FILE", FIELD, ALL_OCCURRENCES);
			stopMeasuring();
		}
		
		// Commit
		commitMeasurements();
		assertPerformance();

		// Store counter
		REFERENCES[1] = this.resultCollector.count;
	}

	/**
	 * Performance tests for search: Declarations Types Names.
	 * 
	 * First wait that already started indexing jobs end before perform test.
	 * Perform one search before measure performance for warm-up.
	 */
	public void testSearchMethod() throws CoreException {
		tagAsSummary("Search>Occurences>Methods", true); // put in fingerprint

		// Wait for indexing end
		waitUntilIndexesReady();

		// Warm up
		search("equals", METHOD, ALL_OCCURRENCES);

		// Clean memory
		runGc();

		// Measures
		for (int i=0; i<MEASURES_COUNT; i++) {
			startMeasuring();
			search("equals", METHOD, ALL_OCCURRENCES);
			stopMeasuring();
		}
		
		// Commit
		commitMeasurements();
		assertPerformance();

		// Store counter
		REFERENCES[2] = this.resultCollector.count;
	}

	/**
	 * Performance tests for search: Declarations Types Names.
	 * 
	 * First wait that already started indexing jobs end before perform test.
	 * Perform one search before measure performance for warm-up.
	 */
	public void testSearchConstructor() throws CoreException {
		tagAsSummary("Search>Occurences>Constructors", true); // put in fingerprint

		// Wait for indexing end
		waitUntilIndexesReady();

		// Warm up
		search("String", CONSTRUCTOR, ALL_OCCURRENCES);

		// Clean memory
		runGc();

		// Measures
		for (int i=0; i<MEASURES_COUNT; i++) {
			startMeasuring();
			search("String", CONSTRUCTOR, ALL_OCCURRENCES);
			stopMeasuring();
		}
		
		// Commit
		commitMeasurements();
		assertPerformance();

		// Store counter
		REFERENCES[3] = this.resultCollector.count;
	}
}
