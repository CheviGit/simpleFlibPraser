package com.company.parser.local;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Класс для записи логов
 * */
public class LogFileWriter {

	private SimpleDateFormat sdf;
	private PrintWriter out = null;

	public LogFileWriter() {
		super();
	}

	/**
	 * Создаётся Лог файл. Первой строкой записывается дата и время запуска. 
	 */
	public void init(String logFilePath) {
		try {			
			int lastIndexOf = logFilePath.lastIndexOf("/");
			if(lastIndexOf != -1){
				String path = logFilePath.substring(0, lastIndexOf);				
				File dir = new File(path);
				if (dir.exists() == false) {
					dir.mkdir();
				}				
			}
			File file = new File(logFilePath);		
			out = new PrintWriter(new BufferedWriter(new FileWriter(file)));
			sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss"); 
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void write(String string) {
		
		out.println(sdf.format(new Date(System.currentTimeMillis())) + ": " + string);
		out.flush();
	}

	public void close() {
		out.close();
	}

}
