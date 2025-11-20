package org.wtlnw.eclipse.log4j.viewer.ui.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.swt.graphics.RGB;
import org.wtlnw.eclipse.log4j.viewer.ui.Activator;


/**
 * Class used to initialize default preference values.
 */
public class LogViewerPreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		final IPreferenceStore store = Activator.getInstance().getPreferenceStore();
		
		store.setDefault(LogViewerPreferenceConstants.PORT, 4445);
		store.setDefault(LogViewerPreferenceConstants.TIMEOUT, 500);
		store.setDefault(LogViewerPreferenceConstants.REFRESH, 250);
		store.setDefault(LogViewerPreferenceConstants.BUFFER, 1 << 12);
		store.setDefault(LogViewerPreferenceConstants.AUTOSTART, true);
		
		PreferenceConverter.setDefault(store, LogViewerPreferenceConstants.COLOR_DEBUG, new RGB(0, 0, 0));
		PreferenceConverter.setDefault(store, LogViewerPreferenceConstants.COLOR_INFO, new RGB(0, 128, 0));
		PreferenceConverter.setDefault(store, LogViewerPreferenceConstants.COLOR_WARN, new RGB(255, 128, 0));
		PreferenceConverter.setDefault(store, LogViewerPreferenceConstants.COLOR_ERROR, new RGB(255, 0, 0));
		PreferenceConverter.setDefault(store, LogViewerPreferenceConstants.COLOR_FATAL, new RGB(128, 0, 0));
	}
}
