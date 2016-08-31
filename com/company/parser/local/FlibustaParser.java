package com.company.parser.local;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.company.catalog.MultiValueHashMap;
import com.company.catalog.agent.model.ImportLogEnum;
import com.company.catalog.agent.model.ImportResultEnum;
import com.company.catalog.content.model.ContentStatusEnum;
import com.company.catalog.content.model.ContentTypeEnum;
import com.company.torrent.client.TorrentClientException;
import com.company.torrent.common.ITorrent;
import com.company.server.dto.DtoAgentUpdateRelations;
import com.company.server.model.CachedEntry;
import com.company.util.AplicationContextHolder;
import com.company.anthology.common.RelationshipTypeEnum;

import com.company.server.cache.LocalCacheStorage;
import com.company.server.service.CacheConnect;
import com.company.server.service.CatalogConnect;
import com.company.cache.importer.ImportTorrentFactory;
import com.company.config.TorrentParserConfig;
import com.company.parser.DBStorage;
import com.company.parser.data.TorrentBook;
import com.company.parser.data.TorrentItem;
import com.company.parser.exception.AgentParserException;
import com.company.parser.local.model.Author;
import com.company.parser.local.model.Book;
import com.company.parser.local.model.Genre;
import com.company.parser.utils.ParseUtils;

/**
 * @author chevi
 *
 */
public class FlibustaParser {

	private static final Logger log = LoggerFactory.getLogger(FlibustaParser.class);

	private final String LOG_FILE_NAME = "/logs/flibusta_import_result.txt";

	private DBStorage dbs = new DBStorage();

	private String filePath;
	private String tmpDir;

	private LocalCacheStorage cacheStorage;
	private CacheConnect cacheConnect;

	private ImportTorrentFactory torrentFactory = new ImportTorrentFactory();

	private FlibustaDownloader downloader;
	private FlibustaStatistic statistic;

	// Данные из базы Флибусты
	private Map<Integer, Book> allBookDescriptions;
	private MultiValueHashMap<Integer, Book> seriseIdToBooksId;
	private List<String> catalogGenres;

	/**
	 * список, хранящий ID серий, которые нужно объеденяьт связями типа Like
	 */
	private List<Integer> seriesForLike = new ArrayList<Integer>();
		
	// паттерн для поиска "лишних" сииволов
	private Pattern pattern = Pattern.compile("([^0-9A-Za-zА-Яа-я-—\\s\\(\\)\\,]+)");
	
	// Длинна имени файла без учета id(с пробелом)  и точки с расширением
	private static final int FILE_LENGTH = 137;
	
	private int countOfWait = 0;
	
	// сколько нужно заимпортировать книг 
	private int limit;
	// количество заимпортированных книг
	private int successAddedBook;
	
	private int sleepTime = 0;

	public FlibustaParser(String filePath, Integer limit, String sleepTime) {
		super();		
		this.filePath = filePath + "/";
		
		if (limit == null) {
			this.limit = 0;
		} else {
			this.limit = limit;
		}
		
		if (sleepTime != null) {
			try {
				this.sleepTime = Integer.valueOf(sleepTime);
			} catch (NumberFormatException e) {
				log.warn("Incorrect value for sleep time, must be a number. sleepTime = 0");
			}
		}
		
		tmpDir = TorrentParserConfig.getConfig().getImportTempDir();
		cacheStorage = AplicationContextHolder.getBean(LocalCacheStorage.class);
		cacheConnect = new CacheConnect();
		cacheStorage.setCacheConnect(cacheConnect);
		downloader = new FlibustaDownloader();
		statistic = new FlibustaStatistic(tmpDir + LOG_FILE_NAME);
	}

	public void start() throws AgentParserException {
		checkOfAccessibilityOfServers();
		init();
		mainCircle();
	}

	/**
	 * Основной цикл, идём по всем книгам и обрабатываем их, если книга в серии,
	 * запускаем в основном цикле внутренний цикл с книгами из серии и в
	 * процессе их обработки вяжем их связями.
	 * 
	 * @throws AgentParserException
	 */
	public void mainCircle() throws AgentParserException {
		log.info("Begin parsing");

		Book book = null;
		RelationshipTypeEnum rte = null;
		Long anthologyIdFrom = null;
		Long anthologyIdTo = null;

		for (Iterator<Entry<Integer, Book>> iterator = allBookDescriptions.entrySet().iterator(); iterator.hasNext();) {
			book = iterator.next().getValue();		
			try {
				if (book.isProcessed()) {
					iterator.remove();
					continue;
				}
				if (book.getSeriesId() != 0) {
					if (book.getPreviousResult() == null || book.getPreviousResult().contains(ImportResultEnum.CREATE_RELATIONSHIP_FAILED)) {
						anthologyIdFrom = null;
						anthologyIdTo = null;

						if (seriesForLike.contains(book.getSeriesId())) {
							rte = RelationshipTypeEnum.LIKE;
						} else {
							rte = RelationshipTypeEnum.SEASON_SEQUENCE;
						}

						boolean first = true;
						for (Book book2 : getSortedListOfBooks(book.getSeriesId())) {
							if (book2 != null) {
								try {
									addBook(book2, true);
									if (first) {
										first = false;
										anthologyIdFrom = book2.getAnthologyId();
									} else {
										anthologyIdTo = book2.getAnthologyId();
									}

									if (anthologyIdFrom != null && anthologyIdTo != null && anthologyIdFrom != anthologyIdTo) {
										createRelationships(anthologyIdTo, anthologyIdFrom, rte);
										anthologyIdFrom = anthologyIdTo;
									}
								} catch (AgentParserException e) {
									afterExceptionWork(e);
								}
							}
						}
					}
				}

				addBook(book, false);
				iterator.remove();

			} catch (AgentParserException e) {
				afterExceptionWork(e);
				iterator.remove();
			} catch (Exception e){
				afterExceptionWork(new AgentParserException(e));
				iterator.remove();
			}
		}
		statistic.printStatistic(true);
	}

	/**
	 * Парсим книгу, добавляем её на Каталог, создаём торрент и добавляем на
	 * Кеши
	 * 
	 * @param book
	 *            данные полученные из базы Флибусты в виде объекта Book
	 * @param inSeries
	 *            книга из серии? Если true - в book.anthologyId устанавливаем
	 *            значение Антологии, для последующего связывания антологий
	 * @throws AgentParserException
	 *             в эксепшн записываем данные о типе ошибки, данные лога,
	 *             результат импорта
	 */
	private void addBook(Book book, boolean inSeries) throws AgentParserException {
		StringBuilder logResult = new StringBuilder();

		try {
			logResult.append(ImportLogEnum.SOURCE_TYPE.getName() + "flibusta;");
			logResult.append(ImportLogEnum.SOURCE_ID.getName() + book.getBookId() + ";");

			if (book.isProcessed()) {
				logResult = null;
				return;
			}
			if (book.getPreviousResult() != null && book.getPreviousResult().contains(ImportResultEnum.COMPLETE)) {
				logResult.append(ImportLogEnum.RESULT.getName() + ImportResultEnum.COMPLETE.getName());
				book.setProcessed(true);
				logResult = null;
				log.info("book [id={}] complete in previous run", book.getBookId());
				return;
			}

			log.info("");
			log.info("parse book [id={}] ", book.getBookId());
			TorrentBook item = parse(book);

			item.setExternal(false);

			Long shareId = isTorrentExist(item);
			if (book.getPreviousResult() == null || book.getPreviousResult().contains(ImportResultEnum.CREATE_SHARE_FAILED)) {
				if (shareId == null) {
					shareId = dbs.sendToCatalog(item);
				} else {
					String nameById = dbs.getNameById(shareId);
					log.info("Torrent with infohash {} exist, shareId = {}, name = {}", item.getInfohash(), shareId, nameById);
				}
			}

			item.setShareId(shareId);

			if (book.getPreviousResult() == null || book.getPreviousResult().contains(ImportResultEnum.ADD_TO_CACHE_FAILED)) {
				// Добавляем раздачу на Кеш
				addToCashe(item, book.getFullFileName());
			}

			logResult.append(ImportLogEnum.SHARE_ID.getName() + shareId + ";");
			logResult.append(ImportLogEnum.RESULT.getName() + ImportResultEnum.COMPLETE.getName() + (book.getParseResult() == null ? "" : book.getParseResult()));
			book.setProcessed(true);

			if (inSeries) {
				book.setAnthologyId(dbs.getAnthologyIdByShareId(item.getShareId()));
			}
		} catch (AgentParserException e) {
			book.setProcessed(true);
			throw new AgentParserException(e.getMessage(), e.getImportResult(), e.getCause(), logResult.toString() + (book.getParseResult() == null ? "" : book.getParseResult()));
		} catch (NullPointerException e){
			book.setProcessed(true);
			throw new AgentParserException(e.getMessage(), ImportResultEnum.PARSING_ERROR, e, logResult.toString() + (book.getParseResult() == null ? "" : book.getParseResult()));
		} catch (Exception e){
			book.setProcessed(true);
			throw new AgentParserException(e.getMessage(), ImportResultEnum.PARSING_ERROR, e, logResult.toString() + (book.getParseResult() == null ? "" : book.getParseResult()));
		}
		statistic.incrementFileCount(logResult.toString());
		successAddedBook++;
		if(successAddedBook >= limit){
			statistic.printStatistic(true);
			System.exit(0);
		}
		sleep();
	}
	
	private void sleep(){
		try {
			TimeUnit.MILLISECONDS.sleep(sleepTime);
		} catch (InterruptedException e) {			
		}
	}

	/**
	 * Ошибка во время парсинга/добавления на каталог/добавления на Кэш/создание
	 * связи. Данные по ошибке вывести в лог, в статистику и в файл результата
	 * 
	 * @param e
	 */
	private void afterExceptionWork(AgentParserException e) {
		StringBuilder logResult = new StringBuilder();
		log.error(e.getMessage());
		logResult.append(e.getLogResult());
		logResult.append(ImportLogEnum.RESULT.getName() + e.getImportResult().getName());
		logResult.append(ImportLogEnum.ERROR_MESSAGE.getName() + e.getMessage());
		statistic.addReasonOfError(e.getImportResult().getName());
		statistic.incrementFileCount(logResult.toString());
		logResult.setLength(0);
		logResult = null;
	}

	/**
	 * Проверяем, доступен ли Кеш сервер, если нет то ждём
	 * 
	 * @return
	 */
	private boolean checkOfAccessibilityOfServers() {
		if (countOfWait == 0) {
			log.info("check of accessibility of Cache");
		} else if (countOfWait >= 1 && countOfWait < 15) {
			log.info("Not found Life Cache! Wait 1 min.");
		} else if (countOfWait > 15) {
			log.error("Life Cache server not found. Stop agent");
			System.exit(1);
		}
		boolean serversIsLife = true;

		if (cacheConnect.getSession().isEmpty()) {
			serversIsLife = false;
		}

		while (!serversIsLife) {
			try {
				TimeUnit.MINUTES.sleep(1L);
				countOfWait++;
				serversIsLife = checkOfAccessibilityOfServers();
			} catch (InterruptedException e) {
			}
		}
		return serversIsLife;
	}

	/**
	 * Ищем базу Флибусты. Из базы получаем данные для парсинга
	 * */
	private void init() throws AgentParserException {
		log.info("Init");

		// Получаем путь к базе Флибусты
		log.info("search path to Flibusta DB");
		String pathToDB = null;
		List<String> files = downloader.getFiles(filePath);
		for (String string : files) {
			if (string.endsWith(".hlc2")) {
				pathToDB = string;
				break;
			}
		}
		if (pathToDB == null) {
			throw new AgentParserException("Can't find path to Flibusta_ALL_local.hlc2 file. The file shall be in a directory with Flibusta zip arhivies.");
		}
		log.info("found Flibusta DB: {}", pathToDB);
		FlibustaDBConnect.setURL(pathToDB);

		// Из базы Флибусты получаем данные для парсинга\
		log.info("get data from Flibusta DB...");
		allBookDescriptions = FlibustaDBConnect.getAllBookDescriptions();
		FlibustaDBConnect.getAuthors(allBookDescriptions);
		FlibustaDBConnect.getGenres(allBookDescriptions);
		seriseIdToBooksId = FlibustaDBConnect.getSeries(allBookDescriptions);		
		catalogGenres = FlibustaDBConnect.getCatalogGenres();

		downloader.getPreviousImportResult(allBookDescriptions, tmpDir + LOG_FILE_NAME);

		initSeriesForLike();

		if(limit == 0){
			limit = allBookDescriptions.size();
		}
		
		log.info("Number of books which need to be processed: {} ", limit);
		log.info("Waiting time between processing of each book: {} ", sleepTime);
	}

	/**
	 * Заполняем список, хранящий ID серий, которые нужно объеденяьт связями
	 * типа Like
	 */
	private void initSeriesForLike() {
		seriesForLike.add(98);
		seriesForLike.add(191);
		seriesForLike.add(31);
		seriesForLike.add(125);
		seriesForLike.add(310);
		seriesForLike.add(1267);
		seriesForLike.add(303);
		seriesForLike.add(396);
		seriesForLike.add(74);
		seriesForLike.add(1411);
		seriesForLike.add(491);
		seriesForLike.add(3038);
		seriesForLike.add(142);
		seriesForLike.add(143);
		seriesForLike.add(540);
		seriesForLike.add(294);
		seriesForLike.add(520);
		seriesForLike.add(678);
		seriesForLike.add(472);
		seriesForLike.add(995);
		seriesForLike.add(2430);
		seriesForLike.add(746);
	}

	/**
	 * Получить файл из архива, распарсить данные, создать файл для Кэша
	 * */
	private TorrentBook parse(Book book) throws AgentParserException {

		checkOnIgnore(book);

		TorrentBook item = new TorrentBook();
		fillMainInfo(item, book);
		fillAuthors(item, book);
		fillGenres(item, book, catalogGenres);

		// Достаём из архива файл во временную директорию, а путь к полученному
		// новому файлу записываем в поле book		
		book.setFullFileName(downloader.createFileFromZip(filePath + book.getFolder(), book.getFileName() + book.getFormat(), tmpDir + "/unzip_files/" + book.getFolder(), createNewFileName(book)));

		if (book.getFormat().equals(".fb2")) {
			book.setParseResult(parseFb2(item, downloader.getFB2FileContent(book.getFullFileName(), book.getBookSize())));
		}

		try {
			setTorrent(item, book.getFullFileName());
		} catch (Exception e) {
			throw new AgentParserException(e.getMessage(), ImportResultEnum.PARSING_ERROR, e);
		}

		item.setContentStatus(ContentStatusEnum.APPROVED);
		item.setQuality("E-Book");
		return item;
	}

	/**
	 * Игнорировать книги по определенным признакам при импорте. Если книга
	 * попадает под определённое ограничение, кидаем исключение с типом
	 * результата ImportResultEnum.PARSING_ERROR и соответствующим описанием
	 */
	private void checkOnIgnore(Book book) throws AgentParserException {
		if (!book.getLang().equals("ru")) {
			throw new AgentParserException("Skip book - not RU book", ImportResultEnum.PARSING_ERROR);
		}
		if (book.getGenres() != null) {
			for (Genre genre : book.getGenres()) {
				if (genre.getGenre().equals("Неотсортированное")) {
					throw new AgentParserException("Skip book - genre: Неотсортированное!", ImportResultEnum.PARSING_ERROR);
				}
				if (genre.getGenre().equals("Журналы")) {
					throw new AgentParserException("Skip book - genre: Журналы!", ImportResultEnum.PARSING_ERROR);
				}
				if (genre.getGenre().equals("Порно")) {
					throw new AgentParserException("Skip book - genre: Порно!", ImportResultEnum.PARSING_ERROR);
				}
			}
		}
	}
		
	private String createNewFileName(Book book) {
		StringBuilder sb = new StringBuilder();
		if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
			sb.append("(").append(book.getAuthors().get(0).getLastName()).append(" ");
			if (StringUtils.isNotBlank(book.getAuthors().get(0).getFirstName())) {
				if (book.getAuthors().get(0).getFirstName().length() > 1) {
					sb.append(book.getAuthors().get(0).getFirstName().substring(0, 1));
				} else {
					sb.append(book.getAuthors().get(0).getFirstName());
				}
			}
			if (StringUtils.isNotBlank(book.getAuthors().get(0).getMiddleName())) {
				sb.append(" ");
				if (book.getAuthors().get(0).getMiddleName().length() > 1) {
					sb.append(book.getAuthors().get(0).getMiddleName().substring(0, 1));
				} else {
					sb.append(book.getAuthors().get(0).getMiddleName());
				}
			}
			sb.append(") ");
		} else{
			sb.append("(Без автора) ");
		}
		String title = book.getTitle();

		Matcher matcher = pattern.matcher(title);
		while(matcher.find()){
			String group = matcher.group(1);
			title = title.replace(group, "");
			matcher = pattern.matcher(title);
		}	
		
		sb.append(title);
		if(sb.length() > FILE_LENGTH){
			String substring = sb.substring(0, FILE_LENGTH);
			sb = new StringBuilder();
			sb.append(substring);
		}
		sb.append(" [").append(book.getBookId()).append("]");
		return sb.toString();
	}

	private void fillMainInfo(TorrentItem item, Book book) throws AgentParserException {
		TorrentBook torrent = (TorrentBook) item;
		torrent.setSourceId("20");

		// id
		String id = String.valueOf(book.getBookId());
		if (StringUtils.isNotBlank(id)) {
			torrent.setId(id);
		} else {
			throw new AgentParserException("Can't parse Book.id", ImportResultEnum.PARSING_ERROR);
		}

		// Name
		String title = book.getTitle();
		if (StringUtils.isNotBlank(title)) {
			torrent.setName(ParseUtils.toClearTextOfPunctuationMarks(title));
		} else {
			throw new AgentParserException("Can't parse Book.title", ImportResultEnum.PARSING_ERROR);
		}

		// Year
//		String date = book.getDate();
//		if (StringUtils.isNotBlank(date)) {
//			torrent.setYearAndParse(date);
//		} else {
//			throw new FutParsException("Can't parse Book.date", ImportResultEnum.PARSING_ERROR);
//		}

		// Format
		String format = book.getFormat();
		if (StringUtils.isNotBlank(format)) {
			try {
				format = format.substring(1, format.length()).toUpperCase();
			} catch (IndexOutOfBoundsException e) {
				throw new AgentParserException("Can't parse Book.format", ImportResultEnum.PARSING_ERROR, e);
			}
			torrent.setFormat(format);
		} else {
			throw new AgentParserException("Can't parse Book.format", ImportResultEnum.PARSING_ERROR);
		}

		// Series
		if (book.getSeriesName() != null) {
//			book.setSeriesName(seriesMap.get(book.getSeriesId()));
			torrent.setCycle(book.getSeriesName());
			torrent.setSeriesIndex(String.valueOf(book.getSeqNumber()));
		}

	}

	private void fillAuthors(TorrentItem item, Book book) throws AgentParserException {
		TorrentBook torrent = (TorrentBook) item;
		StringBuilder sbAuthors = new StringBuilder();
		for (Author author : book.getAuthors()) {
			sbAuthors.append(author.getLastName());
			sbAuthors.append(" ");
			sbAuthors.append(author.getFirstName());
			sbAuthors.append(" ");
			sbAuthors.append(author.getMiddleName());
			sbAuthors.append(", ");
		}
		String autrs = sbAuthors.toString();
		if (StringUtils.isNotBlank(autrs)) {
			torrent.setAuthorRus(autrs.substring(0, autrs.length() - 2));
		} else {
			throw new AgentParserException("Can't parse Book.authors", ImportResultEnum.PARSING_ERROR);
		}
	}

	/**
	 * @param item
	 *            итем, который заполняется распаршенными данными
	 * @param genres
	 *            список жанров из базы Флибусты
	 * @param catalogGenres
	 *            список всех жанров Футурона
	 * @throws AgentParserException
	 */
	private void fillGenres(TorrentItem item, Book book, List<String> catalogGenres) throws AgentParserException {
		TorrentBook torrent = (TorrentBook) item;
		StringBuilder sbGenre = new StringBuilder();
		if (book.getGenres() != null) {
			for (Genre genre : book.getGenres()) {
				if (catalogGenres.contains(genre.getGenre())) {
					sbGenre.append(genre.getGenre());
					sbGenre.append(", ");
				}
				// sbGenre.append(DataBooksGanres.parse(genre.getFb2Code()));
				// sbGenre.append(", ");
			}
		}
		String gnrs = sbGenre.toString();
		if (StringUtils.isNotBlank(gnrs)) {
			torrent.setGenre(gnrs.substring(0, gnrs.length() - 2));
		} else {
			String notFoundGenres = "";
			if (book.getGenres() != null) {
				for (Genre genre : book.getGenres()) {
					notFoundGenres = genre.getGenre() + ", " + notFoundGenres;
				}
			}
			throw new AgentParserException("Can't parse Book.genres. Flibusta genres: " + notFoundGenres, ImportResultEnum.PARSING_ERROR);
		}
	}

	/**
	 * Парсим книгу формата FB2
	 * 
	 * @param item
	 *            итем, который заполняется распаршенными данными
	 * @param contentFB2
	 *            текстовое представление книги формата FB2
	 */
	private String parseFb2(TorrentItem item, String contentFB2) {
		StringBuilder inLog = new StringBuilder();
		Document doc = Jsoup.parse(contentFB2);

		TorrentBook torrent = (TorrentBook) item;
		Elements description = doc.select("description");

		// Title
		// torrent.setName(getTextFromElement(description, "book-title"));

		// Ganre
		// String genre = getTextFromElement(description, "genre");
		// if (genre != null) {
		// String parsedGanre = DataBooksGanres.parse(genre);
		// if (parsedGanre != null && !parsedGanre.equals("")) {
		// torrent.setGenre(parsedGanre);
		// statistic.addGenre(parsedGanre);
		// } else {
		// // genreYetNotAddedToTheList.add(genre);
		// }
		// }

		// Translation Author
		Elements translator = description.select("translator");
		if (!translator.isEmpty()) {

			StringBuilder sb = new StringBuilder();
			String textFromElement = getTextFromElement(translator, "nickname");
			if (StringUtils.isNotBlank(textFromElement)) {
				sb.append(textFromElement);
				sb.append(" ");
			}
			textFromElement = getTextFromElement(translator, "last-name");
			if (StringUtils.isNotBlank(textFromElement)) {
				sb.append(textFromElement);
				sb.append(" ");
			}
			textFromElement = getTextFromElement(translator, "middle-name");
			if (StringUtils.isNotBlank(textFromElement)) {
				sb.append(textFromElement);
				sb.append(" ");
			}
			textFromElement = getTextFromElement(translator, "first-name");
			if (StringUtils.isNotBlank(textFromElement)) {
				sb.append(textFromElement);
			}
			if (StringUtils.isNotBlank(sb.toString())) {
				torrent.setTranslationAuthor(sb.toString().trim());
			}
			sb = null;
		}

		// Publisher
		torrent.setPublisher(getTextFromElement(description, "publisher"));

		// Format
		// torrent.setFormat("FB2");

		// Year
		String year = getTextFromElement(description, "date");
		if (year != null) {
			torrent.setYear(year);
		}
		year = getTextFromElement(description, "title-info year");
		if (year != null) {
			torrent.setYear(year);
		} else {
			year = getTextFromElement(description, "publish-info year");
			if (year != null) {
				torrent.setYear(year);
			}
		}	

		// Description
		String desc = getTextFromElement(description, "annotation");
		if(StringUtils.isNotBlank(desc)){
			torrent.setDescription(getTextFromElement(description, "annotation"));			
		} else{
			inLog.append(" can't get description.");
		}

		// Cover
		Elements body = doc.select("body");
		String textFromElement = getTextFromElement(body, "binary");
		if (textFromElement != null && !textFromElement.equals("binary") && textFromElement.length() > 100) {
			try {
				torrent.setCover(Base64Decoder.decode(textFromElement.getBytes()));
			} catch (ArrayIndexOutOfBoundsException e) {
				log.warn("can't get cover");
				inLog.append(" can't get cover. ");
			}
			torrent.setMainPicture("cover.jpg");

		} else {
			inLog.append(" can't get cover. ");
		}
		textFromElement = null;
		doc = null;
		description = null;
		contentFB2 = null;
		desc = null;
		return inLog.toString();
	}

	/**
	 * Получить отсортированный по номеру в серии список книг из одной серии
	 * */
	private List<Book> getSortedListOfBooks(Integer seriesId) {	
		List<Book> listOfBooksFromSeries = new ArrayList<Book>(seriseIdToBooksId.removeAll(seriesId));
		
		try {
			if (listOfBooksFromSeries.size() > 1) {
				// Сначала сортируем по id книг
				Collections.sort(listOfBooksFromSeries, new Comparator<Book>() {
					@Override
					public int compare(Book b1, Book b2) {
						if (b1 != null && b2 != null & StringUtil.isNumeric(String.valueOf(b1.getBookId())) && StringUtil.isNumeric(String.valueOf(b2.getBookId()))) {
							if (b1.getBookId() < b2.getBookId()) {
								return -1;
							} else if (b1.getBookId() == b2.getBookId()) {
								return 0;
							} else {
								return 1;
							}
						} else
							return 0;
					}
				});

				// затем сортируем по SeqNumber (если оно есть)
				Collections.sort(listOfBooksFromSeries, new Comparator<Book>() {
					@Override
					public int compare(Book b1, Book b2) {
						if (b1 != null && b2 != null & StringUtil.isNumeric(String.valueOf(b1.getSeqNumber())) && StringUtil.isNumeric(String.valueOf(b2.getSeqNumber()))) {
							if (b1.getSeqNumber() < b2.getSeqNumber()) {
								return -1;
							} else if (b1.getSeqNumber() == b2.getSeqNumber()) {
								return 0;
							} else {
								return 1;
							}
						} else
							return 0;
					}
				});
			}
		} catch (IllegalArgumentException e) {
			log.warn("Error when sort List:" + listOfBooksFromSeries);
		}
		return listOfBooksFromSeries;
	}

	/**
	 * Создать связь между двумя антологиями
	 * */
	private void createRelationships(Long anthologyIdFrom, Long anthologyIdTo, RelationshipTypeEnum rte) throws AgentParserException {
		log.info("Create relationships between anthologies: " + anthologyIdFrom + " and " + anthologyIdTo);

		DtoAgentUpdateRelations relDto = new DtoAgentUpdateRelations();
		relDto.setContentType(ContentTypeEnum.BOOKS);
		relDto.setAnthologyId(anthologyIdFrom);
		relDto.addTo(rte, anthologyIdTo);
		new CatalogConnect().updateAnthologyRelations(relDto);
	}

	private String getTextFromElement(Elements element, String tagName) {
		Elements select = element.select(tagName);
		if (select.size() != 0) {
			if (tagName.equals("binary")) {
				tagName = select.get(select.size() - 1).text().trim();
			} else {
				tagName = select.get(0).text().trim();
			}
		} else {
			tagName = null;
		}
		return ParseUtils.toClearTextOfPunctuationMarks(tagName);
	}

	private void setTorrent(TorrentItem item, String filePath) throws IOException, NoSuchAlgorithmException, InterruptedException, TorrentClientException, URISyntaxException {

		File newFile = new File(filePath);

		String absolutePath = newFile.getAbsolutePath();
		absolutePath = absolutePath.replace(newFile.getName(), "");

		ITorrent torrent = torrentFactory.createTorrent(newFile, new File(absolutePath));

		item.setTorrent(torrent.getEncoded());
		item.setInfohash(torrent.getHexInfoHash());
		torrent = null;
	}

	private void deleteFile(String fileName) {
		File f = new File(fileName);
		if (f.exists() == true) {
			try {
				Files.delete(f.toPath());
			} catch (IOException e) {
				log.warn("Error when try delete file: " + fileName);
			}
		}
	}

	private void addToCashe(TorrentItem item, String fileName) throws AgentParserException {
		log.debug("Try to adding book [id={}, share.id={}] to cache", item.getId(), item.getShareId());
		File newFile = new File(fileName);
		// создать сидам ихние CachedEntry на ихние сервер ид со статусом СИД
		List<CachedEntry> list = cacheStorage.createEntry(item.getShareId(), item.getInfohash(), false);

		if (!list.isEmpty()) {
			/*
			 * переместит этот зашифрованный файл на полку кешей, чтобы сиды
			 * могли их прочитать
			 */
			try {
				cacheStorage.moveFileForSeed(list.get(0), newFile);
			} catch (Exception e) {
				throw new AgentParserException(e.getMessage(), ImportResultEnum.ADD_TO_CACHE_FAILED, e);
			}

			// Дернуть сидов по кластеру:
			for (CachedEntry cachedEntry : list) {
				cacheConnect.processCachedEntry(cacheStorage, cachedEntry);
			}
		} else {
			throw new AgentParserException("File " + fileName + " not added to CACHE_SEED", ImportResultEnum.ADD_TO_CACHE_FAILED);
		}
	}

	/**
	 * По инфохешу определяем, есть ли в базе данный торрент
	 * 
	 * @param item
	 *            распаршенный торрент
	 * @return id шары
	 * @throws AgentParserException
	 */
	private Long isTorrentExist(TorrentItem item) throws AgentParserException {
		Long res = dbs.getShareIdByInfohash(item.getInfohash());
		if (res != null) {
			// log.info("Found content in DB: [" + res.toString() + "]");
		}
		return res;
	}
}
