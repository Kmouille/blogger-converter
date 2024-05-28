package org.kmouille.blog.domain;

import java.util.List;

// TODO Decide a format... beware of links, images, local or remote... etc.
public record ArticleContent(
		String content,
		List<String> imageUrls) {
}
