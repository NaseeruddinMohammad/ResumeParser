package controllers;

import gate.util.GateException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.exception.TikaException;
import org.json.simple.JSONObject;
import org.xml.sax.SAXException;
import org.apache.log4j.Logger;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;

import models.ResumeParser;

public class ResumeController extends Controller {

	final static Logger logger = Logger.getLogger(ResumeController.class);
	public static ResumeParser resumeParser;

	public static Result postResume() throws GateException, IOException, SAXException, TikaException {
		MultipartFormData body = request().body().asMultipartFormData();
		FilePart resume = body.getFile("resume");
		File file = resume.getFile();
		long fileLength = null != file ? file.length() : 0;
		if (fileLength > 0 && fileLength < 1048576) {
			Calendar calendar = Calendar.getInstance();
			String fileName = resume.getFilename();
			String ext = FilenameUtils.getExtension(fileName);
			if (ext.equalsIgnoreCase("html") || ext.equalsIgnoreCase("txt") || ext.equalsIgnoreCase("doc")
					|| ext.equalsIgnoreCase("pdf") || ext.equalsIgnoreCase("docx")) {
				fileName = FilenameUtils.removeExtension(fileName);
				fileName = "resume_" + calendar.getTimeInMillis() + "." + ext;
				File destination = new File(ResumeController.class.getResource("/resumes/") + fileName);
				storeResume(file, destination);
				resumeParser = new ResumeParser(ResumeController.class.getResource("/resumes/") + fileName);
				JSONObject parsedResume = resumeParser.getTransducedResume();
				return ok(Json.toJson(parsedResume));
			} else {
				logger.error(ext + " format  is not supported, Please send a Valid format!!");
				return badRequest(ext + " format  is not supported, Please send a Valid format!!");
			}
		} else {
			logger.error("Input file doesn't meet size requirements (>0 && <1MB): file size=" + fileLength);
			return badRequest("Input file doesn't meet size requirements (>0 && <1MB): file size=" + fileLength);
		}
	}

	public static void storeResume(File file, File destination) throws IOException {
		InputStream inStream = null;
		OutputStream outStream = null;
		inStream = new FileInputStream(file);
		outStream = new FileOutputStream(destination);
		byte[] buffer = new byte[8192];
		int length;
		while ((length = inStream.read(buffer)) > 0) {
			outStream.write(buffer, 0, length);
		}
		inStream.close();
		outStream.close();
	}
}