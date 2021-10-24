/*
 * aoserv-jilter-config - Configuration API for AOServ Jilter.
 * Copyright (C) 2007-2013, 2014, 2015, 2016, 2020, 2021  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of aoserv-jilter-config.
 *
 * aoserv-jilter-config is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * aoserv-jilter-config is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with aoserv-jilter-config.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.aoserv.jilter.config;

import com.aoapps.lang.io.FileUtils;
import com.aoapps.lang.util.PropertiesUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * In order to avoid a real-time dependency between the Jilter code and the full
 * AOServ Platform, the configuration values for the jilter is placed into a
 * properties file that is stored in
 * <code>/etc/opt/aoserv-jilter/aoserv-jilter.properties</code>.  The
 * <code>aoserv-daemon</code> will rebuild this file when any of its related
 * tables have been updated, and at daemon start-up.  The result is that jilter
 * can work off the last config file even when AOServ is down for maintenance of
 * not working properly.
 *
 * @author  AO Industries, Inc.
 */
@SuppressWarnings({"overrides", "EqualsAndHashcode"}) // We will not implement hashCode, despite having equals
public class JilterConfiguration {

	/**
	 * The default milter port.
	 */
	public static final int DEFAULT_MILTER_PORT = 12000;

	/**
	 * This version number should be updated whenever the on-disk format of the configuration has been changed in an
	 * incompatible way.  Newer versions of the code may choose to support older formats, but this should
	 * not be necessary because the aoserv-daemon will overwrite the config file within a minute of start-up.
	 */
	private static final String VERSION_1="2007-05-13";
	private static final String VERSION_2="2009-12-15";
	private static final String VERSION_3="2013-07-13";

	public static final String PROPS_FILE = "/etc/opt/aoserv-jilter/aoserv-jilter.properties";
	private static final String NEW_PROPS_FILE = "/etc/opt/aoserv-jilter/aoserv-jilter.properties.new";

	private static final File propsUF = new File(PROPS_FILE);
	private static final File newPropsUF = new File(NEW_PROPS_FILE);

	private static final Log log = LogFactory.getLog(JilterConfiguration.class);

	private static long lastModifiedTime;
	private static JilterConfiguration jilterConfiguration;

	/**
	 * Gets the current configuration.  If the file has been updated, will reload the configuration
	 * from disk.
	 */
	public static synchronized JilterConfiguration getJilterConfiguration() throws IOException {
		long modifyTime = propsUF.lastModified();
		if(jilterConfiguration==null || modifyTime!=lastModifiedTime) {
			jilterConfiguration = new JilterConfiguration();
			lastModifiedTime = modifyTime;
		}
		return jilterConfiguration;
	}

	private final String version;
	private final String listenIP;
	private final int listenPort;
	private final boolean restrict_outbound_email;
	private final String smtpServer;
	private final String emailSummaryFrom;
	private final String emailSummaryTo;
	private final String emailFullFrom;
	private final String emailFullTo;
	private final Map<String, String> domainBusinesses;
	private final Map<String, Set<String>> domainAddresses;
	private final Set<String> ips;
	private final Set<String> denies;
	private final Set<String> denySpams;
	private final Set<String> allowRelays;
	private final Map<String, EmailLimit> emailInLimits;
	private final Map<String, EmailLimit> emailOutLimits;
	private final Map<String, EmailLimit> emailRelayLimits;

	public JilterConfiguration(
		String listenIP,
		int listenPort,
		boolean restrict_outbound_email,
		String smtpServer,
		String emailSummaryFrom,
		String emailSummaryTo,
		String emailFullFrom,
		String emailFullTo,
		Map<String, String> domainBusinesses,
		Map<String, Set<String>> domainAddresses,
		Set<String> ips,
		Set<String> denies,
		Set<String> denySpams,
		Set<String> allowRelays,
		Map<String, EmailLimit> emailInLimits,
		Map<String, EmailLimit> emailOutLimits,
		Map<String, EmailLimit> emailRelayLimits
	) {
		this.version = VERSION_3;
		this.listenIP = listenIP;
		this.listenPort = listenPort;
		this.restrict_outbound_email = restrict_outbound_email;
		this.smtpServer = smtpServer;
		this.emailSummaryFrom = emailSummaryFrom;
		this.emailSummaryTo = emailSummaryTo;
		this.emailFullFrom = emailFullFrom;
		this.emailFullTo = emailFullTo;
		this.domainBusinesses = domainBusinesses;
		this.domainAddresses = domainAddresses;
		this.ips = ips;
		this.denies = denies;
		this.denySpams = denySpams;
		this.allowRelays = allowRelays;
		this.emailInLimits = emailInLimits;
		this.emailOutLimits = emailOutLimits;
		this.emailRelayLimits = emailRelayLimits;
	}

	/**
	 * Loads the current configuration from the props file.
	 *
	 * @see  #getJilterConfiguration
	 */
	private JilterConfiguration() throws IOException {
		Properties props = PropertiesUtils.loadFromFile(propsUF);

		// Make sure the version matches, throw IOException if doesn't
		final String businessesKey;
		version = props.getProperty("version");
		if(VERSION_1.equals(version)) businessesKey="domainPackages";
		else businessesKey = "domainBusinesses";

		// listenIP
		listenIP = props.getProperty(
			VERSION_1.equals(version) || VERSION_2.equals(version)
			? "primaryIP"
			: "listenIP"
		);

		// listenPort
		try {
			listenPort =
				VERSION_1.equals(version) || VERSION_2.equals(version)
				? DEFAULT_MILTER_PORT
				: Integer.parseInt(props.getProperty("listenPort"))
			;
		} catch(NumberFormatException err) {
			throw new IOException("Unable to parse listenPort: "+props.getProperty("listenPort"), err);
		}

		// restrict_outbound_email
		restrict_outbound_email = Boolean.parseBoolean(props.getProperty("restrict_outbound_email"));

		// Email settings
		smtpServer = props.getProperty("smtp.server");
		emailSummaryFrom = props.getProperty("email.summary.from");
		emailSummaryTo = props.getProperty("email.summary.to");
		emailFullFrom = props.getProperty("email.full.from");
		emailFullTo = props.getProperty("email.full.to");

		domainBusinesses = new HashMap<>();
		domainAddresses = new HashMap<>();
		ips = new HashSet<>();
		denies = new HashSet<>();
		denySpams = new HashSet<>();
		allowRelays = new HashSet<>();
		emailInLimits = new HashMap<>();
		emailOutLimits = new HashMap<>();
		emailRelayLimits = new HashMap<>();
		Enumeration<?> e = props.propertyNames();
		while(e.hasMoreElements()) {
			String key = (String)e.nextElement();
			String value = props.getProperty(key);
			if(key.startsWith(businessesKey+".")) {
				// domainBusinesses
				int pos = value.indexOf('|');
				if(pos==-1) throw new IOException("Unable to parse "+businessesKey+": "+value);
				String accounting = value.substring(0, pos);
				String domain = value.substring(pos+1);
				domainBusinesses.put(domain, accounting);
				// Add to domainAddresses just in case the domain has no addresses
				if(!domainAddresses.containsKey(domain)) domainAddresses.put(domain, new HashSet<>());
			} else if(key.startsWith("addresses.")) {
				// domainAddresses
				int pos = value.indexOf('@');
				if(pos==-1) throw new IOException("Unable to find @ in address: "+value);
				String address = value.substring(0, pos);
				String domain = value.substring(pos+1);

				Set<String> addresses = domainAddresses.get(domain);
				if(addresses==null) domainAddresses.put(domain, addresses = new HashSet<>());
				addresses.add(address);
			} else if(key.startsWith("ips.")) {
				// ips
				ips.add(value);
			} else if(key.startsWith("denies.")) {
				// denies
				denies.add(value);
			} else if(key.startsWith("denySpams.")) {
				// denySpams
				denySpams.add(value);
			} else if(key.startsWith("allowRelays.")) {
				// allowRelays
				allowRelays.add(value);
			} else if(key.startsWith("emailInLimits.")) {
				// emailInLimits
				int pos1 = value.indexOf('|');
				if(pos1==-1) throw new IOException("Unable to parse emailInLimits: "+value);
				int pos2 = value.indexOf('|', pos1+1);
				if(pos2==-1) throw new IOException("Unable to parse emailInLimits: "+value);
				try {
					String name = value.substring(0, pos1);
					int burst = Integer.parseInt(value.substring(pos1+1, pos2));
					float rate = Float.parseFloat(value.substring(pos2+1));
					emailInLimits.put(name, new EmailLimit(burst, rate));
				} catch(NumberFormatException err) {
					throw new IOException("Unable to parse emailInLimits: "+value, err);
				}
			} else if(key.startsWith("emailOutLimits.")) {
				// emailOutLimits
				int pos1 = value.indexOf('|');
				if(pos1==-1) throw new IOException("Unable to parse emailOutLimits: "+value);
				int pos2 = value.indexOf('|', pos1+1);
				if(pos2==-1) throw new IOException("Unable to parse emailOutLimits: "+value);
				try {
					String name = value.substring(0, pos1);
					int burst = Integer.parseInt(value.substring(pos1+1, pos2));
					float rate = Float.parseFloat(value.substring(pos2+1));
					emailOutLimits.put(name, new EmailLimit(burst, rate));
				} catch(NumberFormatException err) {
					throw new IOException("Unable to parse emailOutLimits: "+value, err);
				}
			} else if(key.startsWith("emailRelayLimits.")) {
				// emailRelayLimits
				int pos1 = value.indexOf('|');
				if(pos1==-1) throw new IOException("Unable to parse emailRelayLimits: "+value);
				int pos2 = value.indexOf('|', pos1+1);
				if(pos2==-1) throw new IOException("Unable to parse emailRelayLimits: "+value);
				try {
					String name = value.substring(0, pos1);
					int burst = Integer.parseInt(value.substring(pos1+1, pos2));
					float rate = Float.parseFloat(value.substring(pos2+1));
					emailRelayLimits.put(name, new EmailLimit(burst, rate));
				} catch(NumberFormatException err) {
					throw new IOException("Unable to parse emailRelayLimits: "+value, err);
				}
			}
		}
		// Make all domainAddresses lists unmodifiable
		for(Map.Entry<String, Set<String>> entry : domainAddresses.entrySet()) {
			entry.setValue(Collections.unmodifiableSet(entry.getValue()));
		}
	}

	/**
	 * Saves the new configuration if either the config file doesn't exist or has changed in any way.
	 */
	public void saveIfChanged(String comment) throws IOException {
		boolean matches;
		if(!propsUF.exists()) {
			if(log.isTraceEnabled()) log.trace("configuration file doesn't exist: "+PROPS_FILE);
			matches = false;
		} else {
			// Compare to the persistent copy
			try {
				log.trace("configuration file exists, loading persistent copy");
				JilterConfiguration current = getJilterConfiguration();
				matches = equals(current);
			} catch(IOException err) {
				log.warn("Can't load existing configuration, building new configuration file: "+PROPS_FILE, err);
				matches = false;
			}
		}
		if(!matches) {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			try {
				@SuppressWarnings("deprecation")
				Properties props = new com.aoapps.collections.SortedProperties();

				// VERSION
				props.setProperty("version", VERSION_3);

				// listenIP
				props.setProperty("listenIP", listenIP);

				// listenPort
				props.setProperty("listenPort", Integer.toString(listenPort));

				// restrict_outbound_email
				props.setProperty("restrict_outbound_email", Boolean.toString(restrict_outbound_email));

				// Email settings
				if(smtpServer!=null) props.setProperty("smtp.server", smtpServer);
				if(emailSummaryFrom!=null) props.setProperty("email.summary.from", emailSummaryFrom);
				if(emailSummaryTo!=null) props.setProperty("email.summary.to", emailSummaryTo);
				if(emailFullFrom!=null) props.setProperty("email.full.from", emailFullFrom);
				if(emailFullTo!=null) props.setProperty("email.full.to", emailFullTo);

				// domainBusinesses
				int domainCounter = 1;
				for(String domain : domainBusinesses.keySet()) {
					props.setProperty("domainBusinesses."+(domainCounter++), domainBusinesses.get(domain)+"|"+domain);
				}

				// domainAddresses
				int addressCounter = 1;
				for(String domain : domainAddresses.keySet()) {
					for(String address : domainAddresses.get(domain)) {
						props.setProperty("addresses."+(addressCounter++), address+'@'+domain);
					}
				}

				// ips
				int ipCounter = 1;
				for(String ip : ips) {
					props.setProperty("ips."+(ipCounter++), ip);
				}

				// denies
				int deniesCounter = 1;
				for(String deny : denies) {
					props.setProperty("denies."+(deniesCounter++), deny);
				}

				// denySpams
				int denySpamsCounter = 1;
				for(String denySpam : denySpams) {
					props.setProperty("denySpams."+(denySpamsCounter++), denySpam);
				}

				// denySpams
				int allowRelaysCounter = 1;
				for(String allowRelay : allowRelays) {
					props.setProperty("allowRelays."+(allowRelaysCounter++), allowRelay);
				}

				// emailInLimits
				int emailInLimitsCounter = 1;
				for(String name : emailInLimits.keySet()) {
					EmailLimit limit = emailInLimits.get(name);
					props.setProperty("emailInLimits."+(emailInLimitsCounter++), name+"|"+limit.getBurst()+"|"+limit.getRate());
				}

				// emailOutLimits
				int emailOutLimitsCounter = 1;
				for(String name : emailOutLimits.keySet()) {
					EmailLimit limit = emailOutLimits.get(name);
					props.setProperty("emailOutLimits."+(emailOutLimitsCounter++), name+"|"+limit.getBurst()+"|"+limit.getRate());
				}

				// emailRelayLimits
				int emailRelayLimitsCounter = 1;
				for(String name : emailRelayLimits.keySet()) {
					EmailLimit limit = emailRelayLimits.get(name);
					props.setProperty("emailRelayLimits."+(emailRelayLimitsCounter++), name+"|"+limit.getBurst()+"|"+limit.getRate());
				}

				props.store(bout, comment);
			} finally {
				bout.close();
			}
			byte[] bytes = bout.toByteArray();
			FileOutputStream fileOut = new FileOutputStream(NEW_PROPS_FILE);
			try {
				fileOut.write(bytes);
			} finally {
				fileOut.close();
			}
			FileUtils.rename(newPropsUF, propsUF);
		}
	}

	/**
	 * Gets the version of this file this was loaded from.
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * @see  com.aoindustries.aoserv.client.net.Host#getNetBinds(com.aoindustries.aoserv.client.net.AppProtocol)
	 * @see  com.aoindustries.aoserv.client.net.AppProtocol#MILTER
	 */
	public String getListenIP() {
		return listenIP;
	}

	/**
	 * @see  com.aoindustries.aoserv.client.net.Host#getNetBinds(com.aoindustries.aoserv.client.net.AppProtocol)
	 * @see  com.aoindustries.aoserv.client.net.AppProtocol#MILTER
	 */
	public int getListenPort() {
		return listenPort;
	}

	/**
	 * @see  com.aoindustries.aoserv.client.linux.Server#getRestrictOutboundEmail()
	 */
	public boolean getRestrictOutboundEmail() {
		return restrict_outbound_email;
	}

	/**
	 * Gets the SMTP server to use for outbound email, empty or <code>null</code> means don't send emails.
	 */
	public String getSmtpServer() {
		return smtpServer;
	}

	/**
	 * Gets the from address to be used for the summary email.
	 */
	public String getEmailSummaryFrom() {
		return emailSummaryFrom;
	}

	/**
	 * Gets the comma-separated list of to addresses to be used for the summary email.
	 */
	public String getEmailSummaryTo() {
		return emailSummaryTo;
	}

	/**
	 * Gets the from address to be used for the full email.
	 */
	public String getEmailFullFrom() {
		return emailFullFrom;
	}

	/**
	 * Gets the comma-separated list of to addresses to be used for the full email.
	 */
	public String getEmailFullTo() {
		return emailFullTo;
	}

	/**
	 * Gets the unique business name for a domain or <code>null</code> if domain doesn't exist.
	 */
	public String getBusiness(String domain) {
		return domainBusinesses.get(domain.toLowerCase(Locale.ENGLISH));
	}

	/**
	 * Gets the set of addresses for a specific domain (without the @domain part of the address).  If the domain exists and has no addresses, returns an empty set.  If the
	 * domain doesn't exist at all then returns <code>null</code>.  Domain is case-insensitive by conversion to lower-case.
	 */
	public Set<String> getAddresses(String domain) {
		return domainAddresses.get(domain.toLowerCase(Locale.ENGLISH));
	}

	/**
	 * Returns <code>true</code> if the provided IP address is a local address.
	 */
	public boolean isLocalIPAddress(String ip) {
		return ips.contains(ip);
	}

	/**
	 * Returns <code>true</code> if the provided IP address is denied.
	 */
	public boolean isDenied(String ip) {
		return denies.contains(ip);
	}

	/**
	 * Returns <code>true</code> if the provided IP address is denied because it has been reported as sending spam.
	 */
	public boolean isDeniedSpam(String ip) {
		return denySpams.contains(ip);
	}

	/**
	 * Returns <code>true</code> if the provided IP address has explicit relay permission granted.
	 */
	public boolean isAllowRelay(String ip) {
		return allowRelays.contains(ip);
	}

	/**
	 * Gets the inbound <code>EmailLimit</code> given its unique business name or <code>null</code> if unlimited.
	 */
	public EmailLimit getEmailInLimit(String accounting) {
		return emailInLimits.get(accounting);
	}

	/**
	 * Gets the outbound <code>EmailLimit</code> given its unique business name or <code>null</code> if unlimited.
	 */
	public EmailLimit getEmailOutLimit(String accounting) {
		return emailOutLimits.get(accounting);
	}

	/**
	 * Gets the relay <code>EmailLimit</code> given its unique business name or <code>null</code> if unlimited.
	 */
	public EmailLimit getEmailRelayLimit(String accounting) {
		return emailRelayLimits.get(accounting);
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == null) {
			log.trace("equals(Object obj): obj == null, returning false");
			return false;
		}

		if(!(obj instanceof JilterConfiguration)) {
			log.trace("equals(Object obj): !(obj instanceof JilterConfiguration), returning false");
			return false;
		}

		return equals((JilterConfiguration)obj);
	}

	public boolean equals(JilterConfiguration other) {
		if(!version.equals(other.version)) {
			log.trace("equals(JilterConfiguration other): version != other.version, returning false");
			return false;
		}

		if(!listenIP.equals(other.listenIP)) {
			log.trace("equals(JilterConfiguration other): listenIP != other.listenIP, returning false");
			return false;
		}

		if(listenPort != other.listenPort) {
			log.trace("equals(JilterConfiguration other): listenPort != other.listenPort, returning false");
			return false;
		}

		if(restrict_outbound_email!=other.restrict_outbound_email) {
			log.trace("equals(JilterConfiguration other): restrict_outbound_email != other.restrict_outbound_email, returning false");
			return false;
		}

		if(!Objects.equals(smtpServer, other.smtpServer)) {
			log.trace("equals(JilterConfiguration other): smtpServer != other.smtpServer, returning false");
			return false;
		}

		if(!Objects.equals(emailSummaryFrom, other.emailSummaryFrom)) {
			log.trace("equals(JilterConfiguration other): emailSummaryFrom != other.emailSummaryFrom, returning false");
			return false;
		}

		if(!Objects.equals(emailSummaryTo, other.emailSummaryTo)) {
			log.trace("equals(JilterConfiguration other): emailSummaryTo != other.emailSummaryTo, returning false");
			return false;
		}

		if(!Objects.equals(emailFullFrom, other.emailFullFrom)) {
			log.trace("equals(JilterConfiguration other): emailFullFrom != other.emailFullFrom, returning false");
			return false;
		}

		if(!Objects.equals(emailFullTo, other.emailFullTo)) {
			log.trace("equals(JilterConfiguration other): emailFullTo != other.emailFullTo, returning false");
			return false;
		}

		if(!equalsMap(domainBusinesses, other.domainBusinesses)) {
			log.trace("equals(JilterConfiguration other): domainBusinesses != other.domainBusinesses, returning false");
			return false;
		}

		if(!equalsMapSetString(domainAddresses, other.domainAddresses)) {
			log.trace("equals(JilterConfiguration other): domainAddresses != other.domainAddresses, returning false");
			return false;
		}

		if(!equals(ips, other.ips)) {
			log.trace("equals(JilterConfiguration other): ips != other.ips, returning false");
			return false;
		}

		if(!equals(denies, other.denies)) {
			log.trace("equals(JilterConfiguration other): denies != other.denies, returning false");
			return false;
		}

		if(!equals(denySpams, other.denySpams)) {
			log.trace("equals(JilterConfiguration other): denySpams != other.denySpams, returning false");
			return false;
		}

		if(!equals(allowRelays, other.allowRelays)) {
			log.trace("equals(JilterConfiguration other): allowRelays != other.allowRelays, returning false");
			return false;
		}

		if(!equalsMap(emailInLimits, other.emailInLimits)) {
			log.trace("equals(JilterConfiguration other): emailInLimits != other.emailInLimits, returning false");
			return false;
		}

		if(!equalsMap(emailOutLimits, other.emailOutLimits)) {
			log.trace("equals(JilterConfiguration other): emailOutLimits != other.emailOutLimits, returning false");
			return false;
		}

		if(!equalsMap(emailRelayLimits, other.emailRelayLimits)) {
			log.trace("equals(JilterConfiguration other): emailRelayLimits != other.emailRelayLimits, returning false");
			return false;
		}

		log.trace("equals(JilterConfiguration other): returning true");
		return true;
	}

	/**
	 * Compares two sets and makes sure they exactly match.
	 */
	public static boolean equals(Set<?> set1, Set<?> set2) {
		return
			set1.size()==set2.size()
			&& set1.containsAll(set2)
		;
	}

	/**
	 * Compares two maps and makes sure they exactly match, including all their mapped sets.
	 */
	public static boolean equalsMapSetString(Map<?, ? extends Set<?>> map1, Map<?, ? extends Set<?>> map2) {
		// Make sure the key sets match
		Set<?> keySet1 = map1.keySet();
		Set<?> keySet2 = map2.keySet();
		if(!equals(keySet1, keySet2)) {
			if(log.isTraceEnabled()) log.trace("equals(Map<?, ? extends Set<?>> map1, Map<?, ? extends Set<?>> map2): keySet1 != keySet2");
			return false;
		}

		// Now make sure each of the mapped sets match
		for(Object key : keySet1) {
			if(!equals(map1.get(key), map2.get(key))) {
				if(log.isTraceEnabled()) log.trace("equals(Map<?, ? extends Set<?>> map1, Map<?, ? extends Set<?>> map2): key=\""+key+"\": map1.get(key) != map2.get(key)");
				return false;
			}
		}

		return true;
	}

	/**
	 * Compares two maps and makes sure they exactly match, including all their values.
	 */
	public static boolean equalsMap(Map<?, ?> map1, Map<?, ?> map2) {
		// Make sure the key sets match
		Set<?> keySet1 = map1.keySet();
		Set<?> keySet2 = map2.keySet();
		if(!equals(keySet1, keySet2)) {
			if(log.isTraceEnabled()) log.trace("equals(Map<?, ?> map1, Map<?, ?> map2): keySet1 != keySet2");
			return false;
		}

		// Now make sure each of the mapped email limits match
		for(Object key : keySet1) {
			if(!map1.get(key).equals(map2.get(key))) {
				if(log.isTraceEnabled()) log.trace("equals(Map<?, ?> map1, Map<?, ?> map2): key=\""+key+"\": map1.get(key) != map2.get(key)");
				return false;
			}
		}

		return true;
	}

	/**
	 * Compares two objects with <code>null</code> being equal to <code>null</code>.
	 *
	 * @deprecated  Please use {@link Objects#equals(java.lang.Object, java.lang.Object)} instead.
	 */
	@Deprecated
	public static boolean equals(Object o1, Object o2) {
		return Objects.equals(o1, o2);
	}
}
