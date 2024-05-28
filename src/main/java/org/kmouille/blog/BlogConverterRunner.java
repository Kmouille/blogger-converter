package org.kmouille.blog;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.xml.parsers.ParserConfigurationException;

import org.kmouille.blog.extractor.BlogArticleExtractorFactory;
import org.kmouille.blog.extractor.BlogPlatform;
import org.xml.sax.SAXException;

public class BlogConverterRunner {

	private static final String xmlFile = "4lamaison.WordPress.2024-05-28.xml";
	// private static final String xmlFile = "4lamaison.WordPress.2024-05-28-small.xml";

	private static final File xmlFolder = new File("C:\\perso\\dev\\BLOG_Book");
	private static final File bookFolder = new File("C:\\perso\\dev\\BLOG_Book\\Book_4maison");

	public static void main(String[] args)
			throws SAXException, IOException, ParserConfigurationException, URISyntaxException {

		var blog = BlogArticleExtractorFactory.createExtractor(BlogPlatform.WORPDRESS)
				.extractBlog(new File(xmlFolder, xmlFile));

		var bloggerConverter = new BlogConverter();
		bloggerConverter.convert(blog, bookFolder, true);

	}

}
