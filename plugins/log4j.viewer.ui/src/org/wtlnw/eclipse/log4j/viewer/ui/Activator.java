package org.wtlnw.eclipse.log4j.viewer.ui;

import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.resource.ResourceLocator;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * {@link AbstractUIPlugin} implementation for this bundle.
 */
public class Activator extends AbstractUIPlugin {
	
	public static final String IMG_FILTER_INACTIVE = "filter_inactive";
	public static final String IMG_FILTER_ACTIVE = "filter_active";
	public static final String IMG_CLEAR = "clear";
	public static final String IMG_STOP = "stop";
	public static final String IMG_START = "start";
	public static final String IMG_PAUSE = "pause";
	
	public static final String IMG_MATCH_CASE = "match-case";
	public static final String IMG_REGEX = "regular-expression";
	public static final String IMG_WHOLE_WORD = "whole-word";
	public static final String IMG_INVERT = "invert";
	
	/**
	 * @see #getInstance()
	 */
	private static Activator INSTANCE;

	/**
	 * @return the {@link Activator} instance or {@code null} if the bundle has not
	 *         been started yet or has already been shutdown
	 */
	public static Activator getInstance() {
		return INSTANCE;
	}
	
	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		INSTANCE = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		INSTANCE = null;
		super.stop(context);
	}

	@Override
	protected void initializeImageRegistry(final ImageRegistry images) {
		images.put(IMG_PAUSE, ResourceLocator.imageDescriptorFromBundle(getClass(), "/icons/pause.png").get());
		images.put(IMG_START, ResourceLocator.imageDescriptorFromBundle(getClass(), "/icons/start.png").get());
		images.put(IMG_STOP, ResourceLocator.imageDescriptorFromBundle(getClass(), "/icons/stop.png").get());
		images.put(IMG_CLEAR, ResourceLocator.imageDescriptorFromBundle(getClass(), "/icons/clear.png").get());
		images.put(IMG_FILTER_ACTIVE, ResourceLocator.imageDescriptorFromBundle(getClass(), "/icons/filter-active.png").get());
		images.put(IMG_FILTER_INACTIVE, ResourceLocator.imageDescriptorFromBundle(getClass(), "/icons/filter-inactive.png").get());

		images.put(IMG_MATCH_CASE, ResourceLocator.imageDescriptorFromBundle(getClass(), "/icons/case_sensitive.png").get());
		images.put(IMG_REGEX, ResourceLocator.imageDescriptorFromBundle(getClass(), "/icons/regex.png").get());
		images.put(IMG_WHOLE_WORD, ResourceLocator.imageDescriptorFromBundle(getClass(), "/icons/whole_word.png").get());
		images.put(IMG_INVERT, ResourceLocator.imageDescriptorFromBundle(getClass(), "/icons/invert.png").get());
	}
}
