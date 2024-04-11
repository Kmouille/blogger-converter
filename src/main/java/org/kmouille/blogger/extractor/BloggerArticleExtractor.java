package org.kmouille.blogger.extractor;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.kmouille.blogger.domain.Article;
import org.kmouille.blogger.domain.Blog;
import org.kmouille.blogger.domain.GeoLoc;
import org.tinylog.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class BloggerArticleExtractor {

	public Blog extractBlog(File bloggerXmlFile) throws SAXException, IOException, ParserConfigurationException {
		var javaxDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bloggerXmlFile);
		var title = javaxDocument.getElementsByTagName("title").item(0).getTextContent().trim();
		return new Blog(title, extractArticles(javaxDocument));
	}

	public List<Article> extractArticles(Document javaxDoc) {
		List<Article> articles = new ArrayList<>();
		var nodeList = javaxDoc.getElementsByTagName("entry");
		for (int nodeIndex = 0; nodeIndex < nodeList.getLength(); nodeIndex++) {
			var element = (Element) nodeList.item(nodeIndex);
			if (isArticle(element)) {

				var title = articleTitle(element);
				var publishedDateString = element.getElementsByTagName("published").item(0).getTextContent().trim();
				var publishedDate = LocalDateTime.parse(publishedDateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
				var rawContent = element.getElementsByTagName("content").item(0).getTextContent().trim();
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
		return element.getElementsByTagName("title").item(0).getTextContent().trim();
	}

	GeoLoc extractGeoLoc(Element element) {
		GeoLoc geoLoc = null;
		if (element.getElementsByTagName("georss:point").item(0) != null) {
			var geoLocString = element.getElementsByTagName("georss:point").item(0).getTextContent().trim();
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
			var categoryScheme = category.getAttribute("scheme");
			var categoryTerm = category.getAttribute("term");
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
		for (var category : categories(element)) {
			var categoryScheme = category.getAttribute("scheme");
			var categoryTerm = category.getAttribute("term");
			if (categoryScheme.contains("#kind")
					&& categoryTerm.contains("#post")) {
				return true;
			}
		}
		return false;
	}

	List<Element> categories(Element element) {
		var categoriesNodeList = element.getElementsByTagName("category");
		List<Element> categories = new ArrayList<>();
		for (int categoryIndex = 0; categoryIndex < categoriesNodeList.getLength(); categoryIndex++) {
			categories.add((Element) categoriesNodeList.item(categoryIndex));
		}
		return categories;
	}

}
