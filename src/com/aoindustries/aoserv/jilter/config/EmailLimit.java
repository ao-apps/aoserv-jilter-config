package com.aoindustries.aoserv.jilter.config;

/*
 * Copyright 2007-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */

/**
 * Stores the values that control email rate limiting.
 *
 * @author  AO Industries, Inc.
 */
public class EmailLimit {

    final private int burst;
    final private float rate;
    
    public EmailLimit(int burst, float rate) {
        if(burst<=0) throw new IllegalArgumentException("Invalid burst, must be > 0: "+burst);
        if(Float.isNaN(rate)) throw new IllegalArgumentException("rate may not be NaN: "+rate);
        if(rate<=0) throw new IllegalArgumentException("rate must be > 0: "+rate);
        this.burst = burst;
        this.rate = rate;
    }
    
    public int hashCode() {
        return burst ^ Float.floatToRawIntBits(rate);
    }
    
    public boolean equals(Object O) {
        if(O==null) return false;
        if(!(O instanceof EmailLimit)) return false;
        EmailLimit other = (EmailLimit)O;
        return
            burst == other.burst
            && rate == other.rate
        ;
    }
    
    public int getBurst() {
        return burst;
    }
    
    public float getRate() {
        return rate;
    }
}
