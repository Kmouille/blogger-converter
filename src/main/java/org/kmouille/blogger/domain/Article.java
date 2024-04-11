package org.kmouille.blogger.domain;

import java.time.LocalDateTime;
import java.util.List;

public record Article(
		String title,
		LocalDateTime publishedDate,
		// TODO remove this... unless we want to keep HTML as THE format
		String rawContent,
		ArticleContent agnosticContent,
		List<String> tags,
		GeoLoc location) {
}
