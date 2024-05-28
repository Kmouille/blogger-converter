package org.kmouille.blog.domain;

import java.util.List;

public record Blog(
		String title,
		List<Article> articles) {
}