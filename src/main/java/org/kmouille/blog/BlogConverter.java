package org.kmouille.blog;

import static org.apache.commons.lang3.StringUtils.abbreviate;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;
import org.kmouille.blog.domain.Article;
import org.kmouille.blog.domain.Blog;
import org.kmouille.blog.domain.GeoLoc;
import org.tinylog.Logger;

public class BlogConverter {

	/** Abbreviate filename max length for logging purpose. */
	private int abbreviateMaxLength = 30;

	/** Filename max length. Sometimes google or blogger API uses very long encoded names. */
	private int filenameMaxLength = 20;

	private boolean cleanupUnusedFiles = false;

	void convert(Blog blog, File bookDestinationFolder) throws IOException, URISyntaxException {
		bookDestinationFolder.mkdirs();
		convert(blog, bookDestinationFolder, false);
	}

	void convert(Blog blog, File bookDestinationFolder, boolean downloadImages) throws IOException, URISyntaxException {

		var articlesByYear = blog.articles().stream()
				.collect(Collectors.groupingBy(a -> a.publishedDate().getYear()));

		var sortedYears = new ArrayList<>(articlesByYear.keySet());
		sortedYears.sort(Comparator.naturalOrder());

		var welcomePageIndexHtml = new StringBuilder();

		// Could implement a general cleaner.
		// var htmlCleaner = new Cleaner(Safelist.basicWithImages());

		List<String> leafletMarkers = new ArrayList<>();

		for (var year : sortedYears) {
			var articles = articlesByYear.get(year);

			Logger.info("[REPORT] Writing year {} - {} articles", year, articles.size());

			var yearSubFolder = new File(bookDestinationFolder, year + "_articles");
			yearSubFolder.mkdirs();

			var yearIndexHtml = new StringBuilder();
			articles.sort(Comparator.comparing(Article::publishedDate));
			for (var article : articles) {
				var imageFiles = new ArrayList<File>();
				var imageUrls = new ArrayList<String>();

				var htmlFilename = article.publishedDate().format(DateTimeFormatter.ofPattern("YYYYMMdd"))
						+ "-" + sanitizeFilename(article.title());

				var resourceFolderName = FilenameUtils.getBaseName(htmlFilename);
				var resourceFolder = new File(yearSubFolder, resourceFolderName);
				resourceFolder.mkdirs();

				var linkTag = "<a href=\"" + year + "_articles/" + htmlFilename + "\" target=\"_blank\">"
						+ article.publishedDate().format(DateTimeFormatter.ofPattern("YYYY-MM-dd")) + " - "
						+ article.title() + "</a>";

				// Add link to the welcome page
				yearIndexHtml.append("<li>" + linkTag + "</li>");

				var jsoupArticleDoc = Jsoup.parse(article.agnosticContent().content());

				// TODO Download images not wrapped into a link?
				for (var img : jsoupArticleDoc.select("img")) {
					if (img.parents().select("a").isEmpty()) {
						Logger.info(" MISSING Found <img> tag without <a> parent in {} (src: {})",
								article.title(), abbreviate(img.attr("src"), 100));
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
					// -> url...?fit=...
					if (containsAny(resourceFileName, "?")) {
						var paramedName = resourceFileName;
						resourceFileName = substringBeforeLast(resourceFileName, "?fit");
						Logger.debug(" URL PARAMED {} BECAME {}/{}", paramedName, resourceFolderName, resourceFileName);
						// Change the URL not to ask for the resized image to download hires img
						imageUrl = substringBeforeLast(imageUrl, "?fit");
					}
					// Might have requests like:
					// -> =s320
					// -> =w240-h320
					else if (containsAny(resourceFileName, "=s", "=w", "=h")) {
						var resizedName = abbreviateMiddle(resourceFileName, "...", abbreviateMaxLength);
						resourceFileName = truncate(substringBeforeLast(resourceFileName, "="), filenameMaxLength)
								+ ".jpg";
						Logger.debug(" RESIZED {} BECAME {}/{}", resizedName, resourceFolderName, resourceFileName);
						// Change the URL not to ask for the resized image to download hires img
						imageUrl = substringBeforeLast(imageUrl, "=");
					} else if (!endsWithAny(resourceFileName.toLowerCase(), ".png", ".jpeg", ".jpg", ".bmp")) {
						var unsupportedName = abbreviateMiddle(resourceFileName, "...", abbreviateMaxLength);
						// QUESTION How not to suppose JPG?
						resourceFileName = substring(resourceFileName, 0, filenameMaxLength) + ".jpg";
						Logger.debug(" UNSUPPORTED {} BECAME {}/{}",
								unsupportedName, resourceFolderName, resourceFileName);
					} else if (containsAny(resourceFileName, "=S", "=W", "=H")) {
						Logger.warn("FIX CASE SENSITIVE RESIZE PARAMS");
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
					img.removeAttr("class");
					img.removeAttr("alt");
				}
				trimTextNodes(jsoupArticleDoc.root());
				removeComments(jsoupArticleDoc);
				try (var writer = new FileWriter(new File(yearSubFolder, htmlFilename))) {
					writer.write(jsoupArticleDoc.toString());
					// writer.write(htmlCleaner.clean(jsoupArticleDoc).toString());
				}

				// clean directory ONLY if download images is activated
				if (downloadImages) {
					for (File legacyFile : resourceFolder.listFiles()) {
						if (!imageFiles.contains(legacyFile)) {
							if (cleanupUnusedFiles) {
								Logger.info("[REPORT] REMOVE UNUSED {}", legacyFile);
								legacyFile.delete();
							} else {
								Logger.info("[REPORT] SHOULD REMOVE UNUSED {}", legacyFile);
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
		var websiteTitle = blog.title();
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

		Logger.info("Conversion completed successfully.");

	}

	protected String leafletMarker(GeoLoc geoLoc, String content) {
		return "L.marker([" + geoLoc.lat() + ", " + geoLoc.lon() + "])"
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
			Logger.debug(" ENCODED {} BECAME {}", fileName, resultFileName);
		}
		return resultFileName;
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
			// Logger.info("Already existing " + resourceFolder.getName() + "/" + resourceFileName);
		} else {
			// Establish a connection to the image URL
			var url = new URI(imageUrl).toURL();
			var connection = url.openConnection();
			Logger.debug("Downloading {}", imageUrl);
			try (var is = connection.getInputStream()) {
				FileUtils.writeByteArrayToFile(resourceFile, is.readAllBytes());
			} catch (Exception e) {
				Logger.debug("Downloading failed {}", imageUrl, e);
			}

		}
		return resourceFile;
	}

	// Function to recursively trim text nodes within an element
	// Used to remove ";" at beginning of some paragraph
	public static void trimTextNodes(org.jsoup.nodes.Element element) {
		for (var childNode : element.childNodes()) {
			if (childNode instanceof TextNode textNode) {
				// No more trim cause it trims whitespace before tags...
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

	public static void removeComments(Document jsoupArticleDoc) {
		jsoupArticleDoc.traverse(new NodeVisitor() {

			@Override
			public void head(Node node, int depth) {
				if (node instanceof Comment) {
					node.remove();
				}
			}

			@Override
			public void tail(Node node, int depth) {
				// No operation
			}
		});
	}

}
