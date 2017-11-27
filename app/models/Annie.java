package models;

import gate.Corpus;
import gate.CorpusController;
import gate.util.GateException;
import gate.util.persistence.PersistenceManager;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;

public class Annie {

	final static Logger logger = Logger.getLogger(Annie.class);
	private CorpusController annieController;

	public void initAnnie() throws GateException, IOException {
		logger.info("Initialising processing engine...");
		File annieGapp = new File(System.getProperty("user.dir"), "/conf/resources/GATEFiles/ANNIEResumeParser.gapp");
		annieController = (CorpusController) PersistenceManager.loadObjectFromFile(annieGapp);
		logger.info("...processing engine loaded");
	}

	public void setCorpus(Corpus corpus) {
		annieController.setCorpus(corpus);
	}

	public void execute() throws GateException {
		logger.info("Running processing engine...");
		annieController.execute();
		logger.info("...processing engine complete");
	}
}
