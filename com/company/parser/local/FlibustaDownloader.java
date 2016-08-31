package com.company.parser.local;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.company.parser.exception.AgentParserException;
import com.company.parser.local.model.Book;

import com.company.catalog.MultiValueHashMap;
import com.company.catalog.agent.model.ImportLogEnum;
import com.company.catalog.agent.model.ImportResultEnum;

public class FlibustaDownloader {

	private static final Logger log = LoggerFactory.getLogger(FlibustaDownloader.class);
	
	/**
	 * Get all path of files in directories
	 * */
	public List<String> getFiles(String filePath) {
		List<String> listOfFile = new ArrayList<String>();
		if (filePath != null) {
			getChild(filePath, listOfFile);
		} else {
			log.info("Wrong file path!");
		}
		return listOfFile;
	}

	private void getChild(String filePath, List<String> listOfFile) {
		File file = new File(filePath);
		if (file.isDirectory()) {
			String[] arrArg = file.list();
			if (arrArg.length != 0) {
				for (String s : arrArg) {
					String p = filePath + "/" + s;
					getChild(p, listOfFile);
				}
			}
		}
		if (file.isFile()) {
			listOfFile.add(filePath);
		}
		file = null;
	}

	/**
	 * Создать файл из архива
	 * 
	 * @param path
	 *            полный путь к архиву
	 * @param fileName
	 *            имя файла в этом архиве
	 * @param tmpDir
	 *            путь к временной папке в которой создать папку по имени файла
	 *            и в неё сохранить файл
	 * @param newFileName
	 *            новое имя для "разахивированного" файла
	 * @return полный путь к созданному файлу, либо в случае неудачи null
	 * */
	public String createFileFromZip(String path, String fileName, String tmpDir, String newFileName) throws AgentParserException {
		log.info("Unzip file {} from arhive {}", fileName, path);
		String outpath = null;
		ZipFile zipFile = null;
		InputStream stream = null;
		FileOutputStream output = null;
		try {
			File dir = new File(path);
			if (dir.exists() == true) {
				zipFile = new ZipFile(path);
				ZipEntry ze = zipFile.getEntry(fileName);
				if (ze != null) {
					stream = zipFile.getInputStream(ze);
					
					String fileNameWithoutExtension = null;
					String fileExtension = null;
					int lastIndexOf = fileName.lastIndexOf(".");
					if(lastIndexOf != -1){
						fileNameWithoutExtension = fileName.substring(0, lastIndexOf);
						fileExtension = fileName.substring(lastIndexOf, fileName.length());						
					} else {
						fileNameWithoutExtension = fileName; 
						fileExtension = fileName;
					}
					String pathForNewFile = tmpDir + "/" + fileNameWithoutExtension + "/";

					File tempDir = new File(tmpDir);
					if (tempDir.exists() == false) {
						tempDir.mkdirs();
					}

					File newDirForFile = new File(pathForNewFile);
					if (newDirForFile.exists() == false) {
						newDirForFile.mkdir();
					}

					outpath = newDirForFile.getAbsolutePath() + "/" + newFileName + fileExtension;
					File newFile = new File(outpath);
					try{
						newFile.createNewFile();
					} catch (IOException e) {
						outpath = newDirForFile.getAbsolutePath() + "/" + ze.getName();
					}

					int fileSize = (int) ze.getSize();
					byte[] buffer = new byte[fileSize];
					output = new FileOutputStream(outpath);
					int len = 0;
					while ((len = stream.read(buffer)) > 0) {
						output.write(buffer, 0, len);
					}
				} else {
					throw new AgentParserException("Error when try unzip from " + path + " file " + fileName, ImportResultEnum.FILE_NOT_FOUND);
				}
			} else {
				throw new AgentParserException("ZIP file not found: " + path, ImportResultEnum.FILE_NOT_FOUND);
			}

		} catch (IOException e) {
			log.warn("Error when try unzip from " + path + " file " + fileName, e);
			throw new AgentParserException("Error when try unzip from " + path + " file " + fileName, ImportResultEnum.FILE_NOT_FOUND, e);
		} finally {
			try {
				if (output != null) {
					output.close();
					output = null;
				}
				if (stream != null) {
					stream.close();
					stream = null;
				}
				if (zipFile != null){
					zipFile.close();
					zipFile = null;
				}
			} catch (IOException e) {
			}
		}
		return outpath;
	}

	public String getFB2FileContent(String filePath, Integer booksize) {
		String xmlHeader = null;
		FileInputStream is = null;
		try {
			byte[] buffer = new byte[booksize];
			is = new FileInputStream(new File(filePath));
			is.read(buffer);
			xmlHeader = new String(buffer, 0, booksize, defineEncoding(buffer));
			is.close();
			is = null;
		} catch (IOException e) {
		}
		return xmlHeader;
	}

	/**
	 * Получить содержимое файла из архива по имени
	 * 
	 * @param path
	 *            полный путь к архиву
	 * @param fileName
	 *            имя файла в этом архиве
	 * 
	 * @return содержимое файла в виде строки
	 * */
	public String getFileContentFromZip(String path, String fileName) {
		ZipFile zipFile;
		InputStream stream = null;
		String xmlHeader = null;
		try {
			zipFile = new ZipFile(path);
			ZipEntry ze = zipFile.getEntry(fileName);
			if (ze != null) {
				stream = zipFile.getInputStream(ze);
				int fileSize = (int) ze.getSize();
				byte[] buffer = new byte[fileSize];
				stream.read(buffer);
				if (ze.getName().endsWith(".fb2")) {
					xmlHeader = new String(buffer, 0, fileSize, defineEncoding(buffer));
				} else {
					xmlHeader = new String(buffer, 0, fileSize, "UTF-8");
				}
				stream.close();
				zipFile.close();
			} else {
				log.warn("Error when try unzip from " + path + " file " + fileName);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		return xmlHeader;
	}

	/**
	 * Open ZIP archive and read all files. Place contents of each file in the
	 * list.
	 * 
	 * @param path
	 *            to the ZIP file
	 * 
	 * @return list of content files from ZIP archive
	 * */
	public Map<String, String> getFileFromZip(String path, String fileName) {
		Map<String, String> listOfFB2 = new HashMap<String, String>();

		log.info("Reading zip file: " + path);
		FileInputStream fi;
		try {
			fi = new FileInputStream(path);
			CheckedInputStream csumi = new CheckedInputStream(fi, new Adler32());
			ZipInputStream in2 = new ZipInputStream(csumi);
			BufferedInputStream bis = new BufferedInputStream(in2);
			ZipEntry ze;
			String xmlHeader = null;
			int count = 0;
			int fileSize = 0;
			byte[] buffer = null;
			while ((ze = in2.getNextEntry()) != null) {
				fileSize = (int) ze.getSize();
				buffer = new byte[fileSize];
				bis.read(buffer);
				if (ze.getName().endsWith(".fb2")) {
					xmlHeader = new String(buffer, 0, fileSize, defineEncoding(buffer));
				} else {
					xmlHeader = new String(buffer, 0, fileSize, "UTF-8");
				}
				listOfFB2.put(ze.getName(), xmlHeader);
				count++;
				buffer = null;
				xmlHeader = null;
				ze = null;
				break;

			}
			log.info("From archive " + count + " files were read.");
			bis.close();
			in2.close();
			csumi.close();
			fi.close();
		} catch (IOException e) {
			log.warn("Can't read zip file");
		}

		return listOfFB2;
	}

	/**
	 * Получить данные с предыдущего запуска.
	 * 
	 * @param filePath
	 *            путь к лог-файлу, если он есть, получаем данные в виде HashMap
	 *            с id книги и результатами импорта.
	 * @return HashMap с id книги и результатом импорта
	 */
	public void getPreviousImportResult(Map<Integer, Book> allBookDescriptions, String filePath) {		
		try {
			File dir = new File(filePath);
			if (dir.exists() == true) {
				BufferedReader bufReader = new BufferedReader(new FileReader(dir));
				String s;
				while ((s = bufReader.readLine()) != null) {
					parseLogFileString(allBookDescriptions, s);
				}
				bufReader.close();

				String name = dir.getName();
				name = name.replace(".txt", "_previous.txt");
				String path = dir.getPath();
				path = path.replace(dir.getName(), name);
				File destFile = new File(path);
				FileUtils.copyFile(dir, destFile);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseLogFileString(Map<Integer, Book> allBookDescriptions, String string) {

		String stringBookId = getSubstringAfterString(string, ImportLogEnum.SOURCE_ID.getName());
		String stringResult = getSubstringAfterString(string, ImportLogEnum.RESULT.getName());

		if (stringBookId != null && stringResult != null) {
			try {
				Integer parseInt = Integer.parseInt(stringBookId);
				Book book = allBookDescriptions.get(parseInt);
				book.setPreviousResult(ImportResultEnum.getEnumbyName(stringResult));
//				map.putAll(parseInt, ImportResultEnum.getEnumbyName(stringResult));
			} catch (NumberFormatException e) {
			}
		}
	}

	private String getSubstringAfterString(String string, String begin) {
		String ret = null;
		int beginIndex = string.indexOf(begin);
		if (beginIndex != -1) {
			string = string.substring(beginIndex, string.length());
			int endIndex = string.indexOf(";");
			if (endIndex != -1) {
				ret = string.substring(begin.length(), endIndex);
			}
		}
		return ret;
	}

	private String defineEncoding(byte[] buffer) throws UnsupportedEncodingException {
		String encoding = null;
		String xmlHeader = new String(buffer, 0, 50, "UTF-8");
		Pattern pattern = Pattern.compile("(?i).*encoding=[\"'](.*?)[\"'].*");
		Matcher matcher = pattern.matcher(xmlHeader);
		if (matcher.find()) {
			encoding = matcher.group(1);
		} else {
			encoding = "UTF-8";
		}
		xmlHeader = null;
		pattern = null;
		matcher = null;
		return encoding;
	}
}
