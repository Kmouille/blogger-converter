# Blogger Converter

Very basic converter that takes a XML Blogger export file and generates a local static website.

## Purpose

The idea is to have a very basic website, very simple, locally, to try to make a photobook easier.
With a very basic file architecture, it should be easy to design a photo book based on a blog.
Articles are separated in distinct html files and linked resources (images) are stored in dedicated folder per article.


> Downloading of images is optional

** Features ** 


*   Try to download the highest available image manipulating different URL (across the ages, depending on the source, there are operations to retrieve thumbnails or broken links during article edition...)
*   Sanitize filenames
*   Basic map
*   Start of reporting (to design and improve) no tag articles and no geolocalized articles
*   Introduction of a basic map locating articles (biggest missing blogger feature?)
*   Online site generation: continue retrieving images online
*   Clean unused resources files on reimport

## TODO

*   Convert image embedded data into files for easy image drag'n'drop into photo book editor (maybe copy paste is enough)
*   Add a bit of design
*   Implement basic tests
*   Improve options
*   Use credentials for private blogs
*   Convert into photobook format :D 

...