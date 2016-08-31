package com.parser.company.local;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.company.common.persistance.AgentDao;
import com.company.common.persistance.AgentDaoImpl;
import com.company.parser.exception.AgentParserException;
import com.company.parser.local.model.Author;
import com.company.parser.local.model.Book;
import com.company.parser.local.model.Genre;
import com.company.parser.utils.ParseUtils;

import com.company.catalog.MultiValueHashMap;

public class FlibustaDBConnect {

	private static String url = "jdbc:sqlite:";

	public static void setURL(String pathToDB) {
		url = url + pathToDB;
	}

	public static Map<Integer, Book> getAllBookDescriptions() throws AgentParserException {

		Map<Integer, Book> ret = new HashMap<Integer, Book>();

		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		String sql = "select BookId, Title, UpdateDate, Lang, FileName, Ext, Folder, SeriesID, SeqNumber, BookSize from Books group by FileName COLLATE NOCASE order by BookID";

		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection(url);
			pstmt = conn.prepareStatement(sql);
			rs = pstmt.executeQuery();	
			while (rs.next()) {
				Book book = new Book();
				book.setBookId(rs.getInt("BookId"));
				book.setTitle(rs.getString("Title"));
				book.setDate(rs.getString("UpdateDate"));
				book.setLang(rs.getString("Lang"));
				book.setFileName(rs.getString("FileName"));
				book.setFormat(rs.getString("Ext"));
				book.setFolder(rs.getString("Folder"));
				book.setSeriesId(rs.getInt("SeriesID"));
				book.setSeqNumber(rs.getInt("SeqNumber"));
				book.setBookSize(rs.getInt("BookSize"));

				ret.put(rs.getInt("BookId"), book);
			}
		} catch (ClassNotFoundException e) {
			throw new AgentParserException("Not Found JDBC ", e);
		} catch (SQLException e) {
			throw new AgentParserException("Error when try get data from DB ", e);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException e) {
				}
			}
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException e) {
				}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
				}
			}
		}
		return ret;
	}

	public static void getAuthors(Map<Integer, Book> allBookDescriptions) throws AgentParserException {

		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		StringBuilder sb = new StringBuilder();
		sb.append("select b.BookID, a.FirstName, a.MiddleName, a.LastName ");
		sb.append("from Authors a ");
		sb.append("inner join Author_List al on al.AuthorID=a.AuthorID ");
		sb.append("inner join Books b on b.BookID=al.BookID ");
		sb.append("group by FileName COLLATE NOCASE ");
		sb.append("order by b.BookID");

		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection(url);
			pstmt = conn.prepareStatement(sb.toString());
			rs = pstmt.executeQuery();
			while (rs.next()) {
				Author author = new Author();
				author.setFirstName(rs.getString("FirstName"));
				author.setMiddleName(rs.getString("MiddleName"));
				author.setLastName(rs.getString("LastName"));
				try {
					allBookDescriptions.get(rs.getInt("BookID")).addAuthor(author);
				} catch (NullPointerException e) {
				}
			}
		} catch (ClassNotFoundException e) {
			throw new AgentParserException("Not Found JDBC ", e);
		} catch (SQLException e) {
			throw new AgentParserException("Error when try get data from DB ", e);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException e) {
				}
			}
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException e) {
				}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
				}
			}
		}
	}

	public static void getGenres(Map<Integer, Book> allBookDescriptions) throws AgentParserException {

		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		StringBuilder sb = new StringBuilder();
		sb.append("select b.BookID, g.GenreCode, g.ParentCode, g.FB2Code, g.GenreAlias ");
		sb.append("from Genres g ");
		sb.append("inner join Genre_List gl on gl.GenreCode=g.GenreCode ");
		sb.append("inner join Books b on b.BookID=gl.BookID ");
		sb.append("group by FileName COLLATE NOCASE ");
		sb.append("order by b.BookID");

		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection(url);
			pstmt = conn.prepareStatement(sb.toString());
			rs = pstmt.executeQuery();
			while (rs.next()) {
				Genre genre = new Genre();
				genre.setGenreCode(rs.getString("GenreCode"));
				genre.setParentCode(rs.getString("ParentCode"));
				genre.setFb2Code(rs.getString("FB2Code"));
				genre.setGenre(rs.getString("GenreAlias"));
				try {
					allBookDescriptions.get(rs.getInt("BookID")).addGenre(genre);
				} catch (NullPointerException e) {
				}
			}
		} catch (ClassNotFoundException e) {
			throw new AgentParserException("Not Found JDBC ", e);
		} catch (SQLException e) {
			throw new AgentParserException("Error when try get data from DB ", e);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException e) {
				}
			}
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException e) {
				}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
				}
			}
		}
	}

	public static MultiValueHashMap<Integer, Book> getSeries(Map<Integer, Book> allBooks) throws AgentParserException {
		MultiValueHashMap<Integer, Book> ret = new MultiValueHashMap<Integer, Book>();

		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		String sql = "select b.BookID, s.SeriesTitle from Books b inner join Series s on b.SeriesID = s.SeriesID order by b.BookID";

		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection(url);
			pstmt = conn.prepareStatement(sql);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				try {
					Book book = allBooks.get(rs.getInt("BookID"));
					book.setSeriesName(ParseUtils.toClearTextOfPunctuationMarks(rs.getString("SeriesTitle")));

					ret.put(book.getSeriesId(), book);
					book = null;
				} catch (NullPointerException e) {
				}
			}
		} catch (ClassNotFoundException e) {
			throw new AgentParserException("Not Found JDBC ", e);
		} catch (SQLException e) {
			throw new AgentParserException("Error when try get data from DB ", e);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException e) {
				}
			}
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException e) {
				}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
				}
			}
		}
		return ret;
	}

	public static List<String> getCatalogGenres() throws AgentParserException {
		AgentDao dao = new AgentDaoImpl();
		return dao.getGenres();
	}
}
