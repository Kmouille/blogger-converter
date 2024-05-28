package org.kmouille.blog.extractor.wordpress;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.kmouille.blog.domain.Article;
import org.kmouille.blog.domain.Blog;
import org.kmouille.blog.domain.GeoLoc;
import org.kmouille.blog.extractor.BlogArticleExtractor;
import org.kmouille.blog.extractor.exception.BlogException;
import org.tinylog.Logger;

public class WordpressArticleExtractor implements BlogArticleExtractor {

	static Pattern blankLinePattern = Pattern
			.compile("(^\\s*$\\r?\\n)+", Pattern.MULTILINE);

	public Blog extractBlog(File xmlExportFile) throws BlogException {
		try {
			var jsoupDoc = Jsoup.parse(xmlExportFile);
			var title = jsoupDoc.select("title").text();
			return new Blog(title, extractArticles(jsoupDoc));
		} catch (Exception e) {
			throw new BlogException(e);
		}
	}

	public List<Article> extractArticles(Document jsoupDoc) {
		List<Article> articles = new ArrayList<>();

		var elements = jsoupDoc.select("item");
		for (int nodeIndex = 0; nodeIndex < elements.size(); nodeIndex++) {
			var element = elements.get(nodeIndex);
			if (isArticle(element)) {
				// CDATA is supported depending on namespace?
				// var title = element.select("wp\\:post_name").text();
				var title = decodeCdata(element.select("title").text());
				var publishedDateString = element.select("wp\\:post_date").text().trim();
				var publishedDate = LocalDateTime.parse(publishedDateString,
						DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

				var rawContent = blankLinePattern
						.matcher(element.select("content\\:encoded").text())
						.replaceAll("")
						.replace("\n", "<br>\n");
				var geoLoc = extractGeoLoc(element);
				var tags = extractTags(element);

				var agnosticContent = new WordpressArticleContentExtractor().extractContent(rawContent, title);

				articles.add(new Article(title, publishedDate, rawContent, agnosticContent, tags, geoLoc));

				Logger.debug("Found article #{}: {} - {}",
						nodeIndex, publishedDate.format(DateTimeFormatter.ISO_DATE), title);

			} else {
				Logger.trace("[UNKNOWN_TYPE] Not an article #{}", nodeIndex);
			}
		}
		return articles;
	}

	private static String decodeCdata(String text) {
		if (text.startsWith("<![CDATA[") && text.endsWith("]]>")) {
			return text.substring(9, text.length() - 3);
		}
		return text;
	}

	GeoLoc extractGeoLoc(Element element) {
		return null;
	}

	List<String> extractTags(Element element) {
		return List.of();
	}

	boolean isArticle(Element element) {
		var postType = element.select("wp\\:post_type").text().trim();
		return "post".equalsIgnoreCase(postType);
	}

	List<Element> categories(Element element) {
		return List.of();
	}

}
