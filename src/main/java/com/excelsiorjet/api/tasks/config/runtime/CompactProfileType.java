/*
 * Copyright (c) 2016-2017, Excelsior LLC.
 *
 *  This file is part of Excelsior JET API.
 *
 *  Excelsior JET API is free software:
 *  you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Excelsior JET API is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Excelsior JET API.
 *  If not, see <http://www.gnu.org/licenses/>.
 *
*/
package com.excelsiorjet.api.tasks.config.runtime;

import com.excelsiorjet.api.tasks.JetTaskFailureException;
import com.excelsiorjet.api.util.Utils;

import static com.excelsiorjet.api.util.Txt.s;

/**
 * Java SE 8 compact profiles enumeration.
 */
public enum CompactProfileType {
    AUTO, // special value meaning that Excelsior JET should determine the smallest possible profile itself.
    COMPACT1,
    COMPACT2,
    COMPACT3,
    FULL;

    public String toString() {
        return Utils.enumConstantNameToParameter(name());
    }

    public static CompactProfileType validate(String profile) throws JetTaskFailureException {
        try {
            return CompactProfileType.valueOf(Utils.parameterToEnumConstantName(profile));
        } catch (Exception e) {
            throw new JetTaskFailureException(s("JetApi.UnknownProfileType.Failure", profile));
        }
    }
}
