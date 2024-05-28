package org.kmouille.blog.extractor.blogger;

import java.util.List;

import org.jsoup.Jsoup;
import org.kmouille.blog.domain.ArticleContent;

public class BloggerArticleContentExtractor {

	protected ArticleContent extractContent(String rawContent, String title) {
		var html = Jsoup.parseBodyFragment(rawContent);
		html.title(title);
		html.body().prependElement("h1").text(title);
		// TODO Complete...
		return new ArticleContent(html.html(), List.of());
	}

}
