package org.kmouille.blog.extractor;

import java.io.File;

import org.kmouille.blog.domain.Blog;
import org.kmouille.blog.extractor.exception.BlogException;

public interface BlogArticleExtractor {

	Blog extractBlog(File xmlExportFile) throws BlogException;

}