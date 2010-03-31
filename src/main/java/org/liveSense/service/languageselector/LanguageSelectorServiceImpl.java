package org.liveSense.service.languageselector;

import au.com.bytecode.opencsv.CSVReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.osgi.framework.Bundle;

import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Language selector service
 *
 * @scr.service

 * @scr.component immediate="false" label="%service.name"
 * 					description="%service.description"
 * @scr.property name="service.description" value="Language Selector service"
 * @scr.property name="service.vendor" value="liveSense.org"
 */
public class LanguageSelectorServiceImpl implements LanguageSelectorService {

	/**
	 * default log
	 */
	private final Logger log = LoggerFactory.getLogger(LanguageSelectorServiceImpl.class);
	/**
	 * @scr.property    label="%languages.name"
	 *                  description="%languages.description"
	 *                  valueRef="DEFAULT_LANGUAGES"
	 */
	public static final String PARAM_LANGUAGES = "languages";
	public static final String[] DEFAULT_LANGUAGES = new String[]{"localhost!" + Locale.getDefault()};
	private String[] languages = DEFAULT_LANGUAGES;

	
	private HashMap<String, HashMap<String, Locale>> langs = new HashMap<String, HashMap<String,Locale>>();
	private HashMap<String, HashMap<String, Locale>> customLangs = new HashMap<String, HashMap<String,Locale>>();
	private HashMap<String, HashMap<String, String>> langNames = new HashMap<String, HashMap<String, String>>();
	private HashMap<String, HashMap<String, String>> countryNames = new HashMap<String, HashMap<String, String>>();

	private Bundle bundle = null;

	/**
	 * Activates this component.
	 *
	 * @param componentContext The OSGi <code>ComponentContext</code> of this
	 *            component.
	 */
	protected void activate(ComponentContext componentContext) {
		Dictionary<?, ?> props = componentContext.getProperties();
		bundle = componentContext.getBundleContext().getBundle();

		// Setting up languages
		String[] languagesNew = OsgiUtil.toStringArray(componentContext.getProperties().get(PARAM_LANGUAGES), DEFAULT_LANGUAGES);
		boolean languagesChanged = false;
		if (languagesNew.length != this.languages.length) {
			languagesChanged = true;
		} else {
			for (int i = 0; i < languagesNew.length; i++) {
				if (!languagesNew[i].equals(this.languages[i])) {
					languagesChanged = true;
				}
			}
			if (languagesChanged) {
				StringBuffer languagesValueList = new StringBuffer();
				StringBuffer languagesNewValueList = new StringBuffer();

				for (int i = 0; i < languagesNew.length; i++) {
					if (i != 0) {
						languagesNewValueList.append(", ");
					}
					languagesNewValueList.append(languagesNew[i].toString());
				}

				for (int i = 0; i < languages.length; i++) {
					if (i != 0) {
						languagesValueList.append(", ");
					}
					languagesValueList.append(languages[i].toString());
				}
				log.info("Setting new languages: {}) (was: {})", languagesNewValueList.toString(), languagesValueList.toString());
				this.languages = languagesNew;
			}
		}
		

		for (int i = 0; i < languages.length; i++) {
			String act = languages[i];
			String[] domainLangs = act.split("!");
			if (domainLangs.length == 2) {
				HashMap<String, Locale> domainLocales = null;
				if (customLangs.get(domainLangs[0]) == null) {
					domainLocales = new HashMap<String, Locale>();
				} else {
					domainLocales = new HashMap<String, Locale>(customLangs.get(domainLangs[0]));
				}
				String[] domLocsStr = domainLangs[1].split(",");
				for (int j = 0; j < domLocsStr.length; j++) {
					String loc = domLocsStr[j];
					domainLocales.put(loc, string2Locale(loc));
				}
				langs.put(domainLangs[0], domainLocales);
			}
		}

		URL languageCsvUrl = bundle.getEntry("/languages/iso639-1.csv");
		URL countryCsvUrl =  bundle.getEntry("/countries/iso3166-1.csv");

		// Parsing language table
		if (languageCsvUrl != null) {
			try {
				CSVReader reader = new CSVReader(new InputStreamReader(new BufferedInputStream(languageCsvUrl.openStream()),"UTF-8"));
				String [] nextLine;
				boolean firstLine = true;
				String[] colNames = null;
				int index = 0;
				while ((nextLine = reader.readNext()) != null) {
					if (firstLine) {
						colNames = nextLine.clone();
						// Determinate locale
						for (int i = 0; i < nextLine.length; i++) {
							if (nextLine[i].trim().equalsIgnoreCase("639-1")) {
								index = i;
							};
						}
						firstLine = false;
					} else {
						HashMap<String, String> lang = new HashMap<String, String>();
						for (int i = 0; i < nextLine.length; i++) {
							lang.put(colNames[i], nextLine[i]);
						}
						langNames.put(nextLine[index], lang);
					}
				}
			} catch (IOException ex) {
				log.error("Error on CSV loading ("+languageCsvUrl.getFile()+")", ex);
			}
		}


		if (countryCsvUrl != null) {
			// Parsing country table
			try {
				CSVReader reader = new CSVReader(new InputStreamReader(new BufferedInputStream(countryCsvUrl.openStream()), "UTF-8"));
				String [] nextLine;
				boolean firstLine = true;
				String[] colNames = null;
				int index = 0;
				while ((nextLine = reader.readNext()) != null) {
					if (firstLine) {
						colNames = nextLine.clone();
						// Determinate locale
						for (int i = 0; i < nextLine.length; i++) {
							if (nextLine[i].trim().equalsIgnoreCase("alpha-2")) {
								index = i;
							};
						}
						firstLine = false;
					} else {
						HashMap<String, String> country = new HashMap<String, String>();
						for (int i = 0; i < nextLine.length; i++) {
							country.put(colNames[i], nextLine[i]);
						}
						countryNames.put(nextLine[index], country);
					}
				}
			} catch (IOException ex) {
				log.error("Error on CSV loading ("+countryCsvUrl.getFile()+")", ex);
			}
		}
	}


	private Locale string2Locale(String str) {
		Locale ret = null;
		String[] locParts = str.split("_");

		if (locParts.length == 3) {
			ret = new Locale(locParts[0], locParts[1], locParts[2]);
		} else if (locParts.length == 2) {
			ret = new Locale(locParts[0], locParts[1]);
		} else if (locParts.length == 1) {
			ret = new Locale(locParts[0]);
		}
		return ret;
	}


	public void addLanguage(String domain,String locale) {
		addLanguage(domain, string2Locale(locale));
	}

	public void addLanguage(String domain, Locale locale) {
		HashMap<String, Locale> domainLocalesCustom = customLangs.get(domain);
		if (domainLocalesCustom == null) {
			domainLocalesCustom = new HashMap<String, Locale>();
			domainLocalesCustom.put(locale.toString(), locale);
			customLangs.put(domain, domainLocalesCustom);
		}
		HashMap<String, Locale> domainLocales = langs.get(domain);
		if (domainLocales == null) {
			domainLocales = new HashMap<String, Locale>();
			domainLocales.put(locale.toString(), locale);
			langs.put(domain, domainLocalesCustom);
		}
	}

	public List<Locale> getAvailableLanguages(String domain) {
		return new ArrayList(langs.values());
	}

	public String getLanguageName(Locale lang, Locale locale) {
		String ret = null;
		HashMap<String, String> langHash = langNames.get(lang.getLanguage());
		if (langHash != null && langHash.get(locale.toString()) != null) {
			ret = langHash.get(locale.toString());
		}
		return ret;
	}

	public String getLanguageName(String lang, String locale) {
		return getLanguageName(string2Locale(lang), string2Locale(locale));
	}


	public InputStream getFlag(String locale, String size, String imageType) {
		URL flagImageUrl = bundle.getEntry("/countries/flags/"+locale+"."+size+"."+imageType);
		if (flagImageUrl != null) {
			try {
				return flagImageUrl.openStream();
			} catch (IOException ex) {
				log.error("Cannot get flag: "+flagImageUrl.getFile());
			}
		}
		return null;
	}
}
