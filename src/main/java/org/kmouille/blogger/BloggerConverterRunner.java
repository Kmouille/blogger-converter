package org.kmouille.blogger;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class BloggerConverterRunner {

	private static final String xmlFile = "blog-04-09-2024.xml";
	private static final String xmlFileSmall = "blog-03-12-2024_01_small.xml";

	private static final String xmlUnJourFile = "blog-un-jour-04-09-2024.xml";

	private static final File xmlFolder = new File("C:\\perso\\dev\\BLOG_Book");
	private static final File bookFolder = new File("C:\\perso\\dev\\BLOG_Book\\Book");
	private static final File bookFolderOffline = new File("C:\\perso\\dev\\BLOG_Book\\BookOffline");
	private static final File bookFolderSmall = new File("C:\\perso\\dev\\BLOG_Book\\Book_small");
	private static final File bookUnJourFolder = new File("C:\\perso\\dev\\BLOG_Book\\BookUnjour");
	private static final File bookUnJourFolderOffline = new File("C:\\perso\\dev\\BLOG_Book\\BookUnjourOffline");

	public static void main(String[] args)
			throws SAXException, IOException, ParserConfigurationException, URISyntaxException {
		var bloggerConverter = new BloggerConverter();
		bloggerConverter.convert(new File(xmlFolder, xmlUnJourFile), bookUnJourFolderOffline);
		bloggerConverter.convert(new File(xmlFolder, xmlUnJourFile), bookUnJourFolder, true);
		bloggerConverter.convert(new File(xmlFolder, xmlFile), bookFolder, true);
		bloggerConverter.convert(new File(xmlFolder, xmlFile), bookFolderOffline);
		bloggerConverter.convert(new File(xmlFolder, xmlFileSmall), bookFolderSmall);
	}

}
