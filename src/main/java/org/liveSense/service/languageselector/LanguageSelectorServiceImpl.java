package org.liveSense.service.languageselector;

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

import javax.servlet.http.HttpServletRequest;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyUnbounded;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.liveSense.core.Configurator;
import org.liveSense.core.wrapper.RequestWrapper;
import org.osgi.framework.Bundle;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.bytecode.opencsv.CSVReader;

/** 
 * Language selector service
 */


@Component(immediate=true, metatype=true, label="%langugae.service.name", description="%language.service.description")
@Service
@Properties(value={
		@Property(name=LanguageSelectorServiceImpl.PARAM_LANGUAGES, label="%languages.name", description="%languages.description", value = {LanguageSelectorServiceImpl.DEFAULT_LANGUAGE}, unbounded=PropertyUnbounded.ARRAY),
		@Property(name=LanguageSelectorServiceImpl.PARAM_STORE_KEY_NAME, label="%storeKey.name", description="%storeKey.description", value=LanguageSelectorServiceImpl.DEFAULT_STORE_KEY_NAME),
		@Property(name="service.vendor", value="liveSense.org")
})
public class LanguageSelectorServiceImpl implements LanguageSelectorService {

	/**
	 * default log
	 */
	private final Logger log = LoggerFactory.getLogger(LanguageSelectorServiceImpl.class);

	public static final String PARAM_LANGUAGES = "languages";
	public static final String DEFAULT_LANGUAGE = "localhost!en_US";
	public static final String[] DEFAULT_LANGUAGES = new String[]{DEFAULT_LANGUAGE}; //+ Locale.getDefault();


	private String[] languages = DEFAULT_LANGUAGES;

	static final String STORE_LOCALE_KEY = "locale";

	public static final String PARAM_STORE_KEY_NAME = "storeKeyName";
	public static final String DEFAULT_STORE_KEY_NAME = STORE_LOCALE_KEY;

	private String storeKeyName = DEFAULT_STORE_KEY_NAME;

	private final HashMap<String, HashMap<String, Locale>> langs = new HashMap<String, HashMap<String,Locale>>();
	private final HashMap<String, Locale> domainDefaults = new HashMap<String, Locale>();
	private final HashMap<String, HashMap<String, Locale>> customLangs = new HashMap<String, HashMap<String,Locale>>();
	private final HashMap<String, HashMap<String, String>> langNames = new HashMap<String, HashMap<String, String>>();
	private final HashMap<String, HashMap<String, String>> countryNames = new HashMap<String, HashMap<String, String>>();


	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
	Configurator configurator;

	private Bundle bundle = null;

	/**
	 * Activates this component.
	 *
	 * @param componentContext The OSGi <code>ComponentContext</code> of this
	 *            component.
	 */
	@Activate
	protected void activate(ComponentContext componentContext) {
		Dictionary<?, ?> props = componentContext.getProperties();
		bundle = componentContext.getBundleContext().getBundle();

		this.storeKeyName = (String) componentContext.getProperties().get(PARAM_STORE_KEY_NAME);

		this.languages = PropertiesUtil.toStringArray(componentContext.getProperties().get(PARAM_LANGUAGES), DEFAULT_LANGUAGES);
		customLangs.clear();
		domainDefaults.clear();
		langs.clear();

		for (int i = 0; i < this.languages.length; i++) {
			String act = this.languages[i];
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
					if (j==0) 
						domainDefaults.put(domainLangs[0], string2Locale(loc));
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

	private static Locale string2Locale(String str) {
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
		List<Locale> ret = new ArrayList<Locale>();
		if (langs.get(domain) != null) {
			for (Locale locale : langs.get(domain).values()) {
				ret.add(locale);
			}
		}
		return ret;
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

	public Locale getLocaleByRequest(
			HttpServletRequest request) {

		RequestWrapper rw = new RequestWrapper(request, configurator.getDefaultLocale(), storeKeyName);
		return rw.getLocale();
	}

	public Locale getLocaleByRequest(
			HttpServletRequest request, Locale defaultLocale) {

		RequestWrapper rw = new RequestWrapper(request, defaultLocale, storeKeyName);
		return rw.getLocale();
	}


	public String getStoreKeyName() {
		return storeKeyName;
	}

}
