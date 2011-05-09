package org.liveSense.service.languageselector;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

public interface LanguageSelectorService {
	public void addLanguage(String domain,String locale);
	public void addLanguage(String domain, Locale locale);
    public List<Locale> getAvailableLanguages(String domain);
	public String getLanguageName(Locale lang, Locale locale);
	public String getLanguageName(String lang, String locale);
	public InputStream getFlag(String locale, String size, String imageType);
	public Locale getLocaleByRequest(HttpServletRequest request);
	public String getStoreKeyName();
}
