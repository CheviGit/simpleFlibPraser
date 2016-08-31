package com.company.parser.local;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlibustaStatistic {

	private static final Logger log = LoggerFactory.getLogger(FlibustaStatistic.class);

	private AtomicInteger fileCount;

	private HashMap<String, AtomicInteger> errors = new HashMap<String, AtomicInteger>();

	public FlibustaStatistic(String logFilePath) {
		fileCount = new AtomicInteger();
	}
	
	public void incrementFileCount(String result) {
		fileCount.incrementAndGet();
	}

	public void addReasonOfError(String error) {
		AtomicInteger count = errors.get(error);
		if (count == null) {
			count = new AtomicInteger();
			errors.put(error, count);
		}
		count.incrementAndGet();
	}

	public void printStatistic(boolean forse) {
		if ((fileCount.get() != 0 && fileCount.get() % 100 == 0) || forse) {
			log.info("=================STATITICS ====================");
			log.info("In total it is processed " + fileCount + " books");
			
			Integer totalError = 0;			
			for (Entry<String, AtomicInteger> entry : errors.entrySet()) {
				totalError = totalError + entry.getValue().get();
			}
			log.info("From them it is successfully processed " + (fileCount.get() - totalError) + " books");
			log.info("From them it is processed with errors " + totalError + " books, by reason of error:");
			for (Entry<String, AtomicInteger> entry : errors.entrySet()) {
				log.info(entry.getKey() + ":" + entry.getValue());				
			}
			
			log.info("=================STATITICS END ====================");
		}
	}
}
