package com.aoindustries.aoserv.jilter.config;

/*
 * Copyright 2007-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * In order to avoid a real-time dependency between the Jilter code and the full
 * AOServ system, the configuration values for the jilter is placed into a
 * properties file that is stored in
 * <code>/etc/opt/aoserv-jilter/aoserv-jilter.properties</code>.  The
 * <code>aoserv-daemon</code> will rebuild this file when any of its related
 * tables have been updated, and at daemon start-up.  The result is that jilter
 * can work off the last config file even when AOServ is down for maintenance of
 * not working properly.
 *
 * @author  AO Industries, Inc.
 */
public class JilterConfiguration {

    public static final int MILTER_PORT = 12000;

    /**
     * This version number should be updated whenever the on-disk format of the configuration has been changed in an
     * incompatible way.  Newer versions of the code may choose to support older formats, but this should
     * not be necessary because the aoserv-daemon will overwrite the config file within a minute of start-up.
     */
    private static final String VERSION="2007-05-13";

    private static final String PROPS_FILE = "/etc/opt/aoserv-jilter/aoserv-jilter.properties";
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
    public synchronized static JilterConfiguration getJilterConfiguration() throws IOException {
        long modifyTime = propsUF.lastModified();
        if(jilterConfiguration==null || modifyTime!=lastModifiedTime) {
            jilterConfiguration = new JilterConfiguration();
            lastModifiedTime = modifyTime;
        }
        return jilterConfiguration;
    }

    final private String version;
    final private String primaryIP;
    final private boolean restrict_outbound_email;
    final private String smtpServer;
    final private String emailSummaryFrom;
    final private String emailSummaryTo;
    final private String emailFullFrom;
    final private String emailFullTo;
    final private Map<String,String> domainPackages;
    final private Map<String,Set<String>> domainAddresses;
    final private Set<String> ips;
    final private Set<String> denies;
    final private Set<String> denySpams;
    final private Set<String> allowRelays;
    final private Map<String,EmailLimit> emailInLimits;
    final private Map<String,EmailLimit> emailOutLimits;
    final private Map<String,EmailLimit> emailRelayLimits;

    public JilterConfiguration(
        String primaryIP,
        boolean restrict_outbound_email,
        String smtpServer,
        String emailSummaryFrom,
        String emailSummaryTo,
        String emailFullFrom,
        String emailFullTo,
        Map<String,String> domainPackages,
        Map<String,Set<String>> domainAddresses,
        Set<String> ips,
        Set<String> denies,
        Set<String> denySpams,
        Set<String> allowRelays,
        Map<String,EmailLimit> emailInLimits,
        Map<String,EmailLimit> emailOutLimits,
        Map<String,EmailLimit> emailRelayLimits
    ) {
        this.version = VERSION;
        this.primaryIP = primaryIP;
        this.restrict_outbound_email = restrict_outbound_email;
        this.smtpServer = smtpServer;
        this.emailSummaryFrom = emailSummaryFrom;
        this.emailSummaryTo = emailSummaryTo;
        this.emailFullFrom = emailFullFrom;
        this.emailFullTo = emailFullTo;
        this.domainPackages = domainPackages;
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
        Properties props = new Properties();
        FileInputStream in = new FileInputStream(PROPS_FILE);
        try {
            props.load(in);
        } finally {
            in.close();
        }
        
        // Make sure the version matches, throw IOException if doesn't
        version = props.getProperty("version");
        if(!VERSION.equals(version)) throw new IOException("Incorrect version for properties file \""+PROPS_FILE+"\".  Must be version \""+VERSION+"\".  version=\""+version+'"');

        // primaryIP
        primaryIP = props.getProperty("primaryIP");
        
        // restrict_outbound_email
        restrict_outbound_email = "true".equals(props.getProperty("restrict_outbound_email"));

        // Email settings
        smtpServer = props.getProperty("smtp.server");
        emailSummaryFrom = props.getProperty("email.summary.from");
        emailSummaryTo = props.getProperty("email.summary.to");
        emailFullFrom = props.getProperty("email.full.from");
        emailFullTo = props.getProperty("email.full.to");

        domainPackages = new HashMap<String,String>();
        domainAddresses = new HashMap<String,Set<String>>();
        ips = new HashSet<String>();
        denies = new HashSet<String>();
        denySpams = new HashSet<String>();
        allowRelays = new HashSet<String>();
        emailInLimits = new HashMap<String,EmailLimit>();
        emailOutLimits = new HashMap<String,EmailLimit>();
        emailRelayLimits = new HashMap<String,EmailLimit>();
        Enumeration E = props.propertyNames();
        while(E.hasMoreElements()) {
            String key = (String)E.nextElement();
            String value = props.getProperty(key);
            if(key.startsWith("domainPackages.")) {
                // domainPackages
                int pos = value.indexOf('|');
                if(pos==-1) throw new IOException("Unable to parse domainPackages: "+value);
                String packageName = value.substring(0, pos);
                String domain = value.substring(pos+1);
                domainPackages.put(domain, packageName);
                // Add to domainAddresses just in case the domain has no addresses
                if(!domainAddresses.containsKey(domain)) domainAddresses.put(domain, new HashSet<String>());
            } else if(key.startsWith("addresses.")) {
                // domainAddresses
                int pos = value.indexOf('@');
                if(pos==-1) throw new IOException("Unable to find @ in address: "+value);
                String address = value.substring(0, pos);
                String domain = value.substring(pos+1);

                Set<String> addresses = domainAddresses.get(domain);
                if(addresses==null) domainAddresses.put(domain, addresses = new HashSet<String>());
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
                    IOException ioErr = new IOException("Unable to parse emailInLimits: "+value);
                    ioErr.initCause(err);
                    throw ioErr;
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
                    IOException ioErr = new IOException("Unable to parse emailOutLimits: "+value);
                    ioErr.initCause(err);
                    throw ioErr;
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
                    IOException ioErr = new IOException("Unable to parse emailRelayLimits: "+value);
                    ioErr.initCause(err);
                    throw ioErr;
                }
            }
        }
        // Make all domainAddresses lists unmodifiable
        for(Map.Entry<String,Set<String>> entry : domainAddresses.entrySet()) {
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
                Properties props = new Properties();

                // VERSION
                props.setProperty("version", VERSION);

                // primaryIP
                props.setProperty("primaryIP", primaryIP);

                // restrict_outbound_email
                props.setProperty("restrict_outbound_email", restrict_outbound_email ? "true" : "false");

                // Email settings
                if(smtpServer!=null) props.setProperty("smtp.server", smtpServer);
                if(emailSummaryFrom!=null) props.setProperty("email.summary.from", emailSummaryFrom);
                if(emailSummaryTo!=null) props.setProperty("email.summary.to", emailSummaryTo);
                if(emailFullFrom!=null) props.setProperty("email.full.from", emailFullFrom);
                if(emailFullTo!=null) props.setProperty("email.full.to", emailFullTo);

                // domainPackages
                int domainCounter = 1;
                for(String domain : domainPackages.keySet()) {
                    props.setProperty("domainPackages."+(domainCounter++), domainPackages.get(domain)+"|"+domain);
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
            if(!newPropsUF.renameTo(propsUF)) throw new IOException("Unable to rename "+newPropsUF.getPath()+" to "+propsUF.getPath());
        }
    }

    /**
     * Gets the version of this file this was loaded from.
     */
    public String getVersion() {
        return getVersion();
    }

    /**
     * @see  AOServer#getPrimaryIPAddress()
     */
    public String getPrimaryIP() {
        return primaryIP;
    }

    /**
     * @see  AOServer#getRestrictOutboundEmail()
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
     * Gets the unique package name for a domain or <code>null</code> if domain doesn't exist.
     */
    public String getPackageName(String domain) {
        return domainPackages.get(domain.toLowerCase());
    }

    /**
     * Gets the set of addresses for a specific domain (without the @domain part of the address).  If the domain exists and has no addresses, returns an empty set.  If the
     * domain doesn't exist at all then returns <code>null</code>.  Domain is case-insensitive by conversion to lower-case.
     */
    public Set<String> getAddresses(String domain) {
        return domainAddresses.get(domain.toLowerCase());
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
     * Gets the inbound <code>EmailLimit</code> given its unique package name or <code>null</code> if unlimited.
     */
    public EmailLimit getEmailInLimit(String packageName) {
        return emailInLimits.get(packageName);
    }

    /**
     * Gets the outbound <code>EmailLimit</code> given its unique package name or <code>null</code> if unlimited.
     */
    public EmailLimit getEmailOutLimit(String packageName) {
        return emailOutLimits.get(packageName);
    }

    /**
     * Gets the relay <code>EmailLimit</code> given its unique package name or <code>null</code> if unlimited.
     */
    public EmailLimit getEmailRelayLimit(String packageName) {
        return emailRelayLimits.get(packageName);
    }

    public boolean equals(Object O) {
        if(O==null) {
            log.trace("equals(Object O): O == null, returning false");
            return false;
        }
        
        if(!(O instanceof JilterConfiguration)) {
            log.trace("equals(Object O): !(O instanceof JilterConfiguration), returning false");
            return false;
        }
        
        return equals((JilterConfiguration)O);
    }

    public boolean equals(JilterConfiguration other) {
        if(!version.equals(other.version)) {
            log.trace("equals(JilterConfiguration other): version != other.version, returning false");
            return false;
        }

        if(!primaryIP.equals(other.primaryIP)) {
            log.trace("equals(JilterConfiguration other): primaryIP != other.primaryIP, returning false");
            return false;
        }
        
        if(restrict_outbound_email!=other.restrict_outbound_email) {
            log.trace("equals(JilterConfiguration other): restrict_outbound_email != other.restrict_outbound_email, returning false");
            return false;
        }
        
        if(!equals(smtpServer, other.smtpServer)) {
            log.trace("equals(JilterConfiguration other): smtpServer != other.smtpServer, returning false");
            return false;
        }

        if(!equals(emailSummaryFrom, other.emailSummaryFrom)) {
            log.trace("equals(JilterConfiguration other): emailSummaryFrom != other.emailSummaryFrom, returning false");
            return false;
        }

        if(!equals(emailSummaryTo, other.emailSummaryTo)) {
            log.trace("equals(JilterConfiguration other): emailSummaryTo != other.emailSummaryTo, returning false");
            return false;
        }

        if(!equals(emailFullFrom, other.emailFullFrom)) {
            log.trace("equals(JilterConfiguration other): emailFullFrom != other.emailFullFrom, returning false");
            return false;
        }

        if(!equals(emailFullTo, other.emailFullTo)) {
            log.trace("equals(JilterConfiguration other): emailFullTo != other.emailFullTo, returning false");
            return false;
        }

        if(!equalsMap(domainPackages, other.domainPackages)) {
            log.trace("equals(JilterConfiguration other): domainPackages != other.domainPackages, returning false");
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
     *
     * Note: This should be able to accept a Set<?> instead of Set<String>, but won't compile in Java 1.5.0_07.
     */
    public static boolean equalsMapSetString(Map<?,Set<String>> map1, Map<?,Set<String>> map2) {
        // Make sure the key sets match
        Set<?> keySet1 = map1.keySet();
        Set<?> keySet2 = map2.keySet();
        if(!equals(keySet1, keySet2)) {
            if(log.isTraceEnabled()) log.trace("equals(Map<?,Set<String>> map1, Map<?,Set<String>> map2): keySet1 != keySet2");
            return false;
        }

        // Now make sure each of the mapped sets match
        for(Object key : keySet1) {
            if(!equals(map1.get(key), map2.get(key))) {
                if(log.isTraceEnabled()) log.trace("equals(Map<?,Set<String>> map1, Map<?,Set<String>> map2): key=\""+key+"\": map1.get(key) != map2.get(key)");
                return false;
            }
        }
        
        return true;
    }

    /**
     * Compares two maps and makes sure they exactly match, including all their values.
     */
    public static boolean equalsMap(Map<?,?> map1, Map<?,?> map2) {
        // Make sure the key sets match
        Set<?> keySet1 = map1.keySet();
        Set<?> keySet2 = map2.keySet();
        if(!equals(keySet1, keySet2)) {
            if(log.isTraceEnabled()) log.trace("equals(Map<?,?> map1, Map<?,?> map2): keySet1 != keySet2");
            return false;
        }

        // Now make sure each of the mapped email limits match
        for(Object key : keySet1) {
            if(!map1.get(key).equals(map2.get(key))) {
                if(log.isTraceEnabled()) log.trace("equals(Map<?,?> map1, Map<?,?> map2): key=\""+key+"\": map1.get(key) != map2.get(key)");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Compares two objects with <code>null</code> being equal to <code>null</code>.
     */
    public static boolean equals(Object O1, Object O2) {
        return O1==null ? O2==null : O1.equals(O2);
    }
}
