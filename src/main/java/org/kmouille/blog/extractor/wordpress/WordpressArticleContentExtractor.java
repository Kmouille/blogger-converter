package org.kmouille.blog.extractor.wordpress;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.kmouille.blog.domain.ArticleContent;

public class WordpressArticleContentExtractor {

	protected ArticleContent extractContent(String rawContent, String title) {
		var html = Jsoup.parseBodyFragment(rawContent);
		html.title(title);
		html.body().prependElement("h1").text(title);
		// TODO Complete...

		// Find all <a> tags
		for (var link : html.select("a")) {
			// Insert a <br> tag after each <a> tag
			link.after("<br>");
		}

		// Wrap images with an <a> for future work?
		// Or could just keep image?
		for (var img : html.select("img")) {
			if (img.parents().select("a").isEmpty()) {
				var imgSrc = img.attr("src");
				var link = new Element("a").attr("href", imgSrc);
				img.wrap(link.toString());
			}
		}

		// TODO replace figCaption by a-img-p
		// <div class="wp-block-image"><figure class="aligncenter"><a href="

		// html.select("div:has(figure)").unwrap();
		// html.select("figcaption").unwrap();
		// html.select("figure").unwrap();

		// TODO Des fois une image inexistante a un lien vers un article de type attachment avec un parent
		// <title><![CDATA[mini_20190802_111442-1]]></title>
		// <link>http://vnuilbg.cluster031.hosting.ovh.net/bc811172-ovh/index.php/2019/08/04/valence/mini_20190802_111442-1/</link>
		// <pubDate>Fri, 23 Aug 2019 13:43:43 +0000</pubDate>
		// <dc:creator><![CDATA[admin9424]]></dc:creator>
		// <guid isPermaLink="false">http://4alamaison.ovh/wp-content/uploads/2019/08/mini_20190802_111442-1.jpg</guid>
		// <description></description>
		// <content:encoded><![CDATA[]]></content:encoded>
		// <wp:post_parent>2802</wp:post_parent>
		// <wp:menu_order>0</wp:menu_order>
		// <wp:post_type><![CDATA[attachment]]></wp:post_type>

		return new ArticleContent(html.html(), List.of());
	}

}
