package org.kmouille.blog.extractor.exception;

// TODO Improve expception management
public class BlogException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public BlogException(Exception e) {
		super(e);
	}

}
