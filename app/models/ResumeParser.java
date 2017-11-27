package models;

import static gate.Utils.stringFor;
import gate.Annotation;
import gate.AnnotationSet;
import gate.Corpus;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.util.GateException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.apache.log4j.Logger;
import play.Configuration;
import play.Play;

public class ResumeParser {

	final static Logger logger = Logger.getLogger(ResumeParser.class);
	public File tikkaConvertedFile;
	public JSONObject parsedJSON;
	public JSONObject resultJSON;

	public ResumeParser(String inputFileName) throws IOException, SAXException, TikaException, GateException {
		Configuration conf = Play.application().configuration();
		System.setProperty("gate.home", "/opt/");
		conf.getString("gate.home");
		tikkaConvertedFile = parseToHTMLUsingApacheTikka(inputFileName);
		if (tikkaConvertedFile != null) {
			parsedJSON = loadGateAndAnnie(tikkaConvertedFile);
		}
		Utils u = new Utils();
		resultJSON = u.getResultJSON(parsedJSON);
	}

	public static File parseToHTMLUsingApacheTikka(String file) throws IOException, SAXException, TikaException {
		String ext = FilenameUtils.getExtension(file);
		String outputFileFormat = "";
		if (ext.equalsIgnoreCase("html") || ext.equalsIgnoreCase("pdf") || ext.equalsIgnoreCase("doc")
				|| ext.equalsIgnoreCase("docx")) {
			outputFileFormat = ".html";
		} else if (ext.equalsIgnoreCase("txt")) {
			outputFileFormat = ".txt";
		} else {
			logger.info("Input format of the file " + file + " is not supported.");
			return null;
		}
		String OUTPUT_FILE_NAME = FilenameUtils.removeExtension(file) + outputFileFormat;
		ContentHandler handler = new ToXMLContentHandler();
		InputStream stream = new FileInputStream(file);
		AutoDetectParser parser = new AutoDetectParser();
		Metadata metadata = new Metadata();
		try {
			parser.parse(stream, handler, metadata);
			FileWriter htmlFileWriter = new FileWriter(OUTPUT_FILE_NAME);
			htmlFileWriter.write(handler.toString());
			htmlFileWriter.flush();
			htmlFileWriter.close();
			return new File(OUTPUT_FILE_NAME);
		} finally {
			stream.close();
		}
	}

	public JSONObject loadGateAndAnnie(File file) throws GateException, IOException, NullPointerException {
		logger.info("Initialising basic system...");
		Gate.init();
		logger.info("...Basic System Initialised");
		Annie annie = new Annie();
		annie.initAnnie();
		Corpus corpus = Factory.newCorpus("Annie corpus");
		String current = new File(".").getAbsolutePath();
		URL u = file.toURI().toURL();
		FeatureMap params = Factory.newFeatureMap();
		params.put("sourceUrl", u);
		params.put("preserveOriginalContent", new Boolean(true));
		params.put("collectRepositioningInfo", new Boolean(true));
		logger.info("Creating doc for " + u);
		Document resume = (Document) Factory.createResource("gate.corpora.DocumentImpl", params);
		corpus.add(resume);
		annie.setCorpus(corpus);
		annie.execute();
		Iterator iter = corpus.iterator();
		JSONObject parsedJSON = new JSONObject();
		logger.info("Started parsing...");
		if (iter.hasNext()) {
			JSONObject profileJSON = new JSONObject();
			Document doc = (Document) iter.next();
			AnnotationSet defaultAnnotSet = doc.getAnnotations();
			AnnotationSet curAnnSet;
			Iterator it;
			Annotation currAnnot;
			curAnnSet = defaultAnnotSet.get("NameFinder");
			if (curAnnSet.iterator().hasNext()) {
				currAnnot = (Annotation) curAnnSet.iterator().next();
				JSONObject nameJson = new JSONObject();
				String[] nameFeatures = new String[] { "firstName", "middleName", "surname" };
				for (String feature : nameFeatures) {
					String s = (String) currAnnot.getFeatures().get(feature);
					if (s != null && s.length() > 0) {
						nameJson.put(feature, s);
					}
				}
				profileJSON.put("name", nameJson);
			}
			curAnnSet = defaultAnnotSet.get("TitleFinder");
			if (curAnnSet.iterator().hasNext()) {
				currAnnot = (Annotation) curAnnSet.iterator().next();
				String title = stringFor(doc, currAnnot);
				if (title != null && title.length() > 0) {
					profileJSON.put("title", title);
				}
			}
			String[] annSections = new String[] { "EmailFinder", "AddressFinder", "PhoneFinder", "URLFinder" };
			String[] annKeys = new String[] { "email", "address", "phone", "url" };
			for (short i = 0; i < annSections.length; i++) {
				String annSection = annSections[i];
				curAnnSet = defaultAnnotSet.get(annSection);
				it = curAnnSet.iterator();
				JSONArray sectionArray = new JSONArray();
				while (it.hasNext()) {
					currAnnot = (Annotation) it.next();
					String s = stringFor(doc, currAnnot);
					if (s != null && s.length() > 0) {
						sectionArray.add(s);
					}
				}
				if (sectionArray.size() > 0) {
					profileJSON.put(annKeys[i], sectionArray);
				}
			}
			if (!profileJSON.isEmpty()) {
				parsedJSON.put("basics", profileJSON);
			}
			String[] otherSections = new String[] { "summary", "education_and_training", "skills", "accomplishments",
					"awards", "credibility", "extracurricular", "misc" };
			for (String otherSection : otherSections) {
				curAnnSet = defaultAnnotSet.get(otherSection);
				it = curAnnSet.iterator();
				JSONArray subSections = new JSONArray();
				while (it.hasNext()) {
					JSONObject subSection = new JSONObject();
					currAnnot = (Annotation) it.next();
					String key = (String) currAnnot.getFeatures().get("sectionHeading");
					String value = stringFor(doc, currAnnot);
					if (!StringUtils.isBlank(key) && !StringUtils.isBlank(value)) {
						subSection.put(key, value);
					}
					if (!subSection.isEmpty()) {
						subSections.add(subSection);
					}
				}
				if (!subSections.isEmpty()) {
					parsedJSON.put(otherSection, subSections);
				}
			}
			curAnnSet = defaultAnnotSet.get("work_experience");
			it = curAnnSet.iterator();
			JSONArray workExperiences = new JSONArray();
			while (it.hasNext()) {
				JSONObject workExperience = new JSONObject();
				currAnnot = (Annotation) it.next();
				String key = (String) currAnnot.getFeatures().get("sectionHeading");
				if (key.equals("work_experience_marker")) {
					String[] annotations = new String[] { "date_start", "date_end", "jobtitle", "organization" };
					for (String annotation : annotations) {
						String v = (String) currAnnot.getFeatures().get(annotation);
						if (!StringUtils.isBlank(v)) {
							workExperience.put(annotation, v);
						}
					}
					key = "text";
				}
				String value = stringFor(doc, currAnnot);
				if (!StringUtils.isBlank(key) && !StringUtils.isBlank(value)) {
					workExperience.put(key, value);
				}
				if (!workExperience.isEmpty()) {
					workExperiences.add(workExperience);
				}
			}
			if (!workExperiences.isEmpty()) {
				parsedJSON.put("work_experience", workExperiences);
			}
		}
		logger.info("Completed parsing...");
		return parsedJSON;
	}

	public JSONObject getTransducedResume() {
		return resultJSON;
	}
}