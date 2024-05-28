package org.kmouille.blog.extractor.blogger;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.kmouille.blog.domain.Article;
import org.kmouille.blog.domain.Blog;
import org.kmouille.blog.domain.GeoLoc;
import org.kmouille.blog.extractor.BlogArticleExtractor;
import org.kmouille.blog.extractor.exception.BlogException;
import org.tinylog.Logger;

public class BloggerArticleExtractor implements BlogArticleExtractor {

	@Override
	public Blog extractBlog(File xmlExportFile) throws BlogException {
		try {
			var jsoupDoc = Jsoup.parse(xmlExportFile);
			var title = jsoupDoc.select("title").first().text();
			return new Blog(title, extractArticles(jsoupDoc));
		} catch (Exception e) {
			throw new BlogException(e);
		}
	}

	public List<Article> extractArticles(Document jsoupDoc) {
		List<Article> articles = new ArrayList<>();
		var nodeList = jsoupDoc.select("entry");
		for (int nodeIndex = 0; nodeIndex < nodeList.size(); nodeIndex++) {
			var element = nodeList.get(nodeIndex);
			if (isArticle(element)) {

				var title = articleTitle(element);
				var publishedDateString = element.select("published").text();
				var publishedDate = LocalDateTime.parse(publishedDateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
				var rawContent = element.select("content").text();
				var geoLoc = extractGeoLoc(element);
				var tags = extractTags(element);

				var agnosticContent = new BloggerArticleContentExtractor().extractContent(rawContent, title);

				articles.add(new Article(title, publishedDate, rawContent, agnosticContent, tags, geoLoc));

				Logger.debug("Found article #{}: {} - {}",
						nodeIndex, publishedDate.format(DateTimeFormatter.ISO_DATE), title);

			} else {
				Logger.trace("[UNKNOWN_TYPE] Not an article #{}", nodeIndex);
			}
		}
		return articles;
	}

	String articleTitle(Element element) {
		return element.select("title").text();
	}

	GeoLoc extractGeoLoc(Element element) {
		GeoLoc geoLoc = null;
		if (!element.select("georss\\:point").isEmpty()) {
			var geoLocString = element.select("georss\\:point").text();
			var geoLocStringArray = geoLocString.split(" ");
			var lat = Double.parseDouble(geoLocStringArray[0]);
			var lon = Double.parseDouble(geoLocStringArray[1]);
			geoLoc = new GeoLoc(lat, lon);
		} else {
			Logger.warn("[NO GEOLOC] {}", articleTitle(element));
		}
		return geoLoc;
	}

	List<String> extractTags(Element element) {
		List<String> tags = new ArrayList<>();
		for (var category : categories(element)) {
			var categoryScheme = category.attr("scheme");
			var categoryTerm = category.attr("term");
			if (categoryScheme.contains("http://www.blogger.com/atom/ns#")) {
				tags.add(categoryTerm);
			}
		}
		if (tags.isEmpty()) {
			Logger.debug("[NO TAG] {}", articleTitle(element));
		}
		return tags;
	}

	boolean isArticle(Element element) {
		for (var category : element.select("category")) {
			var categoryScheme = category.attr("scheme");
			var categoryTerm = category.attr("term");
			if (categoryScheme.contains("#kind")
					&& categoryTerm.contains("#post")) {
				return true;
			}
		}
		return false;
	}

	List<Element> categories(Element element) {
		return element.select("category");
	}

}
