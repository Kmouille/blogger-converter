package org.kmouille.blogger;

import static org.apache.commons.lang3.StringUtils.abbreviateMiddle;
import static org.apache.commons.lang3.StringUtils.containsAny;
import static org.apache.commons.lang3.StringUtils.endsWithAny;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.stripAccents;
import static org.apache.commons.lang3.StringUtils.substring;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.apache.commons.lang3.StringUtils.substringBeforeLast;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.apache.commons.text.StringEscapeUtils.escapeEcmaScript;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class BloggerConverter {

	/** Abbreviate filename max length for logging purpose. */
	private int abbreviateMaxLength = 30;

	/** Filename max length. Sometimes google or blogger API uses very long encoded names. */
	private int filenameMaxLength = 20;

	private boolean cleanupUnusedFiles = false;

	void convert(File bloggerXmlFile, File bookDestinationFolder)
			throws SAXException, IOException, ParserConfigurationException, URISyntaxException {
		convert(bloggerXmlFile, bookDestinationFolder, false);
	}

	void convert(File bloggerXmlFile, File bookDestinationFolder, boolean downloadImages)
			throws SAXException, IOException, ParserConfigurationException, URISyntaxException {

		var javaxDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bloggerXmlFile);

		var articlesByYear = extractBlogArticles(javaxDocument);
		var websiteTitle = extractBlogTitle(javaxDocument);

		var welcomePageIndexHtml = new StringBuilder();

		List<String> leafletMarkers = new ArrayList<String>();

		// Write 1 HTML files for each article
		for (var entry : articlesByYear.entrySet()) {
			var year = entry.getKey();
			var articles = entry.getValue();

			System.out.println("[REPORT] Writing " + articles.size() + " year " + year + " articles");

			var yearSubFolder = new File(bookDestinationFolder, year + "_articles");
			yearSubFolder.mkdirs();

			var yearIndexHtml = new StringBuilder();
			articles.sort(Comparator.comparing(Article::publishedDate));
			for (var article : articles) {
				var imageFiles = new ArrayList<File>();
				var imageUrls = new ArrayList<String>();

				var htmlFilename = article.publishedDate.format(DateTimeFormatter.ofPattern("YYYYMMdd"))
						+ "-" + sanitizeFilename(article.title());

				var resourceFolderName = FilenameUtils.getBaseName(htmlFilename);
				var resourceFolder = new File(yearSubFolder, resourceFolderName);
				resourceFolder.mkdirs();

				var linkTag = "<a href=\"" + year + "_articles/" + htmlFilename + "\" target=\"_blank\">"
						+ article.publishedDate.format(DateTimeFormatter.ofPattern("YYYY-MM-dd")) + " - "
						+ article.title() + "</a>";

				// Add link to the welcome page
				yearIndexHtml.append("<li>" + linkTag + "</li>");

				var jsoupArticleDoc = Jsoup.parse("<html>"
						+ "<head><title>" + article.title() + "</title>"
						+ "<link rel=\"stylesheet\" href=\"../styles.css\">"
						+ "</head>"
						+ "<body>"
						+ "<div class=\"page\">"
						+ "<h1>" + article.title() + "</h1>"
						+ article.content()
						+ "</div>"
						+ "</body>"
						+ "</html>");

				// TODO Download images not wrapped into a link?
				for (var img : jsoupArticleDoc.select("img")) {
					if (img.parents().select("a").isEmpty()) {
						System.out.println(" MISSING Found <img> tag without <a> parent in " + article.title()
								+ " (src: " + img.attr("src") + ")");
					}
				}

				// Select all <a> tags containing <img> tags
				var linksWithImages = jsoupArticleDoc.select("a:has(img)");
				for (var linkWithImage : linksWithImages) {
					var img = linkWithImage.selectFirst("img");

					var aHrefValue = linkWithImage.attr("href");
					var imgSrcValue = img.attr("src");

					// Priority to <a> href, <img> if link is broken (might happen during edition)
					String imageUrl;
					if (substringAfterLast(aHrefValue, "/").equalsIgnoreCase("#")) {
						imageUrl = imgSrcValue;
					} else {
						imageUrl = aHrefValue;
					}

					var resourceFileName = substringAfterLast(imageUrl, "/");

					// Might have requests like:
					// -> =s320
					// -> =w240-h320
					if (containsAny(resourceFileName, "=s", "=w", "=h")) {
						var resizedName = abbreviateMiddle(resourceFileName, "...", abbreviateMaxLength);
						resourceFileName = truncate(substringBeforeLast(resourceFileName, "="), filenameMaxLength)
								+ ".jpg";
						System.out.println(" RESIZED " + resizedName
								+ " BECAME " + "./" + resourceFolderName + "/" + resourceFileName);
						// Change the URL not to ask for the resized image to download hires img
						imageUrl = substringBeforeLast(imageUrl, "=");
					} else if (!endsWithAny(resourceFileName.toLowerCase(), ".png", ".jpeg", ".jpg", ".bmp")) {
						var unsupportedName = abbreviateMiddle(resourceFileName, "...", abbreviateMaxLength);
						// QUESTION How not to suppose JPG?
						resourceFileName = substring(resourceFileName, 0, filenameMaxLength) + ".jpg";
						System.out.println(" UNSUPPORTED " + unsupportedName
								+ " BECAME " + "./" + resourceFolderName + "/" + resourceFileName);
					} else if (containsAny(resourceFileName, "=S", "=W", "=H")) {
						System.out.println(" FIX CASE SENSITIVE RESIZE PARAMS");
					}

					resourceFileName = decodeUrlFormattedName(resourceFileName);

					// TODO Add parameters to download image or just keep (update) URL
					if (downloadImages) {
						imageFiles.add(downloadImage(resourceFolder, resourceFileName, imageUrl));
						linkWithImage.attr("href", "./" + resourceFolderName + "/" + resourceFileName);
						img.attr("src", "./" + resourceFolderName + "/" + resourceFileName);
					} else {
						imageUrls.add(imageUrl);
						linkWithImage.attr("href", imageUrl);
						img.attr("src", imageUrl);
					}

					linkWithImage.attr("target", "_blank");

					// TODO Add parameters to resize images or not
					img.attr("width", "480");
					img.removeAttr("height");
				}
				trimTextNodes(jsoupArticleDoc.root());
				try (var writer = new FileWriter(new File(yearSubFolder, htmlFilename))) {
					writer.write(jsoupArticleDoc.toString());
				}

				// clean directory ONLY if download images is activated
				if (downloadImages) {
					for (File legacyFile : resourceFolder.listFiles()) {
						if (!imageFiles.contains(legacyFile)) {
							if (cleanupUnusedFiles) {
								System.out.println("[REPORT] REMOVE UNUSED " + legacyFile);
								legacyFile.delete();
							} else {
								System.out.println("[REPORT] SHOULD REMOVE UNUSED " + legacyFile);
							}
						}
					}
				}

				// Adding pin with link on the Leaflet Map for the article, get first image as preview
				var geoLoc = article.location();
				if (geoLoc != null) {
					var optionalImagePreview = "";
					String height = "100px";
					String imgSrc = "";
					if (downloadImages) {
						if (!imageFiles.isEmpty()) {
							var relativeImgPath = bookDestinationFolder.toPath()
									.relativize(imageFiles.get(0).toPath());
							imgSrc = relativeImgPath.toString();
						}
					} else {
						if (!imageUrls.isEmpty()) {
							imgSrc = imageUrls.get(0);
						}
					}
					if (isNotBlank(imgSrc)) {
						optionalImagePreview = "<br />"
								+ "<img src=\"" + imgSrc + "\" "
								+ "width=\"auto\" height=\"" + height + "\" />";
					}
					leafletMarkers.add(leafletMarker(geoLoc, linkTag + optionalImagePreview));
				}

			}
			welcomePageIndexHtml.append("<h1>" + year + "</h1>");
			welcomePageIndexHtml.append("<ul>" + yearIndexHtml + "</ul>");
		}

		// Create HTML for the welcome page
		// TODO Improve style
		var welcomePageHtml = new StringBuilder("<html>"
				+ "<head>"
				+ "<title>" + websiteTitle + "</title>"
				+ "<script src=\"https://unpkg.com/leaflet@latest/dist/leaflet.js\"></script>"
				+ "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@latest/dist/leaflet.css\" />"
				+ "<link rel=\"stylesheet\" href=\"styles.css\">"
				+ "</head>"
				+ "<body>"
				+ "<div id=\"map\" style=\"height: 420px;\"></div>"
				+ "<h1>Welcome to " + websiteTitle + "</h1>"
				+ welcomePageIndexHtml
				+ "</body>"
				+ leafletScript(leafletMarkers)
				+ "</html>");

		// Write the welcome page HTML
		try (var writer = new FileWriter(new File(bookDestinationFolder, "index.html"))) {
			// Parse to pretty print with JSoup
			writer.write(Jsoup.parse(welcomePageHtml.toString()).toString());
		}

		System.out.println("Conversion completed successfully.");

	}

	protected String leafletMarker(GeoLoc geoLoc, String content) {
		return "L.marker([" + geoLoc.lat + ", " + geoLoc.lon + "])"
				+ ".addTo(map).bindPopup('" + escapeEcmaScript(content) + "');";
	}

	protected String leafletScript(List<String> leafletMarkers) {
		var leafletScript = """
				<script>
				    // Initialize map
				    var map = L.map('map').setView([47, -73], 6);

				    // Add OpenStreetMap tile layer
				    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
				        attribution: '&copy; OpenStreetMap contributors'
				    }).addTo(map);
					"""
				+ leafletMarkers.stream().collect(Collectors.joining("\n"))
				+ "</script>";
		return leafletScript;
	}

	protected String decodeUrlFormattedName(final String fileName) {
		var resultFileName = fileName;
		if (resultFileName.contains("%")) {
			while (resultFileName.contains("%")) {
				resultFileName = URLDecoder.decode(resultFileName, StandardCharsets.UTF_8);
			}
			System.out.println(" ENCODED " + fileName + " BECAME " + resultFileName);
		}
		return resultFileName;
	}

	protected String extractBlogTitle(Document doc) {
		return doc.getElementsByTagName("title").item(0).getTextContent().trim();
	}

	protected Map<Integer, List<Article>> extractBlogArticles(Document doc) {
		// Using sorted map for a natural order of the years keys
		Map<Integer, List<Article>> articlesByYear = new TreeMap<>();
		var nodeList = doc.getElementsByTagName("entry");
		for (int i = 0; i < nodeList.getLength(); i++) {
			var isArticle = false;
			List<String> tags = new ArrayList<>();

			var element = (Element) nodeList.item(i);
			var categories = element.getElementsByTagName("category");
			for (int j = 0; j < categories.getLength(); j++) {
				var category = (Element) categories.item(j);
				var categoryScheme = category.getAttribute("scheme");
				var categoryTerm = category.getAttribute("term");
				if (categoryScheme.contains("http://www.blogger.com/atom/ns#")) {
					tags.add(categoryTerm);
				}
				if (categoryScheme.contains("#kind") && categoryTerm.contains("#post")) {
					isArticle = true;
				}
			}

			if (isArticle) {
				var title = element.getElementsByTagName("title").item(0).getTextContent().trim();
				var articlePublished = element.getElementsByTagName("published").item(0).getTextContent().trim();
				var content = element.getElementsByTagName("content").item(0).getTextContent().trim();
				var publishedDate = LocalDateTime.parse(articlePublished, DateTimeFormatter.ISO_OFFSET_DATE_TIME);

				GeoLoc geoLoc = null;
				if (element.getElementsByTagName("georss:point").item(0) != null) {
					var geoLocString = element.getElementsByTagName("georss:point").item(0).getTextContent().trim();
					var geoLocStringArray = geoLocString.split(" ");
					var lat = Double.parseDouble(geoLocStringArray[0]);
					var lon = Double.parseDouble(geoLocStringArray[1]);
					geoLoc = new GeoLoc(lat, lon);
				} else {
					System.out.println("[REPORT] NO GEOLOC " + title);
				}

				if (tags.isEmpty()) {
					System.out.println("[REPORT] NO TAG " + title);
				}

				// Add article to corresponding year
				articlesByYear.computeIfAbsent(publishedDate.getYear(), key -> new ArrayList<>())
						.add(new Article(title, publishedDate, content, geoLoc));

			}
		}
		return articlesByYear;
	}

	protected static String sanitizeFilename(String value) {
		return sanitizeFilename(value, ".html");
	}

	private static String sanitizeFilename(String value, String fileExt) {
		var sanitizedFilename = stripAccents(value)
				.replaceAll("\\.+", "_")
				.replaceAll("\"", "")
				.replaceAll("[^a-zA-Z0-9.-]", "_")
				.trim()
				.toLowerCase();
		return sanitizedFilename + fileExt;
	}

	private static File downloadImage(File resourceFolder, String resourceFileName, String imageUrl)
			throws IOException, URISyntaxException {
		var resourceFile = new File(resourceFolder, resourceFileName);
		if (resourceFile.exists()) {
			// System.out.println("Already existing " + resourceFolder.getName() + "/" + resourceFileName);
		} else {
			// Establish a connection to the image URL
			var url = new URI(imageUrl).toURL();
			var connection = url.openConnection();
			System.out.println("Downloading " + imageUrl);
			try (var is = connection.getInputStream()) {
				FileUtils.writeByteArrayToFile(
						resourceFile, is.readAllBytes());
			}
		}
		return resourceFile;
	}

	// Function to recursively trim text nodes within an element
	// Used to remove ";" at beginning of some paragraph
	public static void trimTextNodes(org.jsoup.nodes.Element element) {
		for (var childNode : element.childNodes()) {
			if (childNode instanceof TextNode textNode) {
				// No more trim cause it trims whitespace befose tags...
				// var trimmedText = textNode.text().trim();
				var trimmedText = textNode.text();
				while (startsWith(trimmedText, ";")) {
					trimmedText = trimmedText.substring(1);
				}
				textNode.text(trimmedText);
			} else if (childNode instanceof org.jsoup.nodes.Element elementChild) {
				trimTextNodes(elementChild); // Recursively trim child elements
			}
		}
	}

	static record Article(
			String title,
			LocalDateTime publishedDate,
			String content,
			GeoLoc location) {

	}

	static record GeoLoc(double lat, double lon) {

	}
}
