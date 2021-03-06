/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.model.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.common.LoggingConfigurationManager;
import com.evolveum.midpoint.util.aspect.MidpointAspect;
import com.evolveum.midpoint.util.aspect.ProfilingDataManager;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 * @author semancik
 *
 */
public class LogfileTestTailer {
	
	private static final String LOG_FILENAME = "target/test.log";
	public static final String MARKER = "_M_A_R_K_E_R_";
	
	public static final String LEVEL_ERROR = "ERROR";
	public static final String LEVEL_WARN = "WARN";
	public static final String LEVEL_INFO = "INFO";
	public static final String LEVEL_DEBUG = "DEBUG";
	public static final String LEVEL_TRACE = "TRACE";

	// see also the 'optimization' at the beginning of processLogLine() - interfering with these patterns
	private static final Pattern markerPattern = Pattern.compile(".*\\[[^]]*\\]\\s+(\\w+)\\s+\\S+\\s+"+MARKER+"\\s+(\\w+).*");
	private static final Pattern markerPatternPrefix = Pattern.compile(".*\\[[^]]*\\]\\s+(\\w+)\\s+\\S+\\s+.*"+MARKER+"\\s+(\\w+).*");
	public static final Pattern auditPattern = 
			Pattern.compile(".*\\[[^]]*\\]\\s+(\\w+)\\s+\\("+LoggingConfigurationManager.AUDIT_LOGGER_NAME+"\\):\\s*(.*)");
	public static final Pattern levelPattern = 
			Pattern.compile(".*\\[[^]]*\\]\\s+(\\w+)\\s+(.*)");
	
	
	final static Trace LOGGER = TraceManager.getTrace(LogfileTestTailer.class);
	
	private Reader fileReader;
	private BufferedReader reader;
	private boolean seenMarker;
	private Set<String> loggedMarkers;
	private List<String> auditMessages = new ArrayList<String>();
	private String expectedMessage;
	private String expectedMessageLine;
	private boolean allowPrefix = false;
	private Collection<String> errors = new ArrayList<String>();
	private Collection<String> warnings = new ArrayList<String>();
	
	public LogfileTestTailer() throws IOException {
		this(true);
	}
	
	public LogfileTestTailer(boolean skipCurrentContent) throws IOException {
		reset();
		File file = new File(LOG_FILENAME);

		// doing skipping on FileInputStream instead of BufferedReader, hoping it is faster
		// as the sequential scanning of the file is eliminated
		FileInputStream fileInputStream = new FileInputStream(file);
		if (skipCurrentContent) {
			long skipped = fileInputStream.skip(file.length());
			LOGGER.info("Skipped = {}", skipped);
		}
		fileReader = new InputStreamReader(fileInputStream);
		reader = new BufferedReader(fileReader);
	}
	
	public boolean isAllowPrefix() {
		return allowPrefix;
	}

	public void setAllowPrefix(boolean allowPrefix) {
		this.allowPrefix = allowPrefix;
	}

	public Collection<String> getErrors() {
		return errors;
	}

	public Collection<String> getWarnings() {
		return warnings;
	}

	public void close() throws IOException {
		reader.close();
		fileReader.close();
	}
	
	public void reset() {
		seenMarker = false;
		loggedMarkers = new HashSet<String>();
		auditMessages = new ArrayList<String>();
		expectedMessageLine = null;
	}

	public boolean isSeenMarker() {
		return seenMarker;
	}

	public void setSeenMarker(boolean seenMarker) {
		this.seenMarker = seenMarker;
	}

	public void tail() throws IOException {
		while (true) {
		    String line = reader.readLine();
		    if (line == null) {
		    	break;
		    }
			processLogLine(line);
		}
	}

	private void processLogLine(String line) {

		if (line.length() > 0 && Character.isWhitespace(line.charAt(0))) {
			return;			// ugly hack: getting rid of long 'continuation' lines that are not matched by any patterns but terribly slow down the processing
		}
		
		// Match marker
		Pattern pattern = markerPattern;
		if (allowPrefix) {
			pattern = markerPatternPrefix;
		}
		Matcher matcher = pattern.matcher(line);
		while (matcher.find()) {
			seenMarker = true;
			String level = matcher.group(1);
			String subsystemName = matcher.group(2);
			recordMarker(level,subsystemName);
		}
		
		// Match audit
		matcher = auditPattern.matcher(line);
		while (matcher.find()) {
			String level = matcher.group(1);
			String message = matcher.group(2);
			recordAuditMessage(level,message);
		}
		if (expectedMessage != null && line.contains(expectedMessage)) {
			expectedMessageLine = line;
		}
		
		// Match errors and warnings
		matcher = levelPattern.matcher(line);
		while (matcher.find()) {
			String level = matcher.group(1);
			String message = matcher.group(2);
			if ("ERROR".equals(level)) {
				errors.add(message);
			}
			if ("WARN".equals(line)) {
				warnings.add(message);
			}
		}
	}
	
	private void recordMarker(String level, String subsystemName) {
		String key = constructKey(level, subsystemName);
		loggedMarkers.add(key);
	}
	
	private void recordAuditMessage(String level, String message) {
		auditMessages.add(message);
	}

	private String constructKey(String level, String subsystemName) {
		return level+":"+subsystemName;
	}
	
	public void assertMarkerLogged(String level, String subsystemName) {
		assert loggedMarkers.contains(constructKey(level, subsystemName)) : level + " in " + subsystemName + " was not logged";
	}
	
	public void assertMarkerNotLogged(String level, String subsystemName) {
		assert !loggedMarkers.contains(constructKey(level, subsystemName)) : level + " in " + subsystemName + " was logged (while not expecting it)";
	}
	
	public void setExpecteMessage(String expectedMessage) {
		this.expectedMessage = expectedMessage;
		this.expectedMessageLine = null;
	}
	
	public void assertExpectedMessage() {
		assert expectedMessageLine != null : "The expected message was not seen";
	}
	
	public void assertNoAudit() {
		assert auditMessages.isEmpty() : "Audit messages not empty: "+auditMessages;
	}
	
	public void assertAudit() {
		assert !auditMessages.isEmpty() : "No audit message";
	}

	public void assertAudit(String message) {
		assert auditMessages.contains(message) : "No audit message: "+message;
	}
	
	public void assertAuditRequest() {
		for (String message: auditMessages) {
			if (message.contains("stage REQUEST")) {
				return;
			}
			if (message.contains("es=REQUEST")) {
				return;
			}
		}
		assert false: "No request audit message";
	}

	public void assertAuditExecution() {
		for (String message: auditMessages) {
			if (message.contains("stage EXECUTION")) {
				return;
			}
			if (message.contains("es=EXECUTION")) {
				return;
			}
		}
		assert false: "No execution audit message";
	}

	public void assertAudit(int messageCount) {
		assert auditMessages.size() == messageCount : "Wrong number of audit messages, expected "+messageCount+", was "+auditMessages.size();
	}

	/**
	 * Log all levels in all subsystems.
	 */
	public void log() {
		for (ProfilingDataManager.Subsystem subsystem: ProfilingDataManager.subsystems) {
			logAllLevels(LOGGER, subsystem.name());
		}
		logAllLevels(LOGGER, null);
	}
	
	private void logAllLevels(Trace logger, String subsystemName) {
		String message = MARKER+" "+subsystemName;
		String previousSubsystem = MidpointAspect.swapSubsystemMark(subsystemName);
		logger.trace(message);
		logger.debug(message);
		logger.info(message);
		logger.warn(message);
		logger.error(message);
		MidpointAspect.swapSubsystemMark(previousSubsystem);
	}
	
	public void logAndTail() throws IOException {
		log();
		// Some pause here?
		tail();
	}

}
