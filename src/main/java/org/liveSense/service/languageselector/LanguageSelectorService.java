package org.liveSense.service.languageselector;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

public interface LanguageSelectorService {
	void addLanguage(String domain,String locale);
	void addLanguage(String domain, Locale locale);
    List<Locale> getAvailableLanguages(String domain);
	String getLanguageName(Locale lang, Locale locale);
	String getLanguageName(String lang, String locale);
	InputStream getFlag(String locale, String size, String imageType);
	Locale getLocaleByRequest(HttpServletRequest request);
	String getStoreKeyName();
	Locale getLocaleByRequest(HttpServletRequest request, Locale defaultLocale);
}
