package org.kmouille.blog.extractor;

import org.kmouille.blog.extractor.blogger.BloggerArticleExtractor;
import org.kmouille.blog.extractor.wordpress.WordpressArticleExtractor;

public class BlogArticleExtractorFactory {

	public static BlogArticleExtractor createExtractor(BlogPlatform blogPlatform) {
		return switch (blogPlatform) {
			case BLOGGER -> new BloggerArticleExtractor();
			case WORPDRESS -> new WordpressArticleExtractor();
			default -> throw new IllegalArgumentException("Unexpected value: " + blogPlatform);
		};
	}

}
