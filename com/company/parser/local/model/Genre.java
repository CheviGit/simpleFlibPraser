package com.company.parser.local.model;

public class Genre {
	
	private String genreCode;
	private String parentCode;
	private String fb2Code;
	private String genre;
	
	public String getGenreCode() {
		return genreCode;
	}
	public String getParentCode() {
		return parentCode;
	}
	public String getFb2Code() {
		return fb2Code;
	}
	public String getGenre() {
		return genre;
	}
	public void setGenreCode(String genreCode) {
		this.genreCode = genreCode;
	}
	public void setParentCode(String parentCode) {
		this.parentCode = parentCode;
	}
	public void setFb2Code(String fb2Code) {
		this.fb2Code = fb2Code;
	}
	public void setGenre(String genre) {
		this.genre = genre;
	}
}
