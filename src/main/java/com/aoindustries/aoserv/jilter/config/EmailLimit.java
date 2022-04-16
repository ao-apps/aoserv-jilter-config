/*
 * aoserv-jilter-config - Configuration API for AOServ Jilter.
 * Copyright (C) 2007-2011, 2020, 2021, 2022  AO Industries, Inc.
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
 * along with aoserv-jilter-config.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.aoserv.jilter.config;

/**
 * Stores the values that control email rate limiting.
 *
 * @author  AO Industries, Inc.
 */
public class EmailLimit {

	private final int burst;
	private final float rate;

	public EmailLimit(int burst, float rate) {
		if(burst<=0) throw new IllegalArgumentException("Invalid burst, must be > 0: "+burst);
		if(Float.isNaN(rate)) throw new IllegalArgumentException("rate may not be NaN: "+rate);
		if(rate<=0) throw new IllegalArgumentException("rate must be > 0: "+rate);
		this.burst = burst;
		this.rate = rate;
	}

	@Override
	public int hashCode() {
		return burst ^ Float.floatToRawIntBits(rate);
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof EmailLimit)) return false;
		EmailLimit other = (EmailLimit)obj;
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
