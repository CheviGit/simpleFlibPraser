package com.company.parser.local.model;

import java.util.ArrayList;
import java.util.List;

import tv.tz.catalog.agent.model.ImportResultEnum;

public class Book {

	private Integer BookId;
	private String title;
	private String date;
	private String lang;
	private String fileName;
	private String format;
	private String folder;
	private String seriesName;
	private Integer SeriesId;
	private Integer SeqNumber;
	private Integer bookSize;
	private String fullFileName;
	private List<Author> authors;
	private List<Genre> genres;
	
	private List<ImportResultEnum> previousResult;
	private Long anthologyId;
	private boolean isProcessed = false;
	private String parseResult;
	

	public Integer getBookId() {
		return BookId;
	}

	public String getTitle() {
		return title;
	}

	public String getDate() {
		return date;
	}

	public String getLang() {
		return lang;
	}

	public String getFileName() {
		return fileName;
	}

	public String getFormat() {
		return format;
	}

	public void setBookId(Integer bookId) {
		BookId = bookId;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public Integer getSeriesId() {
		return SeriesId;
	}

	public Integer getSeqNumber() {
		return SeqNumber;
	}

	public void setSeriesId(Integer seriesId) {
		SeriesId = seriesId;
	}

	public void setSeqNumber(Integer seqNumber) {
		SeqNumber = seqNumber;
	}

	public String getFolder() {
		return folder;
	}

	public void setFolder(String folder) {
		this.folder = folder;
	}

	public String getSeriesName() {
		return seriesName;
	}

	public void setSeriesName(String cycle) {
		this.seriesName = cycle;
	}

	public Long getAnthologyId() {
		return anthologyId;
	}

	public void setAnthologyId(Long anthologyId) {
		this.anthologyId = anthologyId;
	}

	public Integer getBookSize() {
		return bookSize;
	}

	public void setBookSize(Integer bookSize) {
		this.bookSize = bookSize;
	}	

	public List<ImportResultEnum> getPreviousResult() {
		return previousResult;
	}

	public void setPreviousResult(List<ImportResultEnum> previousResult) {
		this.previousResult = previousResult;
	}
	
	public String getFullFileName() {
		return fullFileName;
	}

	public void setFullFileName(String fullFileName) {
		this.fullFileName = fullFileName;
	}

	public boolean isProcessed() {
		return isProcessed;
	}

	public void setProcessed(boolean isProcessed) {
		this.isProcessed = isProcessed;
	}

	public List<Author> getAuthors() {
		return authors;
	}

	public void addAuthor(Author author) {
		if(this.authors == null){
			this.authors = new ArrayList<Author>();
			this.authors.add(author);
		} else {
			this.authors.add(author);
		}		
	}

	public List<Genre> getGenres() {
		return genres;
	}

	public void addGenre(Genre genre) {
		if(this.genres == null){
			this.genres = new ArrayList<Genre>();
			this.genres.add(genre);
		} else {
			this.genres.add(genre);
		}
	}
	
	public String getParseResult() {
		return parseResult;
	}

	public void setParseResult(String parseResult) {
		this.parseResult = parseResult;
	}

	@Override
	public String toString() {
		return "Book [BookId=" + BookId + ", title=" + title + (SeriesId != 0 ? ", SeriesId=" + SeriesId : "") + (seriesName != null ? ", seriesName=" + seriesName : "")
				+ ",  fileName=" + fileName + (SeqNumber != 0 ? ", SeqNumber=" + SeqNumber : "");
	}

}
