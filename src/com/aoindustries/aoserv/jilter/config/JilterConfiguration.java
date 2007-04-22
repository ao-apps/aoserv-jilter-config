package com.aoindustries.aoserv.jilter.config;

/*
 * Copyright 2007 by AO Industries, Inc.,
 * 816 Azalea Rd, Mobile, Alabama, 36693, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.io.unix.Stat;
import com.aoindustries.io.unix.UnixFile;
import java.io.ByteArrayOutputStream;
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
    private static final String VERSION="2007-04-09";

    private static final String PROPS_FILE = "/etc/opt/aoserv-jilter/aoserv-jilter.properties";
    private static final String NEW_PROPS_FILE = "/etc/opt/aoserv-jilter/aoserv-jilter.properties.new";

    private static final UnixFile propsUF = new UnixFile(PROPS_FILE);
    private static final UnixFile newPropsUF = new UnixFile(NEW_PROPS_FILE);

    private static final Log log = LogFactory.getLog(JilterConfiguration.class);

    private static long lastModifiedTime;
    private static Stat tempStat;
    private static JilterConfiguration jilterConfiguration;
    
    /**
     * Gets the current configuration.  If the file has been updated, will reload the configuration
     * from disk.
     */
    public synchronized static JilterConfiguration getJilterConfiguration() throws IOException {
        long modifyTime = propsUF.getStat(tempStat).getModifyTime();
        if(jilterConfiguration==null || modifyTime!=lastModifiedTime) {
            jilterConfiguration = new JilterConfiguration();
            lastModifiedTime = modifyTime;
        }
        return jilterConfiguration;
    }

    final private String version;
    final private String primaryIP;
    final private boolean restrict_outbound_email;
    final private Map<String,Set<String>> domainsAndAddresses;
    final private Set<String> ips;
    final private Set<String> denies;
    final private Set<String> denySpams;
    final private Set<String> allowRelays;

    public JilterConfiguration(
        String primaryIP,
        boolean restrict_outbound_email,
        Map<String,Set<String>> domainsAndAddresses,
        Set<String> ips,
        Set<String> denies,
        Set<String> denySpams,
        Set<String> allowRelays
    ) {
        this.version = VERSION;
        this.primaryIP = primaryIP;
        this.restrict_outbound_email = restrict_outbound_email;
        this.domainsAndAddresses = domainsAndAddresses;
        this.ips = ips;
        this.denies = denies;
        this.denySpams = denySpams;
        this.allowRelays = allowRelays;
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
        {
            version = props.getProperty("version");
            if(!VERSION.equals(version)) throw new IOException("Incorrect version for properties file \""+PROPS_FILE+"\".  Must be version \""+VERSION+"\".  version=\""+version+'"');
        }

        // primaryIP
        {
            primaryIP = props.getProperty("primaryIP");
        }
        
        // restrict_outbound_email
        {
            restrict_outbound_email = "true".equals(props.getProperty("restrict_outbound_email"));
        }

        // domainsAndAddresses
        {
            domainsAndAddresses = new HashMap<String,Set<String>>();
            Enumeration E = props.propertyNames();
            while(E.hasMoreElements()) {
                String key = (String)E.nextElement();
                if(key.startsWith("domains.")) {
                    String domain = props.getProperty(key);
                    if(!domainsAndAddresses.containsKey(domain)) domainsAndAddresses.put(domain, new HashSet<String>());
                }
                if(key.startsWith("addresses.")) {
                    String fullAddress = props.getProperty(key);
                    int pos = fullAddress.indexOf('@');
                    if(pos==-1) throw new IOException("Unable to find @ in address: "+fullAddress);
                    String address = fullAddress.substring(0, pos);
                    String domain = fullAddress.substring(pos+1);

                    Set<String> addresses = domainsAndAddresses.get(domain);
                    if(addresses==null) domainsAndAddresses.put(domain, addresses = new HashSet<String>());
                    addresses.add(address);
                }
            }
            // Make all domainsAndAddresses lists unmodifiable
            for(Map.Entry<String,Set<String>> entry : domainsAndAddresses.entrySet()) {
                entry.setValue(Collections.unmodifiableSet(entry.getValue()));
            }
        }
        
        // ips
        {
            ips = new HashSet<String>();
            Enumeration E = props.propertyNames();
            while(E.hasMoreElements()) {
                String key = (String)E.nextElement();
                if(key.startsWith("ips.")) ips.add(props.getProperty(key));
            }
        }

        // denies
        {
            denies = new HashSet<String>();
            Enumeration E = props.propertyNames();
            while(E.hasMoreElements()) {
                String key = (String)E.nextElement();
                if(key.startsWith("denies.")) denies.add(props.getProperty(key));
            }
        }

        // denySpams
        {
            denySpams = new HashSet<String>();
            Enumeration E = props.propertyNames();
            while(E.hasMoreElements()) {
                String key = (String)E.nextElement();
                if(key.startsWith("denySpams.")) denySpams.add(props.getProperty(key));
            }
        }

        // allowRelays
        {
            allowRelays = new HashSet<String>();
            Enumeration E = props.propertyNames();
            while(E.hasMoreElements()) {
                String key = (String)E.nextElement();
                if(key.startsWith("allowRelays.")) allowRelays.add(props.getProperty(key));
            }
        }

    }

    /**
     * Saves the new configuration if either the config file doesn't exist or has changed in any way.
     */
    public void saveIfChanged(String comment) throws IOException {
        boolean matches;
        if(!propsUF.getStat().exists()) {
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

                // domainsAndAddresses
                int domainCounter = 1;
                int addressCounter = 1;
                for(String domain : domainsAndAddresses.keySet()) {
                    props.setProperty("domains."+(domainCounter++), domain);
                    for(String address : domainsAndAddresses.get(domain)) {
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
            newPropsUF.renameTo(propsUF);
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
     * Gets the set of addresses for a specific domain (without the @domain part of the address).  If the domain exists and has no addresses, returns an empty set.  If the
     * domain doesn't exist at all then returns <code>null</code>.  Domain is case-insensitive by conversion to lower-case.
     */
    public Set<String> getAddresses(String domain) {
        return domainsAndAddresses.get(domain.toLowerCase());
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

        if(!equals(domainsAndAddresses, other.domainsAndAddresses)) {
            log.trace("equals(JilterConfiguration other): domainsAndAddresses != other.domainsAndAddresses, returning false");
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

        log.trace("equals(JilterConfiguration other): returning true");
        return true;
    }

    /**
     * Compares two sets and makes sure they exactly match.
     */
    public static boolean equals(Set<String> set1, Set<String> set2) {
        return
            set1.size()==set2.size()
            && set1.containsAll(set2)
        ;
    }

    /**
     * Compares two maps and makes sure they exactly match, including all their mapped sets.
     */
    public static boolean equals(Map<String,Set<String>> map1, Map<String,Set<String>> map2) {
        // Make sure the key sets match
        Set<String> keySet1 = map1.keySet();
        Set<String> keySet2 = map2.keySet();
        if(!equals(keySet1, keySet2)) {
            if(log.isTraceEnabled()) log.trace("equals(Map<String,Set<String>> map1, Map<String,Set<String>> map2): keySet1 != keySet2");
            return false;
        }

        // Now make sure each of the mapped sets match
        for(String key : keySet1) {
            if(!equals(map1.get(key), map2.get(key))) {
                if(log.isTraceEnabled()) log.trace("equals(Map<String,Set<String>> map1, Map<String,Set<String>> map2): key=\""+key+"\": map1.get(key) != map2.get(key)");
                return false;
            }
        }
        
        return true;
    }
}
